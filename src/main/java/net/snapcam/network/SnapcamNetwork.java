package net.snapcam.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.Entity;
import net.snapcam.entity.CameraEntity;

public final class SnapcamNetwork {

    public static void registerServer() {
        PayloadTypeRegistry.playS2C().register(AttachCameraPacket.TYPE, AttachCameraPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DetachCameraPacket.TYPE, DetachCameraPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(DetachCameraPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Entity entity = context.player().level().getEntity(payload.entityId());
                if (entity instanceof CameraEntity camera) {
                    camera.releaseViewer(context.player().getUUID());
                }
            });
        });
    }
}
