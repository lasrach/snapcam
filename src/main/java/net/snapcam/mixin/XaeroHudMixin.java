package net.snapcam.mixin;

import net.snapcam.client.EntityCameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.hud.render.HudRenderer", remap = false)
public class XaeroHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void onRender(CallbackInfo ci) {
        if (EntityCameraController.isActive()) ci.cancel();
    }
}
