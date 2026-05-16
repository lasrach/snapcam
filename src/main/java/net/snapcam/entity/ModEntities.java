package net.snapcam.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<CameraEntity> CAMERA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("snapcam", "camera"),
            FabricEntityTypeBuilder.<CameraEntity>create(MobCategory.MISC, CameraEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 1.3f))
                    .trackRangeBlocks(10)
                    .build()
    );

    public static void register() {
        // side-effect: registers entity type via static initializer above
    }
}
