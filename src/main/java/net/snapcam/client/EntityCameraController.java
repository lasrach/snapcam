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

@Environment(EnvType.CLIENT)
public final class EntityCameraController {
    private static final Minecraft MC = Minecraft.getInstance();

    private static boolean active = false;
    private static CameraViewEntity viewEntity = null;
    private static int attachedEntityId = -1;
    private static CameraType prevCameraType = null;

    private static boolean screenshotPending = false;
    private static boolean timedShotMode = false;
    private static int flashTicks = 0;
    private static final int FLASH_DURATION = 6;

    public static void attach(int cameraEntityId) {
        if (active) detach();

        Entity cameraEntity = MC.level.getEntity(cameraEntityId);
        if (cameraEntity == null) return;

        double yawRad = Math.toRadians(cameraEntity.getYRot());
        // Model +Z maps to entity look direction; -lookX/Z is the wall lens outward direction.
        double lookX = -Math.sin(yawRad);
        double lookZ =  Math.cos(yawRad);

        double viewX, viewY, viewZ;
        float viewYaw;

        boolean onGround = cameraEntity instanceof CameraEntity cam && cam.isPlacedOnGround();
        if (onGround) {
            // Tripod: lens is the south (model +Z) face.
            // Lens centre world Y = entity.getY() + translate(0.673) + (modelY 20 - 8)*scale(0.5)/16 = +1.048
            // Forward offset: (modelZ 13 - 8)*0.5/16 = 0.156 blocks in look direction.
            viewX   = cameraEntity.getX() + lookX * 0.20;
            viewY   = cameraEntity.getY() + 1.079;
            viewZ   = cameraEntity.getZ() + lookZ * 0.20;
            viewYaw = cameraEntity.getYRot();
        } else {
            // Wall: lens is the north (model -Z) face, effective scale 1.0.
            // Lens centre world Y = entity.getY() (model centre = no vertical offset).
            // Lens is 0.4 blocks in the -look direction; place view just inside.
            viewX   = cameraEntity.getX() - lookX * 0.35;
            viewY   = cameraEntity.getY();
            viewZ   = cameraEntity.getZ() - lookZ * 0.35;
            viewYaw = cameraEntity.getYRot() + 180;
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

    public static void detach() {
        if (!active) return;
        timedShotMode = false;
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
        if (!active) return;

        // Guard: disconnect or world change
        if (MC.level == null || MC.player == null) {
            detach();
            return;
        }

        // Guard: camera entity removed (broken by another player, chunk unloaded, etc.)
        if (MC.level.getEntity(attachedEntityId) == null) {
            detach();
            return;
        }

        // Sneak to exit
        if (MC.options.keyShift.consumeClick()) {
            ClientPlayNetworking.send(new DetachCameraPacket(attachedEntityId));
            detach();
            return;
        }

        // Keep player body still: clear movement input each tick
        var inp = MC.player.input;
        inp.forwardImpulse = 0;
        inp.leftImpulse = 0;
        inp.up = false;
        inp.down = false;
        inp.left = false;
        inp.right = false;
        inp.jumping = false;
    }

    public static void redirectTurn(double yaw, double pitch) {
        if (!active || viewEntity == null) return;
        viewEntity.turn(yaw, pitch);
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
