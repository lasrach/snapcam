package net.snapcam.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Item CAMERA_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("snapcam", "camera"),
            new CameraItem(new Item.Properties().stacksTo(1))
    );

    /** Internal-only: carries the legs-only model (always fixed orientation). */
    public static final Item CAMERA_WALL_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("snapcam", "camera_tripod"),
            new Item(new Item.Properties())
    );

    /** Internal-only: carries the body-only model (rotates to face player). */
    public static final Item CAMERA_TRIPOD_BODY_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("snapcam", "camera_tripod_body"),
            new Item(new Item.Properties())
    );


    public static void register() {
        // side-effect: registers item via static initializer above
    }
}
