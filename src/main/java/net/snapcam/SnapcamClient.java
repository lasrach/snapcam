package net.snapcam;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.snapcam.client.CameraEntityRenderer;
import net.snapcam.client.EntityCameraController;
import net.snapcam.client.HandCameraController;
import net.snapcam.client.TimedShotController;
import net.snapcam.entity.CameraEntity;
import net.snapcam.entity.ModEntities;
import net.snapcam.item.ModItems;
import net.snapcam.network.AttachCameraPacket;

public class SnapcamClient implements ClientModInitializer {
    private boolean prevUseDown = false;
    private boolean prevSneakDown = false;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.CAMERA, CameraEntityRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(AttachCameraPacket.TYPE, (payload, context) ->
                context.client().execute(() -> EntityCameraController.attach(payload.entityId())));

        // Intercept clicks BEFORE Minecraft.handleKeybinds() consumes them
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            // Rising-edge detection for Use and Sneak — fires once per press for both
            // keyboard (consumeClick) and controller (isDown edge via Controlify).
            boolean useDown = client.options.keyUse.isDown();
            boolean useJustPressed = client.options.keyUse.consumeClick()
                    || (useDown && !prevUseDown);
            prevUseDown = useDown;

            // Sneak: combine key binding, player entity state, and rising-edge.
            boolean sneakDown = client.options.keyShift.isDown()
                    || (client.player != null && client.player.isShiftKeyDown());
            boolean sneakJustPressed = client.options.keyShift.consumeClick()
                    || (sneakDown && !prevSneakDown);
            prevSneakDown = sneakDown;

            if (EntityCameraController.isActive()) {
                // Sneak to exit (checked before screenshot)
                if (sneakDown || sneakJustPressed) {
                    EntityCameraController.requestExit();
                    return;
                }
                // Placed camera: Use = screenshot, Attack = suppress
                if (useJustPressed && EntityCameraController.canShoot()) {
                    EntityCameraController.requestScreenshot();
                }
                client.options.keyAttack.consumeClick();

            } else if (HandCameraController.isActive()) {
                // Hand photo mode: sneak released = exit, Use = shoot, Attack = suppress
                if (!sneakDown) {
                    HandCameraController.exit();
                } else if (HandCameraController.canShoot() && useJustPressed) {
                    HandCameraController.requestScreenshot();
                }
                client.options.keyAttack.consumeClick();

            } else {
                // Detect sneak+Use on a placed camera entity → start timed shot countdown.
                // This branch must run first so it takes priority over hand photo mode.
                boolean startedTimer = false;
                if (client.player != null && sneakDown) {
                    HitResult hit = client.hitResult;
                    if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof CameraEntity cam) {
                        if (useJustPressed) {
                            TimedShotController.start(cam.getId());
                            startedTimer = true;
                        }
                    }
                }

                // Normal: sneak + holding camera item + Use = enter hand photo mode
                if (!startedTimer
                        && client.player != null
                        && sneakDown
                        && client.player.getMainHandItem().getItem() == ModItems.CAMERA_ITEM
                        && useJustPressed) {
                    HandCameraController.enter();
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            EntityCameraController.tick();
            HandCameraController.tick();
            if (TimedShotController.tick()) {
                // Countdown just hit zero — attach to camera and shoot.
                EntityCameraController.attachForTimedShot(TimedShotController.getTargetEntityId());
            }
            // Cancel timer if the targeted camera becomes occupied by a viewer.
            if (TimedShotController.isActive() && client.level != null) {
                var e = client.level.getEntity(TimedShotController.getTargetEntityId());
                if (!(e instanceof CameraEntity cam) || cam.getViewerUuid().isPresent()) {
                    TimedShotController.cancel();
                }
            }
        });

        // Capture screenshot after 3D world is rendered, before any GUI (chat, overlay, etc.)
        WorldRenderEvents.END.register(context -> {
            boolean entityShot = EntityCameraController.consumeScreenshotPending();
            boolean handShot   = HandCameraController.consumeScreenshotPending();
            boolean wasTimedShot = entityShot && EntityCameraController.isTimedShotMode();
            if (entityShot || handShot) {
                Minecraft mc = Minecraft.getInstance();
                Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(),
                        msg -> mc.execute(() -> mc.player.sendSystemMessage(msg)));
                if (entityShot) EntityCameraController.startFlash();
                if (handShot)   HandCameraController.startFlash();
            }
            // Timed shot: auto-detach after screenshot so player regains control.
            if (wasTimedShot) {
                EntityCameraController.detach();
            }
        });

        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();

            if (EntityCameraController.isActive() || HandCameraController.isActive()) {
                renderCameraOverlay(guiGraphics);
            }

            // Timed-shot countdown: large centred number
            if (TimedShotController.isActive()) {
                String countdown = String.valueOf(TimedShotController.getSecondsRemaining());
                Font font = mc.font;
                int cx = w / 2;
                int cy = h / 2;
                float scale = 4f;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(cx, cy - 30, 0);
                guiGraphics.pose().scale(scale, scale, 1f);
                int tw = font.width(countdown);
                guiGraphics.drawString(font, countdown, -tw / 2, -font.lineHeight / 2, WHITE, false);
                guiGraphics.pose().popPose();
            }

            // White flash — normalise each flash by its own duration so both peak equally
            float entityFrac = EntityCameraController.getFlashTicks() > 0
                    ? (float) EntityCameraController.getFlashTicks() / EntityCameraController.getFlashDuration() : 0f;
            float handFrac = HandCameraController.getFlashTicks() > 0
                    ? (float) HandCameraController.getFlashTicks() / HandCameraController.FLASH_DURATION : 0f;
            float flashFrac = Math.max(entityFrac, handFrac);
            if (flashFrac > 0f) {
                int alpha = (int) (flashFrac * 255);
                guiGraphics.fill(0, 0, w, h, (alpha << 24) | 0x00FFFFFF);
            }
        });
    }

    // ── Static camera settings (placeholders) ────────────────────────────────

    private static final String[] ISO_VALS     = {"100", "200", "400", "800", "1.6k"};
    private static final String[] FSTOP_VALS   = {"1.4", "2.0", "2.8", "4.0", "5.6"};
    private static final String[] SHUTTER_VALS = {"1/30", "1/60", "1/125", "1/250", "1/500"};
    private static final int ISO_IDX = 2, FSTOP_IDX = 2, SHUTTER_IDX = 2;

    // ── Colours ───────────────────────────────────────────────────────────────

    private static final int WHITE  = 0xCCFFFFFF;
    private static final int DIM_BG = 0x55000000;

    // ── Main overlay ──────────────────────────────────────────────────────────

    private static void renderCameraOverlay(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w   = mc.getWindow().getGuiScaledWidth();
        int h   = mc.getWindow().getGuiScaledHeight();
        int cx  = w / 2;
        int pad = 10;

        // Vignette edges
        g.fill(0,       0,       w,     pad,   DIM_BG);
        g.fill(0,       h - pad, w,     h,     DIM_BG);
        g.fill(0,       0,       pad,   h,     DIM_BG);
        g.fill(w - pad, 0,       w,     h,     DIM_BG);

        // Double L-corner brackets
        renderDoubleCorners(g, pad, pad, w - pad, h - pad, w, h);

        // Top centre: shutter / aperture / ISO
        String info = SHUTTER_VALS[SHUTTER_IDX] + "   f/" + FSTOP_VALS[FSTOP_IDX] + "   ISO " + ISO_VALS[ISO_IDX];
        g.drawString(font, info, cx - font.width(info) / 2, pad + 4, WHITE, false);

        // Bottom centre: ±3 EV exposure bar
        renderExposureBar(g, font, cx, h - pad - 14);

        // Centre crosshair (small + with gap)
        renderCrosshair(g, cx, h / 2);

        // Boundary scales at the x-position of the outer L's vertical lines
        renderBoundaryScale(g, pad + 1, h / 2);
        renderBoundaryScale(g, w - pad - 1, h / 2);
    }

    // ── Double corner brackets ────────────────────────────────────────────────

    private static void renderDoubleCorners(GuiGraphics g, int x0, int y0, int x1, int y1, int sw, int sh) {
        int outerArm  = 30;
        int innerArmH = 60;  // horizontal leg: 3× base
        int innerArmV = 40;  // vertical leg:   2× base
        int t         = 2;

        // Inner brackets: 1/3 positions shifted inward by 5 line-widths (10px)
        int ix0 = sw / 3 + 10;
        int iy0 = sh / 3 + 10;
        int ix1 = sw * 2 / 3 - 10;
        int iy1 = sh * 2 / 3 - 10;

        // Outer corners
        g.fill(x0,            y0,            x0 + outerArm, y0 + t,        WHITE);
        g.fill(x0,            y0,            x0 + t,        y0 + outerArm, WHITE);
        g.fill(x1 - outerArm, y0,            x1,            y0 + t,        WHITE);
        g.fill(x1 - t,        y0,            x1,            y0 + outerArm, WHITE);
        g.fill(x0,            y1 - t,        x0 + outerArm, y1,            WHITE);
        g.fill(x0,            y1 - outerArm, x0 + t,        y1,            WHITE);
        g.fill(x1 - outerArm, y1 - t,        x1,            y1,            WHITE);
        g.fill(x1 - t,        y1 - outerArm, x1,            y1,            WHITE);

        // Inner corners
        g.fill(ix0,              iy0,             ix0 + innerArmH, iy0 + t,          WHITE);
        g.fill(ix0,              iy0,             ix0 + t,          iy0 + innerArmV,  WHITE);
        g.fill(ix1 - innerArmH,  iy0,             ix1,              iy0 + t,          WHITE);
        g.fill(ix1 - t,          iy0,             ix1,              iy0 + innerArmV,  WHITE);
        g.fill(ix0,              iy1 - t,         ix0 + innerArmH,  iy1,              WHITE);
        g.fill(ix0,              iy1 - innerArmV, ix0 + t,          iy1,              WHITE);
        g.fill(ix1 - innerArmH,  iy1 - t,         ix1,              iy1,              WHITE);
        g.fill(ix1 - t,          iy1 - innerArmV, ix1,              iy1,              WHITE);
    }

    // ── ±3 EV exposure bar ────────────────────────────────────────────────────

    private static void renderExposureBar(GuiGraphics g, Font font, int cx, int y) {
        int stepPx   = 20;   // pixels per full EV stop
        int halfW    = 3 * stepPx; // ±3 stops

        // Horizontal rule
        g.fill(cx - halfW - 1, y, cx + halfW + 1, y + 1, WHITE);

        // Ticks above rule and labels below
        for (int ev = -3; ev <= 3; ev++) {
            int x     = cx + ev * stepPx;
            int tickH = (ev == 0) ? 8 : 5;

            g.fill(x - 1, y - tickH, x + 1, y, WHITE);

            // Half-stop minor tick to the right
            if (ev < 3) {
                int hx = x + stepPx / 2;
                g.fill(hx, y - 3, hx + 1, y, WHITE);
            }

            // Label above tick
            String lbl = ev == 0 ? "0" : String.valueOf(Math.abs(ev));
            g.drawString(font, lbl, x - font.width(lbl) / 2, y - tickH - font.lineHeight - 1, WHITE, false);
        }

        // Triangle indicator (▲) tip touching rule, base pointing down
        g.fill(cx - 1, y + 2, cx + 1, y + 3, WHITE);
        g.fill(cx - 2, y + 3, cx + 2, y + 4, WHITE);
        g.fill(cx - 3, y + 4, cx + 3, y + 5, WHITE);
    }

    // ── Centre crosshair ──────────────────────────────────────────────────────

    private static void renderCrosshair(GuiGraphics g, int cx, int cy) {
        int arm = 6;   // length of each arm
        int gap = 3;   // gap from centre

        g.fill(cx - arm - gap, cy,     cx - gap,      cy + 1, WHITE); // left
        g.fill(cx + gap,       cy,     cx + arm + gap, cy + 1, WHITE); // right
        g.fill(cx,             cy - arm - gap, cx + 1, cy - gap, WHITE); // up
        g.fill(cx,             cy + gap,       cx + 1, cy + arm + gap, WHITE); // down
    }

    // ── Left / right boundary focus scales ───────────────────────────────────

    private static void renderBoundaryScale(GuiGraphics g, int x, int cy) {
        // Vertical ruler: 7 ticks spaced 8px apart, centre tick is longest
        int[] heights = {3, 3, 4, 6, 4, 3, 3};
        int spacing   = 8;
        int n         = heights.length;
        int totalH    = (n - 1) * spacing;

        for (int i = 0; i < n; i++) {
            int y   = cy - totalH / 2 + i * spacing;
            int len = heights[i];
            g.fill(x - len / 2, y, x + len / 2 + 1, y + 1, WHITE);
        }
    }
}
