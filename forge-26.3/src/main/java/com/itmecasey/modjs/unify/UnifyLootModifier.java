package com.itmecasey.modjs.unify;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Replaces non-canonical items in every loot drop (blocks, mobs, chests, fishing) with the canonical item. */
public final class UnifyLootModifier extends LootModifier {
    public static final MapCodec<UnifyLootModifier> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(
        instance -> codecStart(instance).apply(instance, UnifyLootModifier::new));

    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
        DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "modjs");

    public UnifyLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(net.minecraft.world.level.storage.loot.LootTable table, ObjectArrayList<ItemStack> loot, LootContext context) {
        Unifier unifier = Unifier.current();
        if (!unifier.active()) {
            return loot;
        }
        for (int i = 0; i < loot.size(); i++) {
            ItemStack stack = loot.get(i);
            String canonical = unifier.canonicalFor(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (canonical != null) {
                Identifier id = Identifier.tryParse(canonical);
                Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item != null) {
                    loot.set(i, new ItemStack(item, stack.getCount()));
                }
            }
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    /** Registers the serializer; call from the mod constructor. */
    public static void register(net.minecraftforge.eventbus.api.bus.BusGroup modBus) {
        SERIALIZERS.register("unify", () -> CODEC);
        SERIALIZERS.register(modBus);
    }
}
