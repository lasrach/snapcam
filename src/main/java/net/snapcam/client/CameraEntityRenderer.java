package net.snapcam.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.snapcam.entity.CameraEntity;
import net.snapcam.item.ModItems;

@Environment(EnvType.CLIENT)
public class CameraEntityRenderer extends EntityRenderer<CameraEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("snapcam", "textures/entity/camera.png");

    public CameraEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * Barrel extension in world-space blocks along the camera's +Z axis.
     * t is log-linear across the focal-length range (12–200 mm).
     * Max extension = 7 model units × scale 0.5 / 16 = 0.21875 blocks.
     */
    private static float barrelOffset(CameraEntity entity) {
        float fMm = entity.getZoomFocalMm();
        if (fMm <= 0f) return 0f;
        double logMin = Math.log(12.0);
        double logMax = Math.log(200.0);
        double logF   = Math.log(Math.max(12.0, Math.min(200.0, fMm)));
        float t = (float) ((logF - logMin) / (logMax - logMin));
        return t * 0.21875f;
    }

    @Override
    public void render(CameraEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        if (entity.isPlacedOnGround()) {
            // Pass 1: legs — fixed N/S/E/W orientation, no yaw.
            poseStack.pushPose();
            poseStack.translate(0, 0.673, 0);
            poseStack.scale(0.5f, 0.5f, 0.5f);
            renderItem(new ItemStack(ModItems.CAMERA_WALL_ITEM), packedLight, poseStack, buffer, entity, 0);
            poseStack.popPose();

            // Pass 2: body + barrel — yaw pans around world Y, pitch tilts around local X.
            float bOff = barrelOffset(entity);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            poseStack.translate(0, 0.95, 0);
            poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
            poseStack.translate(0, -0.277, 0);

            poseStack.pushPose();
            poseStack.scale(0.5f, 0.5f, 0.5f);
            renderItem(new ItemStack(ModItems.CAMERA_TRIPOD_BODY_ITEM), packedLight, poseStack, buffer, entity, 1);
            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(0, 0, bOff);
            poseStack.scale(0.5f, 0.5f, 0.5f);
            renderItem(new ItemStack(ModItems.CAMERA_LENS_BARREL_ITEM), packedLight, poseStack, buffer, entity, 2);
            poseStack.popPose();

            poseStack.popPose();

        } else if (entity.isCeiling()) {
            // Pass 1: bracket (plate + rod) — symmetric, no yaw rotation needed.
            poseStack.pushPose();
            poseStack.translate(0, 0.05, 0);
            poseStack.scale(0.5f, 0.5f, 0.5f);
            renderItem(new ItemStack(ModItems.CAMERA_CEILING_BRACKET_ITEM), packedLight, poseStack, buffer, entity, 0);
            poseStack.popPose();

            // Pass 2: body + barrel — pivot at rod bottom (model y=6 → world -0.0125).
            float bOff = barrelOffset(entity);
            poseStack.pushPose();
            poseStack.translate(0, -0.1328125, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
            poseStack.translate(0, -0.5625, 0);

            poseStack.pushPose();
            poseStack.scale(0.5f, 0.5f, 0.5f);
            renderItem(new ItemStack(ModItems.CAMERA_TRIPOD_BODY_ITEM), packedLight, poseStack, buffer, entity, 1);
            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(0, 0, bOff);
            poseStack.scale(0.5f, 0.5f, 0.5f);
            renderItem(new ItemStack(ModItems.CAMERA_LENS_BARREL_ITEM), packedLight, poseStack, buffer, entity, 2);
            poseStack.popPose();

            poseStack.popPose();

        } else {
            float wallYaw = entity.getWallYaw();

            // Pass 1: bracket (plate + arm) — always fixed to wall normal direction.
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-wallYaw));
            poseStack.translate(0, -0.192, -0.488);
            poseStack.scale(0.5f, 0.5f, 0.5f);
            renderItem(new ItemStack(ModItems.CAMERA_WALL_BRACKET_ITEM), packedLight, poseStack, buffer, entity, 0);
            poseStack.popPose();

            // Pass 2: body + barrel — pivot at ball-head on top of vertical arm.
            float bOff = barrelOffset(entity);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-wallYaw));
            poseStack.translate(0, -0.0125, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(wallYaw));
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
            poseStack.translate(0, -0.30, 0);

            poseStack.pushPose();
            poseStack.scale(0.5f, 0.5f, 0.5f);
            renderItem(new ItemStack(ModItems.CAMERA_TRIPOD_BODY_ITEM), packedLight, poseStack, buffer, entity, 1);
            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(0, 0, bOff);
            poseStack.scale(0.5f, 0.5f, 0.5f);
            renderItem(new ItemStack(ModItems.CAMERA_LENS_BARREL_ITEM), packedLight, poseStack, buffer, entity, 2);
            poseStack.popPose();

            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void renderItem(ItemStack stack, int packedLight, PoseStack poseStack,
                            MultiBufferSource buffer, CameraEntity entity, int idOffset) {
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), entity.getId() + idOffset);
    }

    @Override
    public ResourceLocation getTextureLocation(CameraEntity entity) {
        return TEXTURE;
    }
}
