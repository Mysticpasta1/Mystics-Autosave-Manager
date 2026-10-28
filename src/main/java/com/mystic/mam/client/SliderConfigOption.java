package com.mystic.mam.client;

import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlValueFormatter;
import net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class SliderConfigOption extends ConfigOption<Integer> {
    private final Consumer<Integer> writer;
    private final int min;
    private final int max;
    private final int step;
    private final ControlValueFormatter formatter;

    SliderConfigOption(Component name, Component tooltip, Supplier<Integer> reader, Consumer<Integer> writer,
                       int min, int max, int step, ControlValueFormatter formatter) {
        super(name, tooltip, reader);
        this.writer = writer;
        this.min = min;
        this.max = max;
        this.step = step;
        this.formatter = formatter;
    }

    @Override
    protected void write(Integer value) {
        this.writer.accept(value);
    }

    @Override
    protected Control<Integer> createControl() {
        return new SliderControl(this, this.min, this.max, this.step, this.formatter);
    }
}
