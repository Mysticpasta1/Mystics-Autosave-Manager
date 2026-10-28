package com.mystic.mam.client;

import com.mystic.mam.Mam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Puts a Save World button in the pause menu, above the quit row, so an unwritten world can be
 * flushed without using the keybind or a command.
 */
@EventBusSubscriber(modid = Mam.MODID, value = Dist.CLIENT)
public final class MamPauseButton {
    private static final int ROW_SPACING = 4;

    private MamPauseButton() {
    }

    @SubscribeEvent
    public static void onPauseScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen)) {
            return;
        }

        // The bottom row of the pause menu is the quit button. Make room for one more row.
        Button anchor = null;
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof Button button && button.getWidth() > 150
                    && (anchor == null || button.getY() > anchor.getY())) {
                anchor = button;
            }
        }
        if (anchor == null) {
            return;
        }

        // The anchor is shifted along with the rest of its row, so remember where the row started.
        int rowX = anchor.getX();
        int rowY = anchor.getY();
        int rowWidth = anchor.getWidth();

        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget widget && widget.getY() >= rowY) {
                widget.setY(widget.getY() + Button.DEFAULT_HEIGHT + ROW_SPACING);
            }
        }

        Button save = Button.builder(Component.translatable("mam.gui.save"), button -> SaveWorldRequest.send())
                .bounds(rowX, rowY, rowWidth, Button.DEFAULT_HEIGHT)
                .build();
        save.active = canSaveHere();
        event.addListener(save);
    }

    /** The client-side copy of {@link com.mystic.mam.AutosaveManager#canSave(net.minecraft.server.level.ServerPlayer)}. */
    private static boolean canSaveHere() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        return server != null && server.isSingleplayerOwner(player.getGameProfile());
    }
}
