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
import net.minecraft.world.entity.EntityType;
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

    public CameraEntity(EntityType<? extends CameraEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(VIEWER_UUID, Optional.empty());
        builder.define(PLACED_ON_GROUND, true);
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
    }

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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            entityData.set(OWNER_UUID, Optional.of(tag.getUUID("Owner")));
        }
        if (tag.contains("OnGround")) {
            entityData.set(PLACED_ON_GROUND, tag.getBoolean("OnGround"));
        }
        // Viewer is never persisted — cleared on world reload
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    protected AABB makeBoundingBox() {
        if (isPlacedOnGround()) {
            double hw = 0.25;
            return new AABB(getX()-hw, getY(), getZ()-hw, getX()+hw, getY()+1.25, getZ()+hw);
        } else {
            // Wall: 0.9 wide/tall in the attachment plane, 0.8 deep perpendicular to wall.
            double halfTall = 0.45;
            double halfWide = 0.45;
            double halfDeep = 0.425;
            // Determine depth axis from entity yaw (outward-facing after the yRot fix).
            double abscos = Math.abs(Math.cos(Math.toRadians(getYRot())));
            double abssin = Math.abs(Math.sin(Math.toRadians(getYRot())));
            double hx = (abscos > abssin) ? halfWide : halfDeep;
            double hz = (abscos > abssin) ? halfDeep : halfWide;
            return new AABB(getX()-hx, getY()-halfTall, getZ()-hz,
                            getX()+hx, getY()+halfTall, getZ()+hz);
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
