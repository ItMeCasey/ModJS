package com.itmecasey.modjs;

import com.itmecasey.modjs.client.ClientBridge;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.format.ModernFormat;
import com.itmecasey.modjs.format.VersionFormat;
import com.itmecasey.modjs.game.TextBinding;
import com.itmecasey.modjs.game.UtilsBinding;
import com.itmecasey.modjs.network.NetworkBinding;
import com.itmecasey.modjs.script.ExtraBindings;
import com.itmecasey.modjs.script.PlatformBinding;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;
import com.itmecasey.modjs.command.ModJSCommands;
import com.itmecasey.modjs.game.GameEvents;
import com.itmecasey.modjs.game.GameRuleBinding;
import com.itmecasey.modjs.game.SelfTest;
import com.itmecasey.modjs.network.ModJSNetwork;
import com.itmecasey.modjs.pack.ModJSPacks;
import com.itmecasey.modjs.script.ScriptFolders;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import com.mojang.logging.LogUtils;
import com.itmecasey.modjs.recipe.RecipeReloadHook;
import com.itmecasey.modjs.registry.ContentRegistry;
import com.itmecasey.modjs.registry.RegistryManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

@Mod(ModJSInfo.MOD_ID)
public class ModJS {
    public static final String MOD_ID = ModJSInfo.MOD_ID;
    private static final Logger LOGGER = LogUtils.getLogger();

    public ModJS(FMLJavaModLoadingContext context) {
        VersionFormat.use(new ModernFormat());
        PlatformBinding.configure("26.2", FMLEnvironment.dist == Dist.CLIENT, ModList::isLoaded);
        ExtraBindings.register((type, cx, scope) -> {
            ScriptableObject.putProperty(scope, "Utils", Context.javaToJS(new UtilsBinding(), scope));
            ScriptableObject.putProperty(scope, "Text", Context.javaToJS(new TextBinding(), scope));
            ScriptableObject.putProperty(scope, "GameRule", Context.javaToJS(new GameRuleBinding(), scope));
            ScriptableObject.putProperty(scope, "Scoreboard", Context.javaToJS(new com.itmecasey.modjs.game.ScoreboardBinding(), scope));
            ScriptableObject.putProperty(scope, "Dimensions", Context.javaToJS(new com.itmecasey.modjs.game.DimensionManager(), scope));
            ScriptableObject.putProperty(scope, "Network", Context.javaToJS(new NetworkBinding(), scope));
        });
        com.itmecasey.modjs.unify.ColorSampler.setFinder(com.itmecasey.modjs.unify.ModTextureFinder.create());
        ScriptManager.setRoot(FMLPaths.GAMEDIR.get().resolve(MOD_ID));
        LOGGER.info("ModJS loading scripts from {}", ScriptManager.root());
        ScriptFolders.prepare(ScriptManager.root());

        // Startup scripts run once, now, so they can register content before the registries open.
        ScriptManager.of(ScriptType.STARTUP).reload();
        RegistryManager.collect();
        com.itmecasey.modjs.unify.UnifyOreData.install();
        ContentRegistry.register(context.getModBusGroup());
        com.itmecasey.modjs.unify.UnifyLootModifier.register(context.getModBusGroup());
        com.itmecasey.modjs.unify.UnifyOreBiomeModifier.register(context.getModBusGroup());

        FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener((FMLCommonSetupEvent e) ->
            {
                e.enqueueWork(com.itmecasey.modjs.game.BlockModifier::apply);
                EventGroups.STARTUP.post("init", new EventJS());
            });
        FMLLoadCompleteEvent.getBus(context.getModBusGroup()).addListener((FMLLoadCompleteEvent e) ->
            EventGroups.STARTUP.post("postInit", new EventJS()));
        ModJSNetwork.init();
        com.itmecasey.modjs.script.ModJSContextFactory.addWrapAdapter(com.itmecasey.modjs.game.BlockFacades::adapt);
        GameEvents.register();
        com.itmecasey.modjs.worldcraft.WorldCraftHooks.register();
        com.itmecasey.modjs.game.ExtraGameEvents.register();
        SelfTest.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) -> {
                    ClientBridge.installToasts();
                    EventGroups.CLIENT.post("init", new EventJS());
                });
            ClientBridge.register();
        }
        AddPackFindersEvent.BUS.addListener(ModJSPacks::onAddPackFinders);
        AddReloadListenerEvent.BUS.addListener(RecipeReloadHook::onAddReloadListeners);
        RegisterCommandsEvent.BUS.addListener(net.minecraftforge.eventbus.api.listener.Priority.LOWEST, ModJSCommands::register);
    }
}
