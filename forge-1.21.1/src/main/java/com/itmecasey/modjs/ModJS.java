package com.itmecasey.modjs;

import com.itmecasey.modjs.client.ClientBridge;
import com.itmecasey.modjs.command.ModJSCommands;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.format.LegacyFormat;
import com.itmecasey.modjs.format.VersionFormat;
import com.itmecasey.modjs.game.GameEvents;
import com.itmecasey.modjs.game.GameRuleBinding;
import com.itmecasey.modjs.game.SelfTest;
import com.itmecasey.modjs.game.SelfTestCases;
import com.itmecasey.modjs.game.TextBinding;
import com.itmecasey.modjs.game.UtilsBinding;
import com.itmecasey.modjs.network.ModJSNetwork;
import com.itmecasey.modjs.network.NetworkBinding;
import com.itmecasey.modjs.pack.ModJSPacks;
import com.itmecasey.modjs.recipe.RecipeReloadHook;
import com.itmecasey.modjs.registry.ContentRegistry;
import com.itmecasey.modjs.registry.RegistryManager;
import com.itmecasey.modjs.script.ExtraBindings;
import com.itmecasey.modjs.script.PlatformBinding;
import com.itmecasey.modjs.script.ScriptFolders;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;

@Mod(ModJSInfo.MOD_ID)
public class ModJS {
    public static final String MOD_ID = ModJSInfo.MOD_ID;
    private static final Logger LOGGER = LogUtils.getLogger();

    public ModJS(FMLJavaModLoadingContext context) {
        VersionFormat.use(new LegacyFormat());
        PlatformBinding.configure("1.21.1", FMLEnvironment.dist == Dist.CLIENT, id -> ModList.get().isLoaded(id));
        ExtraBindings.register((type, cx, scope) -> {
            ScriptableObject.putProperty(scope, "Utils", Context.javaToJS(new UtilsBinding(), scope));
            ScriptableObject.putProperty(scope, "Text", Context.javaToJS(new TextBinding(), scope));
            ScriptableObject.putProperty(scope, "GameRule", Context.javaToJS(new GameRuleBinding(), scope));
            ScriptableObject.putProperty(scope, "Dimensions", Context.javaToJS(new com.itmecasey.modjs.game.DimensionManager(), scope));
            ScriptableObject.putProperty(scope, "Network", Context.javaToJS(new NetworkBinding(), scope));
        });
        ScriptManager.setRoot(FMLPaths.GAMEDIR.get().resolve(MOD_ID));
        LOGGER.info("ModJS loading scripts from {}", ScriptManager.root());
        ScriptFolders.prepare(ScriptManager.root());

        // Startup scripts run once, now, so they can register content before the registries open.
        ScriptManager.of(ScriptType.STARTUP).reload();
        RegistryManager.collect();
        ContentRegistry.register(context.getModEventBus());
        com.itmecasey.modjs.unify.UnifyLootModifier.register(context.getModEventBus());
        com.itmecasey.modjs.unify.UnifyOreBiomeModifier.register(context.getModEventBus());

        context.getModEventBus().addListener((FMLCommonSetupEvent e) -> EventGroups.STARTUP.post("init", new EventJS()));
        context.getModEventBus().addListener((FMLLoadCompleteEvent e) -> EventGroups.STARTUP.post("postInit", new EventJS()));
        ModJSNetwork.init();
        GameEvents.register();
        com.itmecasey.modjs.game.ExtraGameEvents.register();
        SelfTest.register();
        if (Boolean.getBoolean("modjs.selftest")) {
            context.getModEventBus().addListener((RegisterGameTestsEvent e) -> e.register(SelfTestCases.class));
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.getModEventBus().addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) ->
                EventGroups.CLIENT.post("init", new EventJS()));
            ClientBridge.register();
        }
        context.getModEventBus().addListener(ModJSPacks::onAddPackFinders);
        MinecraftForge.EVENT_BUS.addListener(RecipeReloadHook::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(ModJSCommands::register);
    }
}
