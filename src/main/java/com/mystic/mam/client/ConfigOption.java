package com.mystic.mam.client;

import net.caffeinemc.mods.sodium.client.gui.options.Option;
import net.caffeinemc.mods.sodium.client.gui.options.OptionFlag;
import net.caffeinemc.mods.sodium.client.gui.options.OptionImpact;
import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * A Sodium option that reads straight from {@link com.mystic.mam.Config} and writes back to it.
 * Values are persisted the moment the widget is used, so Sodium's apply pass has nothing left to do.
 */
abstract class ConfigOption<T> implements Option<T> {
    /** Sodium only uses this as a type token for its own change bookkeeping. */
    private static final OptionStorage<Object> STORAGE = new OptionStorage<Object>() {
        @Override
        public Object getData() {
            return this;
        }

        @Override
        public void save() {
        }
    };

    private final Component name;
    private final Component tooltip;
    private final Supplier<T> reader;

    ConfigOption(Component name, Component tooltip, Supplier<T> reader) {
        this.name = name;
        this.tooltip = tooltip;
        this.reader = reader;
    }

    protected abstract void write(T value);

    protected abstract Control<T> createControl();

    @Override
    public Component getName() {
        return this.name;
    }

    @Override
    public Component getTooltip() {
        return this.tooltip;
    }

    @Override
    public OptionImpact getImpact() {
        return OptionImpact.LOW;
    }

    @Override
    public Control<T> getControl() {
        return createControl();
    }

    @Override
    public T getValue() {
        return this.reader.get();
    }

    @Override
    public void setValue(T value) {
        write(value);
    }

    @Override
    public void reset() {
    }

    @Override
    public OptionStorage<?> getStorage() {
        return STORAGE;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean hasChanged() {
        // Already written on click; never leave Sodium waiting for an apply pass.
        return false;
    }

    @Override
    public void applyChanges() {
    }

    @Override
    public Collection<OptionFlag> getFlags() {
        return Collections.emptyList();
    }
}
