package com.itmecasey.modjs.game;

import com.itmecasey.modjs.item.ItemModifications;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * Applies {@code ItemEvents.modification} on versions without data components, by rewriting the item's own fields. Rarity is
 * not supported here: in this version it comes from code, not data.
 */
public final class ItemModifier {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");

    private ItemModifier() {
    }

    public static void apply() {
        try {
            Field stack = ObfuscationReflectionHelper.findField(Item.class, "maxStackSize");
            Field damage = ObfuscationReflectionHelper.findField(Item.class, "maxDamage");
            stack.setAccessible(true);
            damage.setAccessible(true);
            for (String id : ItemModifications.ids()) {
                ResourceLocation location = ResourceLocation.tryParse(id);
                Item item = location == null ? null : BuiltInRegistries.ITEM.getOptional(location).orElse(null);
                ItemModifications.Changes changes = ItemModifications.forItem(id);
                if (item == null || changes == null) {
                    LOGGER.warn("ItemEvents.modification: unknown item '{}'", id);
                    continue;
                }
                if (changes.getMaxStackSize() != null) {
                    stack.setInt(item, changes.getMaxStackSize());
                }
                if (changes.getMaxDamage() != null) {
                    damage.setInt(item, changes.getMaxDamage());
                }
                if (changes.getRarity() != null) {
                    LOGGER.warn("ItemEvents.modification: rarity cannot be changed on this Minecraft version ({})", id);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("ItemEvents.modification could not be applied on this version: {}", e.toString());
        }
    }
}
