package com.itmecasey.modjs.compat.jade;

import com.itmecasey.modjs.info.InfoBridge;
import com.itmecasey.modjs.info.InfoDispatch;
import com.itmecasey.modjs.info.InfoEventJS;
import com.itmecasey.modjs.info.InfoLine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The client half of the Jade plugin: adds the script lines to Jade's tooltip. Kept apart so a dedicated server never loads it. */
final class ModJSJadeClient {
    /** Late, so the tags of other providers' lines exist when a script hides them. */
    private static final int PRIORITY = 5000;
    private static final int BAR_CELLS = 10;

    private ModJSJadeClient() {
    }

    static void register(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new IBlockComponentProvider() {
            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                List<InfoLine> lines = new ArrayList<>();
                Set<String> hidden = new LinkedHashSet<>();
                InfoBridge.read(accessor.getServerData(), lines, hidden);
                if (InfoDispatch.active("block")) {
                    InfoEventJS event = InfoBridge.blockEvent(accessor.getLevel(), accessor.getPosition(), accessor.getBlockState(),
                        accessor.getBlockEntity(), "client", accessor.showDetails());
                    InfoDispatch.onClient(event, accessor.getBlockEntity() != null);
                    lines.addAll(event.lines());
                    hidden.addAll(event.hiddenTags());
                }
                show(tooltip, lines, hidden);
            }

            @Override
            public Identifier getUid() {
                return ModJSJadePlugin.id("info_block");
            }

            @Override
            public int getDefaultPriority() {
                return PRIORITY;
            }
        }, Block.class);

        registration.registerEntityComponent(new IEntityComponentProvider() {
            @Override
            public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
                List<InfoLine> lines = new ArrayList<>();
                Set<String> hidden = new LinkedHashSet<>();
                InfoBridge.read(accessor.getServerData(), lines, hidden);
                if (InfoDispatch.active("entity")) {
                    InfoEventJS event = InfoBridge.entityEvent(accessor.getEntity(), "client", accessor.showDetails());
                    InfoDispatch.onClient(event, true);
                    lines.addAll(event.lines());
                    hidden.addAll(event.hiddenTags());
                }
                show(tooltip, lines, hidden);
            }

            @Override
            public Identifier getUid() {
                return ModJSJadePlugin.id("info_entity");
            }

            @Override
            public int getDefaultPriority() {
                return PRIORITY;
            }
        }, Entity.class);
    }

    private static void show(ITooltip tooltip, List<InfoLine> lines, Set<String> hidden) {
        for (String tag : hidden) {
            Identifier id = Identifier.tryParse(tag);
            if (id != null) {
                tooltip.remove(id);
            }
        }
        for (String position : new String[] {"head", "body", "badge", "tail"}) {
            for (InfoLine line : lines) {
                if (line.position().equals(position)) {
                    add(tooltip, line);
                }
            }
        }
    }

    private static void add(ITooltip tooltip, InfoLine line) {
        MutableComponent text = InfoBridge.text(line).copy();
        if (line.hasBar()) {
            // Jade's bar element differs a lot between its versions, so a bar is a row of blocks in the bar's color.
            int filled = Math.round(line.fraction() * BAR_CELLS);
            text.append(Component.literal(" " + "▮".repeat(filled)).withStyle(style -> style.withColor(line.barColor())));
            text.append(Component.literal("▯".repeat(BAR_CELLS - filled)).withStyle(style -> style.withColor(0x666666)));
        }
        ItemStack stack = stackOf(line);
        if (stack.isEmpty()) {
            tooltip.add(text);
        } else {
            tooltip.add(snownee.jade.api.ui.JadeUI.smallItem(stack));
            tooltip.append(Component.literal(" ").append(text));
        }
    }

    private static ItemStack stackOf(InfoLine line) {
        if (line.item().isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier id = Identifier.tryParse(line.item());
        Item item = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, line.count());
    }
}
