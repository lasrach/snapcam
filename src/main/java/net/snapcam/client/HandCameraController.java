package net.snapcam.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class HandCameraController {
    private static boolean active = false;
    private static int enterCooldown = 0;  // ticks before shooting is allowed after entering
    private static boolean screenshotPending = false;
    private static int flashTicks = 0;
    public static final int FLASH_DURATION = 6;

    public static void enter()    { active = true; enterCooldown = 2; }
    public static void exit()     { active = false; enterCooldown = 0; }
    public static boolean isActive() { return active; }
    public static boolean canShoot() { return active && enterCooldown == 0; }

    public static void requestScreenshot() { screenshotPending = true; }

    public static boolean consumeScreenshotPending() {
        if (screenshotPending) { screenshotPending = false; return true; }
        return false;
    }

    public static void startFlash()      { flashTicks = FLASH_DURATION; }
    public static int  getFlashTicks()   { return flashTicks; }

    public static void tick() {
        if (enterCooldown > 0) enterCooldown--;
        if (flashTicks > 0) flashTicks--;
    }
}
