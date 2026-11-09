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
 * the item's own final {@code rarity} field in this version, so it is rewritten the same way.
 */
public final class ItemModifier {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");

    private ItemModifier() {
    }

    /** A game field by its official name. A real (not dev) 1.20.1 game only knows Forge's own names, so try those first. */
    static java.lang.reflect.Field fieldByName(Class<?> type, String name, String srgName) {
        try {
            return ObfuscationReflectionHelper.findField(type, srgName);
        } catch (RuntimeException e) {
            return ObfuscationReflectionHelper.findField(type, name);
        }
    }

    public static void apply() {
        try {
            Field stack = fieldByName(Item.class, "maxStackSize", "f_41370_");
            Field damage = fieldByName(Item.class, "maxDamage", "f_41371_");
            Field rarity = fieldByName(Item.class, "rarity", "f_41369_");
            rarity.setAccessible(true);
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
                    rarity.set(item, switch (changes.getRarity().toLowerCase(java.util.Locale.ROOT)) {
                        case "uncommon" -> net.minecraft.world.item.Rarity.UNCOMMON;
                        case "rare" -> net.minecraft.world.item.Rarity.RARE;
                        case "epic" -> net.minecraft.world.item.Rarity.EPIC;
                        default -> net.minecraft.world.item.Rarity.COMMON;
                    });
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("ItemEvents.modification could not be applied on this version: {}", e.toString());
        }
    }
}
