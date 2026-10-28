package com.mystic.mam.client;

import com.mystic.mam.SaveRequestPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Asks the server for a world write. Shared by the keybind and the pause menu button.
 */
public final class SaveWorldRequest {
    private SaveWorldRequest() {
    }

    public static void send() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        PacketDistributor.sendToServer(SaveRequestPayload.INSTANCE);
    }
}
