package net.snapcam.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DetachCameraPacket(int entityId) implements CustomPacketPayload {
    public static final Type<DetachCameraPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("snapcam", "detach_camera"));

    public static final StreamCodec<FriendlyByteBuf, DetachCameraPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DetachCameraPacket::entityId,
                    DetachCameraPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
