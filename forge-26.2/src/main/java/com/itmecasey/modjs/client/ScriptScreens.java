package com.itmecasey.modjs.client;

import com.itmecasey.modjs.registry.BlockBuilderJS;
import com.itmecasey.modjs.registry.RegistryManager;
import com.itmecasey.modjs.registry.ScriptBlocks;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registers the screen of every scripted block that has slots. */
@Mod.EventBusSubscriber(modid = "modjs", value = Dist.CLIENT)
public final class ScriptScreens {
    private ScriptScreens() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (BlockBuilderJS block : RegistryManager.blocks()) {
                if (block.containerRows() == 0 && block.hasScriptInventory() && ScriptBlocks.menu(block.id()) != null) {
                    MenuScreens.register(ScriptBlocks.menu(block.id()), ScriptContainerScreen::new);
                }
            }
        });
    }
}
