package net.snapcam;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.snapcam.entity.ModEntities;
import net.snapcam.item.ModItems;
import net.snapcam.network.SnapcamNetwork;

public class SnapcamMod implements ModInitializer {
    public static final String MOD_ID = "snapcam";

    @Override
    public void onInitialize() {
        ModEntities.register();
        ModItems.register();
        SnapcamNetwork.registerServer();

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(ModItems.CAMERA_ITEM));
    }
}
