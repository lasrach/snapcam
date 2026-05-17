package net.snapcam.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.snapcam.client.EntityCameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void snapcam$onTurn(double yaw, double pitch, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (Object) this == mc.player && EntityCameraController.isActive()) {
            EntityCameraController.redirectTurn(yaw, pitch);
            ci.cancel();
        }
    }
}
