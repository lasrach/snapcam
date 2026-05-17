package net.snapcam.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ZoomCameraPacket(int entityId, float focalMm) implements CustomPacketPayload {
    public static final Type<ZoomCameraPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("snapcam", "zoom_camera"));

    public static final StreamCodec<FriendlyByteBuf, ZoomCameraPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ZoomCameraPacket::entityId,
                    ByteBufCodecs.FLOAT,   ZoomCameraPacket::focalMm,
                    ZoomCameraPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
