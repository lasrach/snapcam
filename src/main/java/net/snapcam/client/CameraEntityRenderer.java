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

    @Override
    public void render(CameraEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        if (entity.isPlacedOnGround()) {
            // Pass 1: legs — fixed N/S/E/W orientation, no yaw.
            poseStack.pushPose();
            poseStack.translate(0, 0.673, 0);
            poseStack.scale(0.5f, 0.5f, 0.5f);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    new ItemStack(ModItems.CAMERA_WALL_ITEM),
                    ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
                    poseStack, buffer, entity.level(), entity.getId()
            );
            poseStack.popPose();

            // Pass 2: body — rotated to face player.
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            poseStack.translate(0, 0.673, 0);
            poseStack.scale(0.5f, 0.5f, 0.5f);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    new ItemStack(ModItems.CAMERA_TRIPOD_BODY_ITEM),
                    ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
                    poseStack, buffer, entity.level(), entity.getId() + 1
            );
            poseStack.popPose();
        } else {
            // Wall mount: lens is the model's -Z face; add 180° so it faces outward with the entity.
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot() - 180));
            poseStack.scale(0.5f, 0.5f, 0.5f);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    new ItemStack(ModItems.CAMERA_ITEM),
                    ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
                    poseStack, buffer, entity.level(), entity.getId()
            );
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CameraEntity entity) {
        return TEXTURE;
    }
}
