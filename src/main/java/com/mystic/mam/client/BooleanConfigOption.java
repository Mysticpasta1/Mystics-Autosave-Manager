package com.mystic.mam.client;

import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.caffeinemc.mods.sodium.client.gui.options.control.TickBoxControl;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class BooleanConfigOption extends ConfigOption<Boolean> {
    private final Consumer<Boolean> writer;

    BooleanConfigOption(Component name, Component tooltip, Supplier<Boolean> reader, Consumer<Boolean> writer) {
        super(name, tooltip, reader);
        this.writer = writer;
    }

    @Override
    protected void write(Boolean value) {
        this.writer.accept(value);
    }

    @Override
    protected Control<Boolean> createControl() {
        return new TickBoxControl(this);
    }
}
