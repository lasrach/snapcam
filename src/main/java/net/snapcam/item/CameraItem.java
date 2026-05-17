package net.snapcam.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.snapcam.entity.CameraEntity;
import net.snapcam.entity.ModEntities;

public class CameraItem extends Item {
    public CameraItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (!level.isClientSide) {
            Direction face = context.getClickedFace();
            boolean onGround = (face == Direction.UP);

            // Centre on the clicked block face, then push outward for wall mounts
            BlockPos blockPos = context.getClickedPos();
            Vec3 faceNormal = Vec3.atLowerCornerOf(face.getNormal());
            Vec3 blockCenter = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
            Vec3 pos = blockCenter.add(faceNormal.scale(0.5)); // face surface centre
            if (!onGround) {
                pos = pos.add(faceNormal.scale(0.3));
            }

            float yRot;
            if (onGround) {
                yRot = (float) Math.toDegrees(Math.atan2(-(player.getX() - pos.x), player.getZ() - pos.z));
            } else {
                // Entity faces outward from the wall (matches face normal direction).
                yRot = face.toYRot();
            }

            // Reject if a camera is already occupying this spot
            AABB check = new AABB(pos.x - 0.5, pos.y - 0.5, pos.z - 0.5,
                                  pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
            if (!level.getEntitiesOfClass(CameraEntity.class, check).isEmpty()) {
                return InteractionResult.FAIL;
            }

            CameraEntity camera = new CameraEntity(ModEntities.CAMERA, level);
            camera.setNoGravity(true);
            camera.setPlacedOnGround(onGround);
            camera.moveTo(pos.x, pos.y, pos.z, yRot, 0.0f);
            level.addFreshEntity(camera);

            if (!player.isCreative()) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
