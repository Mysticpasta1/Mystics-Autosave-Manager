package com.mystic.mam.client;

import com.mystic.mam.Mam;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Mam.MODID, value = Dist.CLIENT)
public final class MamKeybinds {
    public static final net.minecraft.client.KeyMapping SAVE_WORLD = new net.minecraft.client.KeyMapping(
            "key.mam.save",
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.mam"
    );

    private MamKeybinds() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!SAVE_WORLD.consumeClick()) {
            return;
        }
        while (SAVE_WORLD.consumeClick()) {
            // One save per key press.
        }

        SaveWorldRequest.send();
    }

    @EventBusSubscriber(modid = Mam.MODID, value = Dist.CLIENT)
    public static class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(SAVE_WORLD);
        }
    }
}
