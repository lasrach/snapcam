package net.snapcam.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.Input;

/**
 * Wraps the player's existing Input (keyboard or Controlify's DualInput) so the
 * full input chain still runs each tick, then suppresses movement/jump/fly-down
 * fields based on which Snapcam mode is active.
 */
@Environment(EnvType.CLIENT)
public final class SnapcamInput extends Input {
    private final Input wrapped;

    public SnapcamInput(Input wrapped) {
        this.wrapped = wrapped;
    }

    public Input getWrapped() { return wrapped; }

    @Override
    public void tick(boolean slowDown, float slowdownFactor) {
        wrapped.tick(slowDown, slowdownFactor);

        // Mirror all values so reads on 'this' see the full input state.
        this.forwardImpulse = wrapped.forwardImpulse;
        this.leftImpulse    = wrapped.leftImpulse;
        this.up             = wrapped.up;
        this.down           = wrapped.down;
        this.left           = wrapped.left;
        this.right          = wrapped.right;
        this.jumping        = wrapped.jumping;
        this.shiftKeyDown   = wrapped.shiftKeyDown;

        if (EntityCameraController.isActive()) {
            // Capture before zeroing so START_CLIENT_TICK can use it for zoom.
            EntityCameraController.setCapturedForwardImpulse(this.forwardImpulse);
            this.forwardImpulse = 0;
            this.leftImpulse    = 0;
            this.up             = false;
            this.down           = false;
            this.left           = false;
            this.right          = false;
            this.jumping        = false;
            // shiftKeyDown left intact — SnapcamClient uses it for sneak-to-exit detection
        } else if (HandCameraController.isActive()) {
            // Capture before zeroing so SnapcamClient can use it for exit detection.
            HandCameraController.captureLastSneakState(this.shiftKeyDown);
            this.shiftKeyDown = false;
            // Movement and jumping left intact — player navigates freely in handheld mode
        }
    }
}
