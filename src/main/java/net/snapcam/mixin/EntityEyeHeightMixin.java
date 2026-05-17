package net.snapcam.mixin;

import net.minecraft.world.entity.Entity;
import net.snapcam.entity.CameraEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityEyeHeightMixin {
    @Inject(method = "getEyeHeight()F", at = @At("RETURN"), cancellable = true)
    private void snapcam$cameraEyeHeight(CallbackInfoReturnable<Float> cir) {
        if ((Object)this instanceof CameraEntity cam) {
            cir.setReturnValue(cam.isPlacedOnGround() ? 1.10f : 0.0f);
        }
    }
}
