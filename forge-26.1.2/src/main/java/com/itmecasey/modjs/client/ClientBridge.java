package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.registry.ItemBuilderJS;
import com.itmecasey.modjs.registry.RegistryManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/** Client-only bridges. Only loaded on the physical client. */
public final class ClientBridge {
    private ClientBridge() {
    }

    /** What a script sees for simple client events such as ticks and key presses. */
    public static final class ClientEventJS extends EventJS {
        private final int key;
        private final int action;

        ClientEventJS(int key, int action) {
            this.key = key;
            this.action = action;
        }

        /** GLFW key code, for example 65 for A. */
        public int getKey() {
            return key;
        }

        /** 0 = release, 1 = press, 2 = repeat. */
        public int getAction() {
            return action;
        }
    }

    /** Swaps in the scripted toast manager. Called from client setup: the game instance does not exist yet while mods construct. */
    public static void installToasts() {
        net.minecraft.client.Minecraft toastHost = net.minecraft.client.Minecraft.getInstance();
        toastHost.execute(() -> ClientTweaks.installToastComponent(new ScriptedToasts(toastHost)));
    }

    public static void register() {
        com.itmecasey.modjs.client.ScriptedScreens.setScreenshotHook(name -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, message -> {
            }));
        });

        com.itmecasey.modjs.client.ScriptedScreens.setHooks(name -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            com.itmecasey.modjs.client.TitleScreenConfig config = com.itmecasey.modjs.client.ScriptedScreens.get(name);
            if (config != null) {
                mc.execute(() -> mc.setScreen(new ScriptedTitleScreen(config, mc.screen)));
            }
        }, () -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> mc.setScreen(null));
        });

        net.minecraftforge.client.event.CustomizeGuiOverlayEvent.DebugText.BUS.addListener((net.minecraftforge.client.event.CustomizeGuiOverlayEvent.DebugText e) -> {
            String name = e.getSide() == net.minecraftforge.client.event.CustomizeGuiOverlayEvent.DebugText.Side.Left ? "leftDebugInfo" : "rightDebugInfo";
            if (EventGroups.CLIENT.hasHandlers(name)) {
                com.itmecasey.modjs.client.DebugInfoEventJS js = new com.itmecasey.modjs.client.DebugInfoEventJS();
                EventGroups.CLIENT.post(name, js);
                e.getText().addAll(js.lines());
            }
        });

        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> {
            for (String line : com.itmecasey.modjs.script.ErrorNotice.lines(com.itmecasey.modjs.script.ScriptType.CLIENT)) {
                e.getPlayer().sendSystemMessage(net.minecraft.network.chat.Component.literal(line).withStyle(net.minecraft.ChatFormatting.RED));
            }
        });

        net.minecraftforge.client.event.ScreenEvent.Opening.BUS.addListener((net.minecraftforge.client.event.ScreenEvent.Opening e) -> {
            if (com.itmecasey.modjs.client.ClientTweaks.autoConfirmExperimental(e.getNewScreen())) {
                return true;
            }
            com.itmecasey.modjs.client.TitleScreenConfig config = com.itmecasey.modjs.client.TitleScreenConfig.current();
            if (config != null && e.getNewScreen() != null && e.getNewScreen().getClass() == net.minecraft.client.gui.screens.TitleScreen.class) {
                e.setNewScreen(new ScriptedTitleScreen(config));
            }
            return false;
        });

        ItemTooltipEvent.BUS.addListener((ItemTooltipEvent e) -> {
            ItemStack stack = e.getItemStack();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            for (ItemBuilderJS builder : RegistryManager.items()) {
                if (builder.fullId().equals(id)) {
                    builder.tooltips().forEach(line -> e.getToolTip().add(Component.literal(line)));
                }
            }
            if (EventGroups.ITEM.hasHandlers("dynamicTooltips")) {
                EventGroups.ITEM.post("dynamicTooltips", id, new TooltipEventJS(stack, e.getToolTip()));
            }
            if (EventGroups.ITEM.hasHandlers("tooltip")) {
                EventGroups.ITEM.post("tooltip", new TooltipEventJS(stack, e.getToolTip()));
            }
        });

        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> {
            if (EventGroups.CLIENT.hasHandlers("loggedIn")) {
                EventGroups.CLIENT.post("loggedIn", new ClientEventJS(0, 0));
            }
        });

        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            com.itmecasey.modjs.client.overlay.WorldOverlay.forgetBlocks();
            if (EventGroups.CLIENT.hasHandlers("loggedOut")) {
                EventGroups.CLIENT.post("loggedOut", new ClientEventJS(0, 0));
            }
        });

        TickEvent.ClientTickEvent.Post.BUS.addListener((TickEvent.ClientTickEvent.Post e) -> {
            ScriptedKeys.tick();
            if (EventGroups.CLIENT.hasHandlers("tick")) {
                EventGroups.CLIENT.post("tick", new ClientEventJS(0, 0));
            }
        });

        InputEvent.Key.BUS.addListener((InputEvent.Key e) -> {
            if (EventGroups.CLIENT.hasHandlers("keyPressed")) {
                EventGroups.CLIENT.post("keyPressed", String.valueOf(e.getInfo().key()), new ClientEventJS(e.getInfo().key(), e.getAction()));
            }
        });
    }
}
