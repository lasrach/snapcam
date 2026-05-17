package net.snapcam.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.KeyboardInput;

@Environment(EnvType.CLIENT)
public final class HandCameraController {
    private static boolean active = false;
    private static int enterCooldown = 0;
    private static boolean screenshotPending = false;
    private static int flashTicks = 0;
    public static final int FLASH_DURATION = 6;

    private static final class HandInput extends KeyboardInput {
        HandInput(Options opts) { super(opts); }
        @Override
        public void tick(boolean slowDown, float riftShield) {
            super.tick(slowDown, riftShield);
            shiftKeyDown = false; // suppress creative fly-down while shooting handheld
        }
    }

    public static void enter() {
        active = true;
        enterCooldown = 2;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) mc.player.input = new HandInput(mc.options);
    }

    public static void exit() {
        active = false;
        enterCooldown = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) mc.player.input = new KeyboardInput(mc.options);
    }

    public static boolean isActive()  { return active; }
    public static boolean canShoot()  { return active && enterCooldown == 0; }

    public static void requestScreenshot() { screenshotPending = true; }

    public static boolean consumeScreenshotPending() {
        if (screenshotPending) { screenshotPending = false; return true; }
        return false;
    }

    public static void startFlash()    { flashTicks = FLASH_DURATION; }
    public static int  getFlashTicks() { return flashTicks; }

    public static void tick() {
        if (enterCooldown > 0) enterCooldown--;
        if (flashTicks > 0) flashTicks--;
    }
}
