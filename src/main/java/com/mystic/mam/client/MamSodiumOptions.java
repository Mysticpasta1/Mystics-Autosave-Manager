package com.mystic.mam.client;

import com.google.common.collect.ImmutableList;
import com.mystic.mam.Config;
import net.caffeinemc.mods.sodium.client.gui.options.Option;
import net.caffeinemc.mods.sodium.client.gui.options.OptionGroup;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlValueFormatter;
import net.minecraft.network.chat.Component;
import toni.sodiumoptionsapi.api.ExtendedOptionGroup;
import toni.sodiumoptionsapi.api.OptionGUIConstruction;
import toni.sodiumoptionsapi.api.OptionIdentifier;

/**
 * Puts the MAM options into the Sodium options menu through Sodium Options API, so they are
 * reachable next to the graphics settings instead of only in the config file.
 *
 * <p>Only loaded when both Sodium and Sodium Options API are present.
 */
final class MamSodiumOptions {
    private MamSodiumOptions() {
    }

    static void register() {
        OptionGUIConstruction.EVENT.register(pages -> pages.add(buildPage()));
    }

    private static OptionPage buildPage() {
        Option<?> timer = toggle("timerEnabled", Config::timerEnabled, value -> {
            Config.TIMER_ENABLED.set(value);
            Config.TIMER_ENABLED.save();
        });

        Option<?> interval = new SliderConfigOption(
                Component.translatable("mam.configuration.intervalMinutes"),
                Component.translatable("mam.configuration.intervalMinutes.tooltip"),
                Config::intervalMinutes,
                value -> {
                    Config.INTERVAL_MINUTES.set(value);
                    Config.INTERVAL_MINUTES.save();
                },
                1,
                1440,
                1,
                ControlValueFormatter.translateVariable("mam.options.minutes"));

        Option<?> reload = toggle("saveOnReload", Config::saveOnReload, value -> {
            Config.SAVE_ON_RELOAD.set(value);
            Config.SAVE_ON_RELOAD.save();
        });

        Option<?> dimension = toggle("saveOnDimensionChange", Config::saveOnDimensionChange, value -> {
            Config.SAVE_ON_DIMENSION_CHANGE.set(value);
            Config.SAVE_ON_DIMENSION_CHANGE.save();
        });

        Option<?> announce = toggle("announceSaves", Config::announceSaves, value -> {
            Config.ANNOUNCE_SAVES.set(value);
            Config.ANNOUNCE_SAVES.save();
        });

        Option<?> crash = toggle("saveOnCrash", Config::saveOnCrash, value -> {
            Config.SAVE_ON_CRASH.set(value);
            Config.SAVE_ON_CRASH.save();
        });

        Option<?> hang = toggle("saveOnHang", Config::saveOnHang, value -> {
            Config.SAVE_ON_HANG.set(value);
            Config.SAVE_ON_HANG.save();
        });

        return new OptionPage(
                Component.translatable("key.categories.mam"),
                ImmutableList.of(
                        group("timer", timer, interval),
                        group("events", reload, dimension, announce),
                        group("safety", crash, hang)));
    }

    /** A boolean option labelled and described by the matching {@code mam.configuration.<key>} entries. */
    private static Option<Boolean> toggle(String key, java.util.function.Supplier<Boolean> reader,
                                          java.util.function.Consumer<Boolean> writer) {
        return new BooleanConfigOption(
                Component.translatable("mam.configuration." + key),
                Component.translatable("mam.configuration." + key + ".tooltip"),
                reader,
                writer);
    }

    private static OptionGroup group(String path, Option<?>... options) {
        OptionGroup.Builder builder = ExtendedOptionGroup.createBuilder(OptionIdentifier.create("mam", path));
        for (Option<?> option : options) {
            builder.add(option);
        }
        return builder.build();
    }
}
