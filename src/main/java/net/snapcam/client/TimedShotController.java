package net.snapcam.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TimedShotController {
    private static final int COUNTDOWN_TICKS = 100; // 5 seconds at 20 tps

    private static boolean active = false;
    private static int ticksRemaining = 0;
    private static int targetEntityId = -1;

    public static void start(int entityId) {
        active = true;
        ticksRemaining = COUNTDOWN_TICKS;
        targetEntityId = entityId;
    }

    public static void cancel() {
        active = false;
        ticksRemaining = 0;
        targetEntityId = -1;
    }

    public static boolean isActive() { return active; }

    public static int getTargetEntityId() { return targetEntityId; }

    /** Seconds remaining, rounded up (1–5). */
    public static int getSecondsRemaining() {
        return (ticksRemaining + 19) / 20;
    }

    /**
     * Returns true exactly once when the countdown reaches zero, signalling that
     * the caller should fire the screenshot. Resets active state after firing.
     */
    public static boolean tick() {
        if (!active) return false;
        if (--ticksRemaining <= 0) {
            active = false;
            return true;
        }
        return false;
    }
}
