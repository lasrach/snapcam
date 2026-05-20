package net.snapcam.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;

@Environment(EnvType.CLIENT)
public final class HandCameraController {
    private static boolean active = false;
    private static int enterCooldown = 0;
    private static boolean screenshotPending = false;
    private static int flashTicks = 0;
    private static Input savedInput = null;
    public static final int FLASH_DURATION = 6;

    // Sneak state captured by SnapcamInput.tick() before shiftKeyDown is zeroed
    // (to suppress creative fly-down). Used by SnapcamClient for exit detection.
    private static boolean lastSneakState = false;
    public static boolean getLastSneakState() { return lastSneakState; }
    public static void captureLastSneakState(boolean v) { lastSneakState = v; }

    public static void enter() {
        active = true;
        enterCooldown = 2;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                savedInput = mc.player.input;
                mc.player.input = new SnapcamInput(savedInput);
            }
        } catch (Exception ignored) {}
    }

    public static void exit() {
        active = false;
        enterCooldown = 0;
        lastSneakState = false;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null && savedInput != null) mc.player.input = savedInput;
        } catch (Exception ignored) {}
        savedInput = null;
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
