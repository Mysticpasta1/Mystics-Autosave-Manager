package com.mystic.mam.mixin;

import com.mystic.mam.AutosaveManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * Holds off vanilla's autosave countdown. On 1.21.1 that countdown starts at 6000 ticks (5 minutes).
 * Reload saves run only after {@code reloadResources} has finished applying the new data.
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Shadow
    private int ticksUntilAutosave;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void mam$suppressVanillaAutosave(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        this.ticksUntilAutosave = Integer.MAX_VALUE;
    }

    @Inject(method = "reloadResources", at = @At("RETURN"), cancellable = true)
    private void mam$saveAfterReload(Collection<String> selectedIds, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        cir.setReturnValue(cir.getReturnValue().thenRun(() -> AutosaveManager.onResourcesReloaded(server)));
    }

    /**
     * The crash report has already been written at this point. Save once more before the server
     * reports the crash, in case the normal shutdown save cannot finish.
     */
    @Inject(
            method = "runServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;onServerCrash(Lnet/minecraft/CrashReport;)V"
            )
    )
    private void mam$saveBeforeCrash(CallbackInfo ci) {
        AutosaveManager.onCrashDetected((MinecraftServer) (Object) this);
    }
}
