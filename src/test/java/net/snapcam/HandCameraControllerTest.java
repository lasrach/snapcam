package net.snapcam;

import net.snapcam.client.HandCameraController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HandCameraControllerTest {

    @BeforeEach
    void reset() {
        HandCameraController.exit();
        // Drain any pending flash ticks
        for (int i = 0; i < HandCameraController.FLASH_DURATION + 1; i++) {
            HandCameraController.tick();
        }
    }

    @Test
    void initiallyInactive() {
        assertFalse(HandCameraController.isActive());
        assertFalse(HandCameraController.canShoot());
    }

    @Test
    void enterActivates() {
        HandCameraController.enter();
        assertTrue(HandCameraController.isActive());
    }

    @Test
    void cannotShootImmediatelyAfterEnter() {
        HandCameraController.enter();
        assertFalse(HandCameraController.canShoot());
    }

    @Test
    void canShootAfterEnterCooldown() {
        HandCameraController.enter();
        HandCameraController.tick();
        HandCameraController.tick();
        assertTrue(HandCameraController.canShoot());
    }

    @Test
    void cannotShootAfterOnlyOneTick() {
        HandCameraController.enter();
        HandCameraController.tick();
        assertFalse(HandCameraController.canShoot());
    }

    @Test
    void exitDeactivates() {
        HandCameraController.enter();
        HandCameraController.exit();
        assertFalse(HandCameraController.isActive());
        assertFalse(HandCameraController.canShoot());
    }

    @Test
    void screenshotPendingClearedOnConsume() {
        HandCameraController.requestScreenshot();
        assertTrue(HandCameraController.consumeScreenshotPending());
        assertFalse(HandCameraController.consumeScreenshotPending());
    }

    @Test
    void flashTicksStartAtDuration() {
        HandCameraController.startFlash();
        assertEquals(HandCameraController.FLASH_DURATION, HandCameraController.getFlashTicks());
    }

    @Test
    void flashDecaysOnTick() {
        HandCameraController.startFlash();
        HandCameraController.tick();
        assertEquals(HandCameraController.FLASH_DURATION - 1, HandCameraController.getFlashTicks());
    }

    @Test
    void flashDecaysEvenAfterExit() {
        HandCameraController.enter();
        HandCameraController.startFlash();
        HandCameraController.exit();
        int before = HandCameraController.getFlashTicks();
        HandCameraController.tick();
        assertEquals(before - 1, HandCameraController.getFlashTicks());
    }

    @Test
    void flashFullyDrainsToZero() {
        HandCameraController.startFlash();
        for (int i = 0; i < HandCameraController.FLASH_DURATION; i++) {
            HandCameraController.tick();
        }
        assertEquals(0, HandCameraController.getFlashTicks());
    }
}
