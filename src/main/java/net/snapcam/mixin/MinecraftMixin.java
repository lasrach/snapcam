package net.snapcam.mixin;

import net.minecraft.client.Minecraft;
import net.snapcam.client.EntityCameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    /** Clean up camera state if the player disconnects while viewing. */
    @Inject(method = "disconnect*", at = @At("HEAD"))
    private void onDisconnect(CallbackInfo ci) {
        if (EntityCameraController.isActive()) {
            EntityCameraController.detach();
        }
    }
}
