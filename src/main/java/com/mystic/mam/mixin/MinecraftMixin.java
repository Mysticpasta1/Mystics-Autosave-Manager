package com.mystic.mam.mixin;

import com.mystic.mam.AutosaveManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client crashes and out-of-memory handling call {@code emergencySave} before the game halts the
 * integrated server. Save while that server is still open instead of relying on the shutdown hook.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "emergencySave", at = @At("HEAD"))
    private void mam$saveBeforeClientEmergencyShutdown(CallbackInfo ci) {
        IntegratedServer server = ((Minecraft) (Object) this).getSingleplayerServer();
        if (server != null) {
            AutosaveManager.onCrashDetected(server);
        }
    }
}
