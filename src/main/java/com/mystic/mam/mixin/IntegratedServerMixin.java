package com.mystic.mam.mixin;

import net.minecraft.client.server.IntegratedServer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla writes the world the moment a singleplayer game is paused. MAM never saves on pause; the
 * world is written by the timer, a manual save, a reload, a dimension change or shutdown instead.
 *
 * <p>Only the write and its log line are removed. Pausing, ticking while paused and the time
 * resync on resume keep behaving exactly like vanilla.
 */
@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
    @Redirect(
            method = "tickServer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;saveEverything(ZZZ)Z")
    )
    private boolean mam$skipPauseSave(IntegratedServer server, boolean suppressLog, boolean flush, boolean forced) {
        return false;
    }

    @Redirect(
            method = "tickServer",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V")
    )
    private void mam$skipPauseLog(Logger logger, String message) {
    }
}
