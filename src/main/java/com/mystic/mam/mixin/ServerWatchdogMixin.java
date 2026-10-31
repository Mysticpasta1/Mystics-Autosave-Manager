package com.mystic.mam.mixin;

import com.mystic.mam.AutosaveManager;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.ServerWatchdog;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The dedicated server watchdog already detected the hang. Save before it starts terminating the
 * JVM, because the server thread is the one that is stuck and cannot run a normal shutdown save.
 */
@Mixin(ServerWatchdog.class)
public class ServerWatchdogMixin {
    @Shadow
    @Final
    private DedicatedServer server;

    @Inject(method = "exit", at = @At("HEAD"))
    private void mam$saveBeforeWatchdogExit(CallbackInfo ci) {
        AutosaveManager.onHangDetected(this.server);
    }
}
