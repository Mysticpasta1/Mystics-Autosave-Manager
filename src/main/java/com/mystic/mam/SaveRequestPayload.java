package com.mystic.mam;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveRequestPayload() implements CustomPacketPayload {
    public static final SaveRequestPayload INSTANCE = new SaveRequestPayload();
    public static final Type<SaveRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mam.MODID, "request_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, SaveRequestPayload::handle);
    }

    private static void handle(SaveRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        if (!AutosaveManager.canSave(player)) {
            player.sendSystemMessage(Component.translatable("mam.message.no_permission"));
            return;
        }
        if (AutosaveManager.save(player.getServer(), true, AutosaveManager.Reason.MANUAL)) {
            player.sendSystemMessage(Component.translatable("mam.message.saved"));
        } else {
            player.sendSystemMessage(Component.translatable("mam.message.save_failed"));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
