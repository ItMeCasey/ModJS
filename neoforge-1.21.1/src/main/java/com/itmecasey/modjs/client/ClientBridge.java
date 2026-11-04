package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.registry.ItemBuilderJS;
import com.itmecasey.modjs.registry.RegistryManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

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

    public static void register() {
        net.minecraft.client.Minecraft toastHost = net.minecraft.client.Minecraft.getInstance();
        toastHost.execute(() -> ClientTweaks.installToastComponent(new ScriptedToasts(toastHost)));

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

        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent.DebugText e) -> {
            for (boolean left : new boolean[] {true, false}) {
                String name = left ? "leftDebugInfo" : "rightDebugInfo";
                if (EventGroups.CLIENT.hasHandlers(name)) {
                    com.itmecasey.modjs.client.DebugInfoEventJS js = new com.itmecasey.modjs.client.DebugInfoEventJS();
                    EventGroups.CLIENT.post(name, js);
                    (left ? e.getLeft() : e.getRight()).addAll(js.lines());
                }
            }
        });

        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> {
            for (String line : com.itmecasey.modjs.script.ErrorNotice.lines(com.itmecasey.modjs.script.ScriptType.CLIENT)) {
                e.getPlayer().sendSystemMessage(Component.literal(line).withStyle(net.minecraft.ChatFormatting.RED));
            }
        });

        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Opening e) -> {
            if (com.itmecasey.modjs.client.ClientTweaks.autoConfirmExperimental(e.getNewScreen())) {
                e.setCanceled(true);
                return;
            }
            com.itmecasey.modjs.client.TitleScreenConfig config = com.itmecasey.modjs.client.TitleScreenConfig.current();
            if (config != null && e.getNewScreen() != null && e.getNewScreen().getClass() == net.minecraft.client.gui.screens.TitleScreen.class) {
                e.setNewScreen(new ScriptedTitleScreen(config));
            }
        });

        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e) -> {
            ItemStack stack = e.getItemStack();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (id.startsWith(RegistryManager.NAMESPACE + ":")) {
                String path = id.substring(RegistryManager.NAMESPACE.length() + 1);
                for (ItemBuilderJS builder : RegistryManager.items()) {
                    if (builder.id().equals(path)) {
                        builder.tooltips().forEach(line -> e.getToolTip().add(Component.literal(line)));
                    }
                }
            }
            if (EventGroups.ITEM.hasHandlers("dynamicTooltips")) {
                EventGroups.ITEM.post("dynamicTooltips", id, new TooltipEventJS(stack, e.getToolTip()));
            }
            if (EventGroups.ITEM.hasHandlers("tooltip")) {
                EventGroups.ITEM.post("tooltip", new TooltipEventJS(stack, e.getToolTip()));
            }
        });

        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> {
            if (EventGroups.CLIENT.hasHandlers("loggedIn")) {
                EventGroups.CLIENT.post("loggedIn", new ClientEventJS(0, 0));
            }
        });

        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            if (EventGroups.CLIENT.hasHandlers("loggedOut")) {
                EventGroups.CLIENT.post("loggedOut", new ClientEventJS(0, 0));
            }
        });

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            if (EventGroups.CLIENT.hasHandlers("tick")) {
                EventGroups.CLIENT.post("tick", new ClientEventJS(0, 0));
            }
        });

        NeoForge.EVENT_BUS.addListener((InputEvent.Key e) -> {
            if (EventGroups.CLIENT.hasHandlers("keyPressed")) {
                EventGroups.CLIENT.post("keyPressed", String.valueOf(e.getKey()), new ClientEventJS(e.getKey(), e.getAction()));
            }
        });
    }
}
