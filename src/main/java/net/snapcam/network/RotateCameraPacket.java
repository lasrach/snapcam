package net.snapcam.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RotateCameraPacket(int entityId, float yaw, float pitch) implements CustomPacketPayload {
    public static final Type<RotateCameraPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("snapcam", "rotate_camera"));

    public static final StreamCodec<FriendlyByteBuf, RotateCameraPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,   RotateCameraPacket::entityId,
                    ByteBufCodecs.FLOAT,     RotateCameraPacket::yaw,
                    ByteBufCodecs.FLOAT,     RotateCameraPacket::pitch,
                    RotateCameraPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
