package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.registry.ItemBuilderJS;
import com.itmecasey.modjs.registry.RegistryManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
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

    public static void register() {
        ItemTooltipEvent.BUS.addListener((ItemTooltipEvent e) -> {
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
            if (EventGroups.ITEM.hasHandlers("tooltip")) {
                EventGroups.ITEM.post("tooltip", new TooltipEventJS(stack, e.getToolTip()));
            }
        });

        TickEvent.ClientTickEvent.Post.BUS.addListener((TickEvent.ClientTickEvent.Post e) -> {
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
