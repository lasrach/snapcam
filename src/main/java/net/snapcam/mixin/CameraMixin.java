package net.snapcam.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.snapcam.client.CameraViewEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {
    @Shadow private Entity entity;
    @Shadow private float eyeHeightOld;
    @Shadow private float eyeHeight;
    @Shadow private boolean detached;
    @Shadow private void setPosition(double x, double y, double z) { throw new AssertionError(); }
    @Shadow private void setRotation(float yRot, float xRot) { throw new AssertionError(); }

    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private void onSetup(BlockGetter area, Entity newFocusedEntity, boolean thirdPerson,
                         boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!(newFocusedEntity instanceof CameraViewEntity cve)) return;
        // Set camera.entity to the real player so LevelRenderer's LocalPlayer check
        // (entity instanceof LocalPlayer && camera.getEntity() != entity → skip) allows
        // the player body to render. Camera position/rotation come from setPosition/setRotation.
        Entity cameraEntity = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player : cve;
        this.entity = cameraEntity;
        this.eyeHeightOld = this.eyeHeight = cve.getEyeHeight();
        // detached=true disables the vanilla first-person entity-suppression check
        // in LevelRenderer so no entity is skipped due to camera proximity.
        this.detached = true;
        Vec3 eye = cve.getEyePosition(tickDelta);
        setPosition(eye.x, eye.y, eye.z);
        setRotation(cve.getViewYRot(tickDelta), cve.getViewXRot(tickDelta));
        ci.cancel();
    }
}
