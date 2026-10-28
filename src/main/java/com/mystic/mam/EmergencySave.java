package com.mystic.mam;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Last-chance write if the JVM exits while a world server is still running.
 * Clean shutdown already saves inside {@code MinecraftServer#stopServer}, and this hook skips that path.
 */
public final class EmergencySave {
    private static volatile MinecraftServer active;
    private static boolean hooked;

    private EmergencySave() {
    }

    public static void watch(MinecraftServer server) {
        active = server;
        if (hooked) {
            return;
        }
        hooked = true;
        Runtime.getRuntime().addShutdownHook(new Thread(EmergencySave::saveIfStillRunning, "mam-emergency-save"));
    }

    public static void unwatch(MinecraftServer server) {
        if (active == server) {
            active = null;
        }
    }

    private static void saveIfStillRunning() {
        MinecraftServer server = active;
        if (server == null || server.isStopped() || !server.isRunning()) {
            return;
        }

        try {
            if (server.isSameThread()) {
                AutosaveManager.save(server, true, AutosaveManager.Reason.EMERGENCY);
                return;
            }

            CountDownLatch finished = new CountDownLatch(1);
            server.execute(() -> {
                try {
                    AutosaveManager.save(server, true, AutosaveManager.Reason.EMERGENCY);
                } finally {
                    finished.countDown();
                }
            });
            if (!finished.await(3, TimeUnit.SECONDS)) {
                Mam.LOGGER.warn("Emergency save timed out after 3 seconds.");
            }
        } catch (Throwable throwable) {
            Mam.LOGGER.warn("Emergency save did not finish: {}", throwable.toString());
        }
    }
}
