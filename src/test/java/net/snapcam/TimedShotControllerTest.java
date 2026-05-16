package net.snapcam;

import net.snapcam.client.TimedShotController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimedShotControllerTest {

    @BeforeEach
    void reset() {
        TimedShotController.cancel();
    }

    @Test
    void initiallyInactive() {
        assertFalse(TimedShotController.isActive());
        assertEquals(-1, TimedShotController.getTargetEntityId());
    }

    @Test
    void startActivatesWithGivenEntityId() {
        TimedShotController.start(42);
        assertTrue(TimedShotController.isActive());
        assertEquals(42, TimedShotController.getTargetEntityId());
    }

    @Test
    void startShowsFiveSecondsRemaining() {
        TimedShotController.start(1);
        assertEquals(5, TimedShotController.getSecondsRemaining());
    }

    @Test
    void cancelDeactivates() {
        TimedShotController.start(1);
        TimedShotController.cancel();
        assertFalse(TimedShotController.isActive());
        assertEquals(-1, TimedShotController.getTargetEntityId());
    }

    @Test
    void tickDoesNothingWhenInactive() {
        assertFalse(TimedShotController.tick());
    }

    @Test
    void tickCountsDownSeconds() {
        TimedShotController.start(1);
        // 20 ticks = 1 second; after 20 ticks, 4 seconds remain
        for (int i = 0; i < 20; i++) TimedShotController.tick();
        assertEquals(4, TimedShotController.getSecondsRemaining());
    }

    @Test
    void tickReturnsTrueExactlyOnceAfterFullCountdown() {
        TimedShotController.start(1);
        int fireCount = 0;
        for (int i = 0; i < 110; i++) {
            if (TimedShotController.tick()) fireCount++;
        }
        assertEquals(1, fireCount);
    }

    @Test
    void deactivatesAfterFiring() {
        TimedShotController.start(1);
        for (int i = 0; i < 100; i++) TimedShotController.tick();
        assertFalse(TimedShotController.isActive());
    }

    @Test
    void secondsRemainingRoundsUp() {
        TimedShotController.start(1);
        // After 99 ticks, 1 tick remains — should still show 1 second, not 0
        for (int i = 0; i < 99; i++) TimedShotController.tick();
        assertEquals(1, TimedShotController.getSecondsRemaining());
    }

    @Test
    void restartResetsCountdown() {
        TimedShotController.start(1);
        for (int i = 0; i < 50; i++) TimedShotController.tick();
        TimedShotController.start(99);
        assertEquals(5, TimedShotController.getSecondsRemaining());
        assertEquals(99, TimedShotController.getTargetEntityId());
    }
}
