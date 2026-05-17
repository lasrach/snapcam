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

    private static final int WHITE = 0xCCFFFFFF;

    // ── Main overlay ──────────────────────────────────────────────────────────

    private static void renderCameraOverlay(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Font font    = mc.font;
        int w  = mc.getWindow().getGuiScaledWidth();
        int h  = mc.getWindow().getGuiScaledHeight();
        int cx = w / 2;
        int cy = h / 2;

        // Outer L-brackets (5.9%w / 9.7%h margin, arms 17.5%w / 18.5%h)
        int mx  = (int)(w * 0.059);
        int my  = (int)(h * 0.097);
        int oAH = (int)(w * 0.175);
        int oAV = (int)(h * 0.185);
        renderLCorners(g, mx, my, w - mx, h - my, 2, oAH, oAV);

        // Inner focus brackets (36–64%w, 34–66%h, arms 5.6%w / 7.2%h)
        renderLCorners(g,
                (int)(w * 0.361), (int)(h * 0.344),
                (int)(w * 0.636), (int)(h * 0.656),
                1, (int)(w * 0.056), (int)(h * 0.072));

        // Side tick scales (centred at cy, half-span ±15.2%h)
        int tickHalf = (int)(h * 0.152);
        renderTickScale(g, mx,     cy - tickHalf, cy + tickHalf, true,  w);
        renderTickScale(g, w - mx, cy - tickHalf, cy + tickHalf, false, w);

        // Centre crosshair
        renderCrosshair(g, cx, cy, Math.min(w, h));

        // Top-centre: shutter / aperture / ISO
        String info = SHUTTER_VALS[SHUTTER_IDX] + "   f/" + FSTOP_VALS[FSTOP_IDX] + "   ISO " + ISO_VALS[ISO_IDX];
        g.drawString(font, info, cx - font.width(info) / 2, my + 4, WHITE, false);

        // Bottom: ±3 EV exposure bar
        renderExposureBar(g, font, cx, h - my - font.lineHeight - 18, w);
    }

    // ── L-corner brackets ────────────────────────────────────────────────────

    private static void renderLCorners(GuiGraphics g, int x0, int y0, int x1, int y1, int t, int aH, int aV) {
        g.fill(x0,      y0,      x0 + aH, y0 + t,  WHITE);
        g.fill(x0,      y0,      x0 + t,  y0 + aV, WHITE);
        g.fill(x1 - aH, y0,      x1,      y0 + t,  WHITE);
        g.fill(x1 - t,  y0,      x1,      y0 + aV, WHITE);
        g.fill(x0,      y1 - t,  x0 + aH, y1,      WHITE);
        g.fill(x0,      y1 - aV, x0 + t,  y1,      WHITE);
        g.fill(x1 - aH, y1 - t,  x1,      y1,      WHITE);
        g.fill(x1 - t,  y1 - aV, x1,      y1,      WHITE);
    }

    // ── Side tick scales (17 ticks, major every 4th) ─────────────────────────

    private static void renderTickScale(GuiGraphics g, int x, int top, int bot, boolean right, int w) {
        int minorL = Math.max(3, w * 14 / 1000);
        int majorL = minorL * 164 / 100;
        for (int i = 0; i < 17; i++) {
            int ty  = top + i * (bot - top) / 16;
            int len = (i % 4 == 0) ? majorL : minorL;
            if (right) g.fill(x, ty, x + len, ty + 1, WHITE);
            else       g.fill(x - len, ty, x, ty + 1, WHITE);
        }
    }

    // ── ±3 EV exposure bar ────────────────────────────────────────────────────

    private static void renderExposureBar(GuiGraphics g, Font font, int cx, int y, int w) {
        int step  = Math.max(8, w * 46 / 1000);
        int halfW = 3 * step;

        g.fill(cx - halfW - 1, y, cx + halfW + 1, y + 1, WHITE);

        for (int ev = -3; ev <= 3; ev++) {
            int x     = cx + ev * step;
            int tickH = (ev == 0) ? 8 : 5;
            g.fill(x - 1, y - tickH, x + 1, y, WHITE);
            if (ev < 3) {
                int hx = x + step / 2;
                g.fill(hx, y - 3, hx + 1, y, WHITE);
            }
            String lbl = ev == 0 ? "0" : String.valueOf(Math.abs(ev));
            g.drawString(font, lbl, x - font.width(lbl) / 2, y - tickH - font.lineHeight - 1, WHITE, false);
        }

        // ▽ indicator at 0, base up, tip pointing down below rule
        g.fill(cx - 1, y + 2, cx + 1, y + 3, WHITE);
        g.fill(cx - 2, y + 3, cx + 2, y + 4, WHITE);
        g.fill(cx - 3, y + 4, cx + 3, y + 5, WHITE);
    }

    // ── Centre crosshair ──────────────────────────────────────────────────────

    private static void renderCrosshair(GuiGraphics g, int cx, int cy, int ref) {
        int arm = Math.max(4, ref * 35 / 1000);
        int gap = Math.max(2, ref * 18 / 1000);
        g.fill(cx - arm - gap, cy,             cx - gap,       cy + 1,         WHITE);
        g.fill(cx + gap,       cy,             cx + arm + gap, cy + 1,         WHITE);
        g.fill(cx,             cy - arm - gap, cx + 1,         cy - gap,       WHITE);
        g.fill(cx,             cy + gap,       cx + 1,         cy + arm + gap, WHITE);
    }
}
