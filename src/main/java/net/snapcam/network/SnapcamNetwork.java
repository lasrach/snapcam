package net.snapcam.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.Entity;
import net.snapcam.entity.CameraEntity;

public final class SnapcamNetwork {

    public static void registerServer() {
        PayloadTypeRegistry.playS2C().register(AttachCameraPacket.TYPE, AttachCameraPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DetachCameraPacket.TYPE, DetachCameraPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RotateCameraPacket.TYPE, RotateCameraPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ZoomCameraPacket.TYPE, ZoomCameraPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(DetachCameraPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Entity entity = context.player().level().getEntity(payload.entityId());
                if (entity instanceof CameraEntity camera) {
                    camera.releaseViewer(context.player().getUUID());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RotateCameraPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Entity entity = context.player().level().getEntity(payload.entityId());
                if (entity instanceof CameraEntity camera) {
                    camera.setYRot(payload.yaw());
                    camera.setXRot(payload.pitch());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ZoomCameraPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Entity entity = context.player().level().getEntity(payload.entityId());
                if (entity instanceof CameraEntity camera
                        && camera.getViewerUuid().map(context.player().getUUID()::equals).orElse(false)) {
                    camera.setZoomFocalMm(payload.focalMm());
                }
            });
        });
    }
}
