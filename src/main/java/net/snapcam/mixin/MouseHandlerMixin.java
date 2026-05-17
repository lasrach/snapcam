package net.snapcam.mixin;

import net.minecraft.client.MouseHandler;
import net.snapcam.client.EntityCameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void snapcam$interceptScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (EntityCameraController.isActive() && yOffset != 0) {
            int dir = yOffset > 0 ? 1 : -1;
            EntityCameraController.adjustZoom(dir);
            EntityCameraController.adjustZoom(dir);
            ci.cancel();
        }
    }
}
