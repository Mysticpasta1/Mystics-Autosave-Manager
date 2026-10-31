package com.mystic.mam;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Last-chance writes for paths where the normal shutdown save may never run: a crash, a detected
 * server hang, or a JVM shutdown while the server is still running.
 *
 * <p>These saves are best effort. A healthy server thread is always asked to do the write first.
 * If that thread is the one that is stuck, the calling thread writes directly instead.
 */
public final class EmergencySave {
    private static final long SAVE_TASK_TIMEOUT_MILLIS = 3000L;
    private static final long SAVE_IN_PROGRESS_TIMEOUT_MILLIS = 30_000L;
    private static final long DUPLICATE_WINDOW_MILLIS = 10_000L;
    private static final long HANG_CHECK_INTERVAL_MILLIS = 1000L;
    private static final long HANG_STALL_NANOS = TimeUnit.SECONDS.toNanos(60L);
    private static final long HANG_CONFIRM_NANOS = TimeUnit.SECONDS.toNanos(5L);

    private static final Object SAVE_LOCK = new Object();
    private static volatile MinecraftServer active;
    private static boolean hooked;
    private static boolean saveInProgress;
    private static long lastSaveMillis;
    private static volatile Thread hangWatcher;

    private EmergencySave() {
    }

    public static synchronized void watch(MinecraftServer server) {
        active = server;
        if (!hooked) {
            hooked = true;
            Runtime.getRuntime().addShutdownHook(new Thread(EmergencySave::saveIfStillRunning, "mam-emergency-save"));
        }
        startHangWatcher(server);
    }

    public static synchronized void unwatch(MinecraftServer server) {
        if (active == server) {
            active = null;
            stopHangWatcher();
        }
    }

    /**
     * Stops the integrated-server hang watcher. Called when the server is already on a crash path
     * so the watcher cannot fire a second save while the crash shutdown is running.
     */
    public static synchronized void stopHangWatcher() {
        hangWatcher = null;
    }

    /**
     * Saves if the server is still open. Safe to call from the server thread, the client thread,
     * the watchdog thread, or a shutdown hook.
     */
    public static void attemptSave(MinecraftServer server, AutosaveManager.Reason reason) {
        if (server == null || server.isStopped() || !server.isRunning() || server.getPlayerList() == null) {
            return;
        }

        synchronized (SAVE_LOCK) {
            if (saveInProgress) {
                Mam.LOGGER.warn("An emergency save is already running; skipping the {} save.", reason);
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastSaveMillis < DUPLICATE_WINDOW_MILLIS) {
                Mam.LOGGER.warn("An emergency save just finished; skipping the {} save.", reason);
                return;
            }

            saveInProgress = true;
        }

        try {
            if (server.isSameThread()) {
                saveDirect(server, reason);
                return;
            }

            if (!saveOnServerThread(server, reason)) {
                saveFallback(server, reason);
            }
        } finally {
            synchronized (SAVE_LOCK) {
                saveInProgress = false;
                lastSaveMillis = System.currentTimeMillis();
            }
        }
    }

    private static boolean saveOnServerThread(MinecraftServer server, AutosaveManager.Reason reason) {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean claimed = new AtomicBoolean();

        try {
            server.execute(() -> {
                if (claimed.compareAndSet(false, true)) {
                    try {
                        saveDirect(server, reason);
                    } catch (Throwable throwable) {
                        Mam.LOGGER.error("Emergency save failed on the server thread ({}).", reason, throwable);
                    } finally {
                        finished.countDown();
                    }
                } else {
                    finished.countDown();
                }
            });
        } catch (Throwable throwable) {
            Mam.LOGGER.warn("Could not queue the emergency save ({}): {}", reason, throwable.toString());
            return false;
        }

        try {
            if (finished.await(SAVE_TASK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                return true;
            }

            // A normal save can take longer than the task timeout. Let that save finish and the
            // queued task run instead of starting a second write at the same time.
            if (server.isCurrentlySaving()) {
                Mam.LOGGER.warn("A world save is already in progress; waiting up to {} ms for it to finish.", SAVE_IN_PROGRESS_TIMEOUT_MILLIS);
                if (finished.await(SAVE_IN_PROGRESS_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }

        if (claimed.compareAndSet(false, true)) {
            Mam.LOGGER.warn(
                    "The server thread did not start the emergency save within {} ms; saving from the {} thread instead.",
                    SAVE_TASK_TIMEOUT_MILLIS,
                    Thread.currentThread().getName()
            );
            return false;
        }

        Mam.LOGGER.warn("The emergency save is still running on the server thread.");
        return true;
    }

    private static void saveFallback(MinecraftServer server, AutosaveManager.Reason reason) {
        try {
            saveDirect(server, reason);
        } catch (Throwable throwable) {
            Mam.LOGGER.error("Emergency save failed on the {} thread ({}).", Thread.currentThread().getName(), reason, throwable);
        }
    }

    private static boolean saveDirect(MinecraftServer server, AutosaveManager.Reason reason) {
        try {
            return AutosaveManager.save(server, true, reason);
        } catch (Throwable throwable) {
            Mam.LOGGER.error("Emergency save failed ({}).", reason, throwable);
            return false;
        }
    }

    private static void startHangWatcher(MinecraftServer server) {
        stopHangWatcher();

        // A dedicated server already watches itself through ServerWatchdog. That watchdog calls
        // back into this class before it exits, so a second watcher would only race with it.
        if (server.isDedicatedServer()) {
            return;
        }

        Thread watcher = new Thread(() -> runHangWatcher(server), "mam-hang-watcher");
        watcher.setDaemon(true);
        hangWatcher = watcher;
        watcher.start();
    }

    private static void runHangWatcher(MinecraftServer server) {
        long lastNextTick = server.getNextTickTime();
        long lastProgress = System.nanoTime();
        long stalledSince = 0L;

        while (isCurrentWatcher(server)) {
            try {
                Thread.sleep(HANG_CHECK_INTERVAL_MILLIS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }

            if (!isCurrentWatcher(server)) {
                return;
            }

            long now = System.nanoTime();
            long nextTick = server.getNextTickTime();
            if (nextTick != lastNextTick || server.isCurrentlySaving()) {
                lastNextTick = nextTick;
                lastProgress = now;
                stalledSince = 0L;
                continue;
            }

            if (stalledSince == 0L) {
                if (now - lastProgress >= HANG_STALL_NANOS) {
                    stalledSince = now;
                    Mam.LOGGER.warn(
                            "The server tick has not advanced for {} seconds. Waiting {} more seconds before attempting a hang save.",
                            TimeUnit.NANOSECONDS.toSeconds(now - lastProgress),
                            TimeUnit.NANOSECONDS.toSeconds(HANG_CONFIRM_NANOS)
                    );
                }
            } else if (now - stalledSince >= HANG_CONFIRM_NANOS) {
                Mam.LOGGER.error("The server tick is still stuck. Attempting a hang save.");
                AutosaveManager.onHangDetected(server);
                return;
            }
        }
    }

    private static boolean isCurrentWatcher(MinecraftServer server) {
        return Thread.currentThread() == hangWatcher
                && active == server
                && server.isRunning()
                && !server.isStopped();
    }

    private static void saveIfStillRunning() {
        MinecraftServer server = active;
        if (server == null || server.isStopped() || !server.isRunning()) {
            return;
        }
        attemptSave(server, AutosaveManager.Reason.EMERGENCY);
    }
}
