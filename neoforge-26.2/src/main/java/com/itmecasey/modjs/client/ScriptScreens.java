package com.itmecasey.modjs.client;

import com.itmecasey.modjs.registry.BlockBuilderJS;
import com.itmecasey.modjs.registry.RegistryManager;
import com.itmecasey.modjs.registry.ScriptBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Registers the screen of every scripted block that has slots. */
@EventBusSubscriber(modid = "modjs", value = Dist.CLIENT)
public final class ScriptScreens {
    private ScriptScreens() {
    }

    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        for (BlockBuilderJS block : RegistryManager.blocks()) {
            if (block.containerRows() == 0 && block.hasScriptInventory() && ScriptBlocks.menu(block.id()) != null) {
                event.register(ScriptBlocks.menu(block.id()), ScriptContainerScreen::new);
            }
        }
    }
}
