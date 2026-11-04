package com.itmecasey.modjs.unify;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.core.registries.BuiltInRegistries;

/** Replaces non-canonical items in every loot drop (blocks, mobs, chests, fishing) with the canonical item. */
public final class UnifyLootModifier extends LootModifier {
    public static final MapCodec<UnifyLootModifier> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(
        instance -> codecStart(instance).apply(instance, UnifyLootModifier::new));

    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "modjs");

    public UnifyLootModifier(LootItemCondition[] conditions, int priority) {
        super(conditions, priority);
    }

    public UnifyLootModifier(LootItemCondition[] conditions) {
        this(conditions, 0);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
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
    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        SERIALIZERS.register("unify", () -> CODEC);
        SERIALIZERS.register(modBus);
    }
}
