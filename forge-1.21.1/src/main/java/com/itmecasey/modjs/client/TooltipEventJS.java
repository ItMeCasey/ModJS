package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.recipe.RecipeJson;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The event passed to {@code ItemEvents.tooltip(event => ...)} in client scripts. Handlers run each time a tooltip is
 * built, so {@code event.add('minecraft:apple', 'Crunchy')} appends to every apple's tooltip.
 */
public final class TooltipEventJS extends EventJS {
    private final ItemStack stack;
    private final List<Component> lines;

    public TooltipEventJS(ItemStack stack, List<Component> lines) {
        this.stack = stack;
        this.lines = lines;
    }

    public ItemStack getItem() {
        return stack;
    }

    public String getItemId() {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** True when the hovered item is {@code id}, in {@code #tag}, or when {@code id} is {@code *}. */
    public boolean matches(Object ids) {
        for (Object entry : RecipeJson.asList(ids)) {
            String text = JsonUtil.asString(entry);
            if (text == null) {
                continue;
            }
            if (text.equals("*")) {
                return true;
            }
            if (text.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(text.substring(1));
                if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) {
                    return true;
                }
            } else {
                String full = text.contains(":") ? text : "minecraft:" + text;
                if (getItemId().equals(full)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Appends one or more lines to the tooltip when the hovered item matches {@code ids}. */
    public void add(Object ids, Object text) {
        if (!matches(ids)) {
            return;
        }
        for (Object line : RecipeJson.asList(text)) {
            Object value = JsonUtil.unwrap(line);
            if (value instanceof Component component) {
                lines.add(component);
            } else {
                lines.add(Component.literal(String.valueOf(value)));
            }
        }
    }

    public Item getItemType() {
        return stack.getItem();
    }
}
