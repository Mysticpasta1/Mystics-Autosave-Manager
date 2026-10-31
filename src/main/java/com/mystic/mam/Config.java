package com.mystic.mam;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue TIMER_ENABLED = BUILDER
            .comment("Save on a timer while the world is open. Manual saves, shutdown, reload, dimension changes, crashes, and hangs are separate.")
            .define("timerEnabled", true);

    public static final ModConfigSpec.IntValue INTERVAL_MINUTES = BUILDER
            .comment("Minutes between timed saves while timerEnabled is true. Vanilla saves about every 5 minutes; a longer interval writes to disk less often.")
            .defineInRange("intervalMinutes", 30, 1, 1440);

    public static final ModConfigSpec.BooleanValue SAVE_ON_RELOAD = BUILDER
            .comment("Save after a successful /reload, once datapacks and structure templates have finished reloading.")
            .define("saveOnReload", true);

    public static final ModConfigSpec.BooleanValue SAVE_ON_DIMENSION_CHANGE = BUILDER
            .comment("Save when a player changes dimension, including returning from the End. Several changes in the same tick share one save.")
            .define("saveOnDimensionChange", true);

    public static final ModConfigSpec.BooleanValue SAVE_ON_CRASH = BUILDER
            .comment("Make a last-chance save when the server or client detects a crash. This runs before the normal crash shutdown.")
            .define("saveOnCrash", true);

    public static final ModConfigSpec.BooleanValue SAVE_ON_HANG = BUILDER
            .comment("Make a last-chance save when a server hang is detected. On a dedicated server this uses the vanilla watchdog; integrated servers use a built-in watcher.")
            .define("saveOnHang", true);

    public static final ModConfigSpec.BooleanValue ANNOUNCE_SAVES = BUILDER
            .comment("Tell everyone in chat when a timed, reload, or dimension save finishes. Manual saves always reply to the player who asked.")
            .define("announceSaves", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static boolean timerEnabled() {
        return TIMER_ENABLED.get();
    }

    public static int intervalMinutes() {
        return INTERVAL_MINUTES.get();
    }

    public static int intervalTicks() {
        return intervalMinutes() * 60 * 20;
    }

    public static boolean saveOnReload() {
        return SAVE_ON_RELOAD.get();
    }

    public static boolean saveOnDimensionChange() {
        return SAVE_ON_DIMENSION_CHANGE.get();
    }

    public static boolean saveOnCrash() {
        return SAVE_ON_CRASH.get();
    }

    public static boolean saveOnHang() {
        return SAVE_ON_HANG.get();
    }

    public static boolean announceSaves() {
        return ANNOUNCE_SAVES.get();
    }
}
