package net.snapcam.entity;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.snapcam.item.ModItems;
import net.snapcam.network.AttachCameraPacket;

import java.util.Optional;
import java.util.UUID;

public class CameraEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> VIEWER_UUID =
            SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> PLACED_ON_GROUND =
            SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> WALL_YAW =
            SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_CEILING =
            SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> ZOOM_FOCAL_MM =
            SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.FLOAT);

    public CameraEntity(EntityType<? extends CameraEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(VIEWER_UUID, Optional.empty());
        builder.define(PLACED_ON_GROUND, true);
        builder.define(WALL_YAW, 0.0f);
        builder.define(IS_CEILING, false);
        builder.define(ZOOM_FOCAL_MM, 0.0f);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;

        // Sneaking + right-click is handled client-side as a timed shot — don't attach.
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        UUID viewerUuid = getViewerUuid().orElse(null);

        // Currently occupied — do nothing (viewer exits via sneak)
        if (viewerUuid != null) {
            return InteractionResult.SUCCESS;
        }

        // Unoccupied: claim and enter
        entityData.set(OWNER_UUID, Optional.of(player.getUUID()));
        entityData.set(VIEWER_UUID, Optional.of(player.getUUID()));
        if (player instanceof ServerPlayer sp) {
            ServerPlayNetworking.send(sp, new AttachCameraPacket(getId()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (!(source.getEntity() instanceof Player)) return false;

        getViewerUuid().ifPresent(this::releaseViewer);
        spawnAtLocation(new ItemStack(ModItems.CAMERA_ITEM));
        discard();
        return true;
    }

    /** Server: clear viewer and notify their client. */
    public void releaseViewer(UUID viewerUuid) {
        if (level().isClientSide) return;
        Optional<UUID> current = getViewerUuid();
        if (current.isEmpty() || !current.get().equals(viewerUuid)) return;
        entityData.set(VIEWER_UUID, Optional.empty());
        // Packet not needed: client calls detach locally when receiving or when sneak-exits
    }

    public boolean isPlacedOnGround() {
        return entityData.get(PLACED_ON_GROUND);
    }

    public void setPlacedOnGround(boolean value) {
        entityData.set(PLACED_ON_GROUND, value);
        refreshDimensions();
    }

    public float getWallYaw() { return entityData.get(WALL_YAW); }

    public void setWallYaw(float value) { entityData.set(WALL_YAW, value); }

    public boolean isCeiling() { return entityData.get(IS_CEILING); }

    public void setIsCeiling(boolean value) {
        entityData.set(IS_CEILING, value);
        refreshDimensions();
    }

    public float getZoomFocalMm() { return entityData.get(ZOOM_FOCAL_MM); }

    public void setZoomFocalMm(float value) { entityData.set(ZOOM_FOCAL_MM, value); }

    public Optional<UUID> getOwnerUuid() {
        return entityData.get(OWNER_UUID);
    }

    public Optional<UUID> getViewerUuid() {
        return entityData.get(VIEWER_UUID);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        getOwnerUuid().ifPresent(uuid -> tag.putUUID("Owner", uuid));
        tag.putBoolean("OnGround", isPlacedOnGround());
        tag.putFloat("WallYaw", getWallYaw());
        tag.putBoolean("IsCeiling", isCeiling());
        if (getZoomFocalMm() > 0f) tag.putFloat("ZoomFocalMm", getZoomFocalMm());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            entityData.set(OWNER_UUID, Optional.of(tag.getUUID("Owner")));
        }
        if (tag.contains("OnGround")) {
            entityData.set(PLACED_ON_GROUND, tag.getBoolean("OnGround"));
        }
        if (tag.contains("WallYaw")) {
            entityData.set(WALL_YAW, tag.getFloat("WallYaw"));
        }
        if (tag.contains("IsCeiling")) {
            entityData.set(IS_CEILING, tag.getBoolean("IsCeiling"));
        }
        if (tag.contains("ZoomFocalMm")) {
            entityData.set(ZOOM_FOCAL_MM, tag.getFloat("ZoomFocalMm"));
        }
        // Viewer is never persisted — cleared on world reload
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        // Guard: entityData may not be initialised yet during early Entity construction.
        if (entityData != null && !isPlacedOnGround()) {
            if (isCeiling()) {
                return EntityDimensions.fixed(0.5f, 0.5f).withEyeHeight(-0.6015625f);
            }
            // Wall mount: eye at body-centre height above entity (bracket pivot).
            return EntityDimensions.fixed(0.5f, 0.5f).withEyeHeight(0.19115625f);
        }
        return super.getDimensions(pose);
    }

    @Override
    protected AABB makeBoundingBox() {
        if (isPlacedOnGround()) {
            double hw = 0.25;
            return new AABB(getX()-hw, getY(), getZ()-hw, getX()+hw, getY()+1.25, getZ()+hw);
        } else if (isCeiling()) {
            // 0.8× scale, top face flush with ceiling block (getY()+0.30).
            return new AABB(getX()-0.36, getY()-0.46, getZ()-0.36,
                            getX()+0.36, getY()+0.30, getZ()+0.36);
        } else {
            // Wall: entity sits at the bracket pivot (0.488 from block face).
            // Hitbox extends from block face (-0.488 in outward dir) to camera front (+0.2 outward).
            double wallGap   = 0.488;
            double frontDepth = 0.2;
            double halfWide  = 0.36;
            double halfTall  = 0.36;
            double yawRad    = Math.toRadians(getWallYaw());
            double outX      = -Math.sin(yawRad);
            double outZ      =  Math.cos(yawRad);
            return new AABB(
                getX() + Math.min(-outX * wallGap, outX * frontDepth) - Math.abs(outZ) * halfWide,
                getY() - halfTall,
                getZ() + Math.min(-outZ * wallGap, outZ * frontDepth) - Math.abs(outX) * halfWide,
                getX() + Math.max(-outX * wallGap, outX * frontDepth) + Math.abs(outZ) * halfWide,
                getY() + halfTall,
                getZ() + Math.max(-outZ * wallGap, outZ * frontDepth) + Math.abs(outX) * halfWide
            );
        }
    }

    @Override
    protected void checkFallDamage(double heightDifference, boolean onGround,
                                   net.minecraft.world.level.block.state.BlockState state,
                                   net.minecraft.core.BlockPos pos) {
        // cameras don't take fall damage
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.CAMERA_ITEM);
    }
}
