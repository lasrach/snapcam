package net.snapcam.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.snapcam.client.EntityCameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void snapcam$overrideFov(Camera camera, float partialTick, boolean useFovSetting,
                                     CallbackInfoReturnable<Double> cir) {
        if (EntityCameraController.isActive()) {
            cir.setReturnValue((double) EntityCameraController.getZoomFov());
        }
    }
}
