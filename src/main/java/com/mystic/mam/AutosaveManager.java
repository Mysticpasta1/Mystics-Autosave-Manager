package com.mystic.mam;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla's repeating autosave is suppressed. A world write happens only for:
 * a manual request, the configured timer, a datapack reload, a dimension change, a detected crash
 * or hang, or server shutdown.
 */
@EventBusSubscriber(modid = Mam.MODID)
public final class AutosaveManager {
    private static boolean worldOpen;
    private static boolean dimensionSaveQueued;
    private static int ticksUntilSave;

    private AutosaveManager() {
    }

    public enum Reason {
        MANUAL,
        TIMER,
        RELOAD,
        DIMENSION,
        CRASH,
        HANG,
        EMERGENCY
    }

    public static void resetTimer() {
        ticksUntilSave = Config.intervalTicks();
    }

    /**
     * Operators may always save. Outside a dedicated server the world owner may too; on a dedicated
     * server the owner exemption does not apply, so saving stays with the operators.
     */
    public static boolean canSave(CommandSourceStack source) {
        return source.hasPermission(2) || isWorldOwner(source.getEntity());
    }

    public static boolean canSave(ServerPlayer player) {
        return player.hasPermissions(2) || isWorldOwner(player);
    }

    private static boolean isWorldOwner(@Nullable Entity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        MinecraftServer server = player.getServer();
        return server != null && !server.isDedicatedServer() && server.isSingleplayerOwner(player.getGameProfile());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        worldOpen = true;
        dimensionSaveQueued = false;
        resetTimer();
        EmergencySave.watch(event.getServer());
        Mam.LOGGER.info(
                "Autosave timer is {}. Interval is {} minutes. Reload saves: {}. Dimension saves: {}. Crash saves: {}. Hang saves: {}.",
                Config.timerEnabled() ? "on" : "off",
                Config.intervalMinutes(),
                Config.saveOnReload(),
                Config.saveOnDimensionChange(),
                Config.saveOnCrash(),
                Config.saveOnHang()
        );
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        worldOpen = false;
        dimensionSaveQueued = false;
        EmergencySave.stopHangWatcher();
        Mam.LOGGER.info("World server is closing. The shutdown save writes the world once.");
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EmergencySave.unwatch(event.getServer());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        queueDimensionSave();
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            queueDimensionSave();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!worldOpen) {
            return;
        }

        boolean timerDue = false;
        if (Config.timerEnabled()) {
            int interval = Config.intervalTicks();
            if (ticksUntilSave > interval) {
                ticksUntilSave = interval;
            }
            if (--ticksUntilSave <= 0) {
                ticksUntilSave = interval;
                timerDue = true;
            }
        }

        boolean dimensionDue = dimensionSaveQueued;
        dimensionSaveQueued = false;
        if (!dimensionDue && !timerDue) {
            return;
        }

        Reason reason = dimensionDue ? Reason.DIMENSION : Reason.TIMER;
        boolean saved = save(event.getServer(), true, reason);
        if (saved && Config.announceSaves()) {
            announce(event.getServer(), reason);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var save = Commands.literal("save").requires(AutosaveManager::canSave).executes(AutosaveManager::runSave);
        var timer = Commands.literal("timer")
                .then(Commands.literal("on").executes(context -> setTimer(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setTimer(context.getSource(), false)));
        var interval = Commands.literal("interval")
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                        .executes(context -> {
                            int minutes = IntegerArgumentType.getInteger(context, "minutes");
                            Config.INTERVAL_MINUTES.set(minutes);
                            Config.INTERVAL_MINUTES.save();
                            resetTimer();
                            context.getSource().sendSuccess(
                                    () -> Component.translatable("mam.message.interval", minutes),
                                    true
                            );
                            return minutes;
                        }));
        var reload = Commands.literal("reload")
                .then(Commands.literal("on").executes(context -> setReload(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setReload(context.getSource(), false)));
        var dimension = Commands.literal("dimension")
                .then(Commands.literal("on").executes(context -> setDimension(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setDimension(context.getSource(), false)));
        var crash = Commands.literal("crash")
                .then(Commands.literal("on").executes(context -> setCrash(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setCrash(context.getSource(), false)));
        var hang = Commands.literal("hang")
                .then(Commands.literal("on").executes(context -> setHang(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setHang(context.getSource(), false)));
        var mam = Commands.literal("mam")
                .requires(AutosaveManager::canSave)
                .then(save)
                .then(timer)
                .then(interval)
                .then(reload)
                .then(dimension)
                .then(crash)
                .then(hang)
                .then(Commands.literal("status").executes(context -> {
                    sendStatus(context.getSource());
                    return 1;
                }));

        event.getDispatcher().register(save);
        event.getDispatcher().register(mam);
    }

    public static void onResourcesReloaded(MinecraftServer server) {
        if (!worldOpen || !Config.saveOnReload()) {
            return;
        }
        boolean saved = save(server, true, Reason.RELOAD);
        if (saved && Config.announceSaves()) {
            announce(server, Reason.RELOAD);
        }
    }

    /** Called just before the server reports a crash and before its normal shutdown save. */
    public static void onCrashDetected(MinecraftServer server) {
        EmergencySave.stopHangWatcher();
        if (Config.saveOnCrash()) {
            EmergencySave.attemptSave(server, Reason.CRASH);
        }
    }

    /** Called by the dedicated server watchdog or the integrated-server hang watcher. */
    public static void onHangDetected(MinecraftServer server) {
        if (Config.saveOnHang()) {
            EmergencySave.attemptSave(server, Reason.HANG);
        }
    }

    public static boolean save(MinecraftServer server, boolean flush, Reason reason) {
        if (server.getPlayerList() == null) {
            return false;
        }

        try {
            boolean saved = server.saveEverything(true, flush, false);
            if (saved) {
                Mam.LOGGER.info("Saved the world ({})", reason);
            }
            return saved;
        } catch (RuntimeException exception) {
            Mam.LOGGER.error("Failed to save the world ({})", reason, exception);
            return false;
        }
    }

    private static void queueDimensionSave() {
        if (worldOpen && Config.saveOnDimensionChange()) {
            dimensionSaveQueued = true;
        }
    }

    private static int runSave(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (save(source.getServer(), true, Reason.MANUAL)) {
            source.sendSuccess(() -> Component.translatable("mam.message.saved"), true);
            return 1;
        }
        source.sendFailure(Component.translatable("mam.message.save_failed"));
        return 0;
    }

    private static int setTimer(CommandSourceStack source, boolean enabled) {
        Config.TIMER_ENABLED.set(enabled);
        Config.TIMER_ENABLED.save();
        resetTimer();
        source.sendSuccess(
                () -> Component.translatable(enabled ? "mam.message.timer_on" : "mam.message.timer_off", Config.intervalMinutes()),
                true
        );
        return 1;
    }

    private static int setReload(CommandSourceStack source, boolean enabled) {
        Config.SAVE_ON_RELOAD.set(enabled);
        Config.SAVE_ON_RELOAD.save();
        source.sendSuccess(
                () -> Component.translatable(enabled ? "mam.message.reload_on" : "mam.message.reload_off"),
                true
        );
        return 1;
    }

    private static int setDimension(CommandSourceStack source, boolean enabled) {
        Config.SAVE_ON_DIMENSION_CHANGE.set(enabled);
        Config.SAVE_ON_DIMENSION_CHANGE.save();
        source.sendSuccess(
                () -> Component.translatable(enabled ? "mam.message.dimension_on" : "mam.message.dimension_off"),
                true
        );
        return 1;
    }

    private static int setCrash(CommandSourceStack source, boolean enabled) {
        Config.SAVE_ON_CRASH.set(enabled);
        Config.SAVE_ON_CRASH.save();
        source.sendSuccess(
                () -> Component.translatable(enabled ? "mam.message.crash_on" : "mam.message.crash_off"),
                true
        );
        return 1;
    }

    private static int setHang(CommandSourceStack source, boolean enabled) {
        Config.SAVE_ON_HANG.set(enabled);
        Config.SAVE_ON_HANG.save();
        source.sendSuccess(
                () -> Component.translatable(enabled ? "mam.message.hang_on" : "mam.message.hang_off"),
                true
        );
        return 1;
    }

    private static void sendStatus(CommandSourceStack source) {
        int secondsLeft = Math.max(0, ticksUntilSave / 20);
        source.sendSuccess(
                () -> Component.translatable(
                        "mam.message.status",
                        Config.timerEnabled() ? Component.translatable("mam.message.on") : Component.translatable("mam.message.off"),
                        Config.intervalMinutes(),
                        secondsLeft / 60,
                        secondsLeft % 60,
                        Config.saveOnReload() ? Component.translatable("mam.message.on") : Component.translatable("mam.message.off"),
                        Config.saveOnDimensionChange() ? Component.translatable("mam.message.on") : Component.translatable("mam.message.off"),
                        Config.saveOnCrash() ? Component.translatable("mam.message.on") : Component.translatable("mam.message.off"),
                        Config.saveOnHang() ? Component.translatable("mam.message.on") : Component.translatable("mam.message.off")
                ),
                false
        );
    }

    private static void announce(MinecraftServer server, Reason reason) {
        if (server.getPlayerList() == null) {
            return;
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("mam.message.saved_reason", Component.translatable("mam.reason." + reason.name().toLowerCase())),
                false
        );
    }
}
