package net.snapcam.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AttachCameraPacket(int entityId) implements CustomPacketPayload {
    public static final Type<AttachCameraPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("snapcam", "attach_camera"));

    public static final StreamCodec<FriendlyByteBuf, AttachCameraPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AttachCameraPacket::entityId,
                    AttachCameraPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
