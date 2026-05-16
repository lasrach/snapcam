package net.snapcam.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.snapcam.client.EntityCameraController;
import net.snapcam.client.HandCameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbar(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        if (EntityCameraController.isActive() || HandCameraController.isActive()) ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        if (EntityCameraController.isActive() || HandCameraController.isActive()) ci.cancel();
    }
}
