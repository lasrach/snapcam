package net.snapcam.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.snapcam.entity.CameraEntity;
import net.snapcam.network.DetachCameraPacket;
import net.snapcam.network.RotateCameraPacket;
import net.snapcam.network.ZoomCameraPacket;

@Environment(EnvType.CLIENT)
public final class EntityCameraController {
    private static final Minecraft MC = Minecraft.getInstance();

    private static boolean active = false;
    private static CameraViewEntity viewEntity = null;
    private static int attachedEntityId = -1;
    private static CameraType prevCameraType = null;

    private static boolean isWallCamera = false;
    private static float wallYaw = 0f;
    private static boolean isCeilingCamera = false;

    private static boolean screenshotPending = false;
    private static boolean timedShotMode = false;
    private static int flashTicks = 0;
    private static final int FLASH_DURATION = 6;
    private static int attachCooldown = 0;
    private static final int ATTACH_COOLDOWN = 3;

    // Focal length stored in mm (double for smooth log stepping); FOV derived on demand.
    // 35mm equivalent: f = 18 / tan(FOV_rad/2).  Zoom steps multiplicatively (log-linear).
    private static double zoomFocalMm = 26.0;
    private static final double FOCAL_MM_MIN  = 12.0;  // ~113° FOV
    private static final double FOCAL_MM_MAX  = 200.0;
    private static final double ZOOM_FACTOR   = 1.035;  // ~3.5% per tick; full range ≈ 4 s

    public static boolean canShoot() { return active && attachCooldown == 0; }

    /**
     * Forward distance from the camera body's pan/pitch pivot to the lens glass, in world blocks.
     * Tracks the barrel extension: 0.203 at min zoom (lens plate south face, model z=14.5, scale 0.5),
     * growing by 0.21875 (7 model units × 0.5/16) at max zoom.
     */
    private static double lensForwardOffset() {
        double t = (Math.log(zoomFocalMm) - Math.log(FOCAL_MM_MIN))
                 / (Math.log(FOCAL_MM_MAX) - Math.log(FOCAL_MM_MIN));
        return 0.1875 + t * 0.21875;
    }

    public static int   getZoomFocalMm() { return (int) Math.round(zoomFocalMm); }
    public static float getZoomFov() {
        return (float)(2.0 * Math.toDegrees(Math.atan(18.0 / zoomFocalMm)));
    }
    /** direction: +1 = zoom in (longer focal length), -1 = zoom out. */
    public static void adjustZoom(int direction) {
        zoomFocalMm = (direction > 0)
                ? Math.min(FOCAL_MM_MAX, zoomFocalMm * ZOOM_FACTOR)
                : Math.max(FOCAL_MM_MIN, zoomFocalMm / ZOOM_FACTOR);
        if (attachedEntityId != -1) {
            ClientPlayNetworking.send(new ZoomCameraPacket(attachedEntityId, (float) zoomFocalMm));
        }
    }

    private static final class LockedInput extends net.minecraft.client.player.KeyboardInput {
        LockedInput(net.minecraft.client.Options opts) { super(opts); }
        @Override
        public void tick(boolean slowDown, float riftShield) {
            super.tick(slowDown, riftShield);
            forwardImpulse = 0; leftImpulse = 0;
            up = false; down = false; left = false; right = false;
            jumping = false;
            // shiftKeyDown preserved — needed for sneak-to-exit detection
        }
    }

    public static void attach(int cameraEntityId) {
        if (active) detach();
        attachCooldown = ATTACH_COOLDOWN;

        Entity cameraEntity = MC.level.getEntity(cameraEntityId);
        if (cameraEntity == null) return;

        float entityZoom = (cameraEntity instanceof CameraEntity ce) ? ce.getZoomFocalMm() : 0f;
        if (entityZoom > 0f) {
            zoomFocalMm = Math.max(FOCAL_MM_MIN, Math.min(FOCAL_MM_MAX, entityZoom));
        } else {
            double playerFovDeg = (double)(int) MC.options.fov().get();
            zoomFocalMm = 18.0 / Math.tan(Math.toRadians(playerFovDeg / 2.0));
            zoomFocalMm = Math.max(FOCAL_MM_MIN, Math.min(FOCAL_MM_MAX, zoomFocalMm));
        }

        double yawRad = Math.toRadians(cameraEntity.getYRot());
        // Model +Z maps to entity look direction; -lookX/Z is the wall lens outward direction.
        double lookX = -Math.sin(yawRad);
        double lookZ =  Math.cos(yawRad);

        double viewX, viewY, viewZ;
        float viewYaw;

        boolean onGround = cameraEntity instanceof CameraEntity cam && cam.isPlacedOnGround();
        boolean onCeiling = !onGround && cameraEntity instanceof CameraEntity cc && cc.isCeiling();
        if (onGround) {
            // Tripod: pivot at (entity.x, entity.y+0.95, entity.z); body centre 0.12925 above pivot.
            double lensF    = lensForwardOffset();
            double cosPitch = Math.cos(Math.toRadians(cameraEntity.getXRot()));
            double sinPitch = Math.sin(Math.toRadians(cameraEntity.getXRot()));
            viewX   = cameraEntity.getX() + lookX * cosPitch * lensF;
            viewY   = cameraEntity.getY() + 0.95 + 0.12925 - sinPitch * lensF;
            viewZ   = cameraEntity.getZ() + lookZ * cosPitch * lensF;
            viewYaw = cameraEntity.getYRot();
            isWallCamera = false;
            isCeilingCamera = false;
            wallYaw = 0f;
        } else if (onCeiling) {
            // Ceiling mount: pivot at rod bottom (model y=3.75 → world -0.0828 from entity).
            // Body top (y=26) at pivot; body centre 0.15625 below pivot; lens 0.1875 forward.
            isWallCamera = false;
            isCeilingCamera = true;
            wallYaw = 0f;
            double pivX = cameraEntity.getX();
            double pivY = cameraEntity.getY() - 0.1328125;
            double pivZ = cameraEntity.getZ();
            double camYawRad = Math.toRadians(cameraEntity.getYRot());
            double cosPitch = Math.cos(Math.toRadians(cameraEntity.getXRot()));
            double sinPitch = Math.sin(Math.toRadians(cameraEntity.getXRot()));
            double lensF    = lensForwardOffset();
            viewX   = pivX + (-Math.sin(camYawRad)) * cosPitch * lensF;
            viewY   = pivY - 0.46875 * cosPitch - sinPitch * lensF;
            viewZ   = pivZ + Math.cos(camYawRad) * cosPitch * lensF;
            viewYaw = cameraEntity.getYRot();
        } else {
            // Wall mount: entity is now at the bracket pivot (V-arm top); body centre 0.106 above pivot; lens 0.1875 forward.
            isWallCamera = true;
            isCeilingCamera = false;
            wallYaw = cameraEntity instanceof CameraEntity wc ? wc.getWallYaw() : cameraEntity.getYRot();
            double wallYawRad = Math.toRadians(wallYaw);
            double outX = -Math.sin(wallYawRad);
            double outZ =  Math.cos(wallYawRad);
            double pivX = cameraEntity.getX();
            double pivY = cameraEntity.getY() - 0.0125;
            double pivZ = cameraEntity.getZ();
            double camYawRad = Math.toRadians(cameraEntity.getYRot());
            double cosPitch = Math.cos(Math.toRadians(cameraEntity.getXRot()));
            double sinPitch = Math.sin(Math.toRadians(cameraEntity.getXRot()));
            double lensF    = lensForwardOffset();
            viewX   = pivX + (-Math.sin(camYawRad)) * cosPitch * lensF;
            viewY   = pivY + 0.20365625 - sinPitch * lensF;
            viewZ   = pivZ + Math.cos(camYawRad) * cosPitch * lensF;
            viewYaw = cameraEntity.getYRot();
        }

        viewEntity = new CameraViewEntity(
                (ClientLevel) MC.level,
                viewX,
                viewY,
                viewZ,
                viewYaw,
                cameraEntity.getXRot()
        );
        ((ClientLevel) MC.level).addEntity(viewEntity);
        MC.smartCull = false;

        // Switch to third-person so MC renders the real player body in the scene.
        // Camera.setup() is overridden (CameraMixin) to position at the exact camera
        // entity location without the normal third-person backward zoom.
        prevCameraType = MC.options.getCameraType();
        MC.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        MC.setCameraEntity(viewEntity);
        if (MC.player != null) MC.player.input = new LockedInput(MC.options);
        active = true;
        attachedEntityId = cameraEntityId;
    }

    /** Attaches to a camera entity for a timed shot — immediately requests a screenshot and auto-detaches. */
    public static void attachForTimedShot(int cameraEntityId) {
        attach(cameraEntityId);
        if (active) {
            screenshotPending = true;
            timedShotMode = true;
        }
    }

    public static boolean isTimedShotMode() { return timedShotMode; }

    public static void requestExit() {
        if (!active) return;
        ClientPlayNetworking.send(new DetachCameraPacket(attachedEntityId));
        detach();
    }

    public static int getAttachedEntityId() { return attachedEntityId; }

    public static void detach() {
        if (!active) return;
        timedShotMode = false;
        isWallCamera = false;
        isCeilingCamera = false;
        wallYaw = 0f;
        MC.smartCull = true;
        if (MC.player != null) MC.setCameraEntity(MC.player);
        if (prevCameraType != null) {
            MC.options.setCameraType(prevCameraType);
            prevCameraType = null;
        }
        if (viewEntity != null) {
            if (MC.level != null) {
                ((ClientLevel) MC.level).removeEntity(viewEntity.getId(), RemovalReason.DISCARDED);
            }
            viewEntity = null;
        }
        if (MC.player != null) {
            MC.player.input = new net.minecraft.client.player.KeyboardInput(MC.options);
        }
        active = false;
        attachedEntityId = -1;
    }

    /** Called each client tick while active. Handles sneak-to-exit and screenshot. */
    public static void tick() {
        if (flashTicks > 0) flashTicks--;
        if (attachCooldown > 0) attachCooldown--;
        if (!active) return;

        // Guard: disconnect or world change
        if (MC.level == null || MC.player == null) {
            detach();
            return;
        }

        // Guard: camera entity removed (broken by another player, chunk unloaded, etc.)
        Entity camEnt = MC.level.getEntity(attachedEntityId);
        if (camEnt == null) {
            detach();
            return;
        }

        // Tripod: keep view entity at the lens point as the camera pans/tilts.
        if (camEnt instanceof CameraEntity cam && cam.isPlacedOnGround() && viewEntity != null) {
            double yawRad   = Math.toRadians(viewEntity.getYRot());
            double pitchRad = Math.toRadians(viewEntity.getXRot());
            double sinPitch = Math.sin(pitchRad);
            double cosPitch = Math.cos(pitchRad);
            double lensF    = lensForwardOffset();
            viewEntity.moveTo(
                cam.getX() + (-Math.sin(yawRad)) * cosPitch * lensF,
                cam.getY() + 0.95 + 0.12925 - sinPitch * lensF,
                cam.getZ() + Math.cos(yawRad) * cosPitch * lensF
            );
        }

        // Ceiling: keep view entity at the lens point as the camera pans/tilts around the rod-bottom pivot.
        // Pivot at (0, -0.1328, 0) from entity; body-centre 0.15625 below pivot; lens 0.1875 forward.
        if (isCeilingCamera && camEnt instanceof CameraEntity && viewEntity != null) {
            double pivX = camEnt.getX();
            double pivY = camEnt.getY() - 0.1328125;
            double pivZ = camEnt.getZ();
            double camYawRad = Math.toRadians(viewEntity.getYRot());
            double pitchRad  = Math.toRadians(viewEntity.getXRot());
            double cosPitch  = Math.cos(pitchRad);
            double sinPitch  = Math.sin(pitchRad);
            double lensF     = lensForwardOffset();
            viewEntity.moveTo(
                pivX + (-Math.sin(camYawRad)) * cosPitch * lensF,
                pivY - 0.46875 * cosPitch - sinPitch * lensF,
                pivZ + Math.cos(camYawRad) * cosPitch * lensF
            );
        }

        // Wall: keep view entity at the lens point as the camera pans/tilts around the bracket pivot.
        // Pivot at (0, -0.0125, +0.188 outward) from entity; body-centre 0.106 above pivot; lens 0.1875 forward.
        if (isWallCamera && camEnt instanceof CameraEntity && viewEntity != null) {
            double wallYawRad = Math.toRadians(wallYaw);
            double outX = -Math.sin(wallYawRad);
            double outZ =  Math.cos(wallYawRad);
            double pivX = camEnt.getX();
            double pivY = camEnt.getY() - 0.0125;
            double pivZ = camEnt.getZ();
            double camYawRad = Math.toRadians(viewEntity.getYRot());
            double pitchRad  = Math.toRadians(viewEntity.getXRot());
            double cosPitch  = Math.cos(pitchRad);
            double sinPitch  = Math.sin(pitchRad);
            double lensF     = lensForwardOffset();
            viewEntity.moveTo(
                pivX + (-Math.sin(camYawRad)) * cosPitch * lensF,
                pivY + 0.20365625 - sinPitch * lensF,
                pivZ + Math.cos(camYawRad) * cosPitch * lensF
            );
        }

        // Sneak-to-exit is handled in START_CLIENT_TICK (SnapcamClient) for controller compat.
        // Movement suppression (including jump) is handled by LockedInput installed on attach.
    }

    public static void redirectTurn(double yaw, double pitch) {
        if (!active || viewEntity == null) return;
        viewEntity.turn(yaw, pitch);
        if (isWallCamera) {
            float relYaw = viewEntity.getYRot() - wallYaw;
            while (relYaw >  180) relYaw -= 360;
            while (relYaw < -180) relYaw += 360;
            relYaw = Math.max(-100f, Math.min(100f, relYaw));
            viewEntity.setYRot(wallYaw + relYaw);
        }
        viewEntity.setXRot(Math.max(-70f, Math.min(80f, viewEntity.getXRot())));
        ClientPlayNetworking.send(new RotateCameraPacket(attachedEntityId, viewEntity.getYRot(), viewEntity.getXRot()));
    }

    public static boolean isActive() { return active; }

    public static CameraViewEntity getViewEntity() { return viewEntity; }

    public static void requestScreenshot() { screenshotPending = true; }

    /** Returns true and clears the flag if a screenshot was requested this tick. */
    public static boolean consumeScreenshotPending() {
        if (screenshotPending) { screenshotPending = false; return true; }
        return false;
    }

    public static void startFlash() { flashTicks = FLASH_DURATION; }

    public static int getFlashTicks() { return flashTicks; }

    public static int getFlashDuration() { return FLASH_DURATION; }
}
