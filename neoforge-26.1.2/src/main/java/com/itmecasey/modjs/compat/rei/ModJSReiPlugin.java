package com.itmecasey.modjs.compat.rei;

import com.itmecasey.modjs.compat.viewer.ViewerItems;
import dev.architectury.event.EventResult;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.forge.REIPluginClient;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** REI discovers this adapter only when REI is installed. */
@REIPluginClient
public final class ModJSReiPlugin implements REIClientPlugin {
    @Override
    public void registerEntries(EntryRegistry registry) {
        var hidden = ViewerItems.hiddenItems();
        registry.removeEntryIf(entry -> entry.getType().equals(VanillaEntryTypes.ITEM)
            && hidden.contains(((ItemStack) entry.getValue()).getItem()));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        ViewerItems.information().forEach((selector, lines) -> ViewerItems.resolve(selector).forEach(item ->
            registry.add(DefaultInformationDisplay.createFromEntry(EntryStacks.of(item), Component.literal("Information"))
                .lines(lines.stream().<Component>map(Component::literal).toList()))));
    }

    @Override
    public void registerCategories(CategoryRegistry registry) {
        var hidden = ViewerItems.hiddenCategories();
        registry.registerVisibilityPredicate(category -> {
            String id = category.getCategoryIdentifier().getIdentifier().toString();
            String vanillaId = id.replace("minecraft:plugins/", "minecraft:");
            return hidden.contains(id) || hidden.contains(vanillaId) ? EventResult.interruptFalse() : EventResult.pass();
        });
    }
}
