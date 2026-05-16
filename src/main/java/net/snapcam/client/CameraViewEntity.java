package net.snapcam.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;

import java.util.UUID;

public class CameraViewEntity extends AbstractClientPlayer {

    public CameraViewEntity(ClientLevel level, double x, double y, double z, float yRot, float xRot) {
        super(level, new GameProfile(UUID.randomUUID(), "SnapcamView"));
        setId(-501);
        setPose(Pose.SWIMMING);
        moveTo(x, y, z, yRot, xRot);
        setInvisible(true);
    }

    @Override
    public void tick() {
        // No movement; position is fixed at the placed camera
        xOld = getX();
        yOld = getY();
        zOld = getZ();
    }

    @Override
    public float getViewXRot(float partialTick) {
        return getXRot();
    }

    @Override
    public float getViewYRot(float partialTick) {
        return getYRot();
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return EntityDimensions.fixed(0.6f, 0.6f).withEyeHeight(0f);
    }

    @Override
    public boolean isEffectiveAi() {
        return true;
    }
}
