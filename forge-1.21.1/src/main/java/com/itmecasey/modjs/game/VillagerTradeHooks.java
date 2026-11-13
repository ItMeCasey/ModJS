package com.itmecasey.modjs.game;

import com.itmecasey.modjs.config.ConfigHandle;
import com.itmecasey.modjs.script.ModJSContextFactory;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import com.itmecasey.modjs.villager.TradeFilter;
import com.itmecasey.modjs.villager.TradeItem;
import com.itmecasey.modjs.villager.TradeOps;
import com.itmecasey.modjs.villager.TradeSpec;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades.ItemListing;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.trading.ItemCost;
import java.util.Optional;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraft.world.entity.npc.VillagerTrades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies {@code Villagers.addTrade} and its relatives on 1.20.1 and 1.21.1, where trades are lists in code: when a world starts, the lists of a
 * profession (and of the wandering trader) are changed in the order scripts asked. A trade that scripts add is an {@link ItemListing} that checks its
 * conditions when the villager rolls it and offers nothing when they do not fit. Changes made by scripts that run again apply from the next world start.
 */
public final class VillagerTradeHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");

    private VillagerTradeHooks() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, VillagerTradesEvent.class, e -> villager(e.getType(), e.getTrades()));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, WandererTradesEvent.class, e -> wanderer(e.getGenericTrades(), e.getRareTrades()));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ServerAboutToStartEvent.class, e -> experimental());
    }

    /** The trades of the experimental trade rebalance (1.21.1): the game keeps them in their own lists, which no loader event reaches. */
    private static final Map<VillagerProfession, Map<Integer, ItemListing[]>> EXPERIMENTAL_ORIGINAL = new java.util.HashMap<>();

    private static void experimental() {
        if (!TradeOps.any()) {
            return;
        }
        try {
            for (VillagerProfession profession : new ArrayList<>(VillagerTrades.EXPERIMENTAL_TRADES.keySet())) {
                Int2ObjectMap<ItemListing[]> live = VillagerTrades.EXPERIMENTAL_TRADES.get(profession);
                Map<Integer, ItemListing[]> original = EXPERIMENTAL_ORIGINAL.computeIfAbsent(profession, p -> {
                    Map<Integer, ItemListing[]> copy = new java.util.HashMap<>();
                    for (int level = 1; level <= 5; level++) {
                        ItemListing[] listings = live.get(level);
                        copy.put(level, listings == null ? new ItemListing[0] : listings.clone());
                    }
                    return copy;
                });
                Int2ObjectMap<List<ItemListing>> lists = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
                for (int level = 1; level <= 5; level++) {
                    lists.put(level, new ArrayList<>(java.util.Arrays.asList(original.get(level))));
                }
                villager(profession, lists);
                for (int level = 1; level <= 5; level++) {
                    live.put(level, lists.get(level).toArray(new ItemListing[0]));
                }
            }
        } catch (UnsupportedOperationException e) {
            LOGGER.warn("[villagers] the lists of the experimental trade rebalance cannot be changed on this version");
        }
    }


    // -- the lists -----------------------------------------------------------------------------------------------------------------

    private static void villager(VillagerProfession profession, Int2ObjectMap<List<ItemListing>> trades) {
        var key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        if (key == null || !TradeOps.any()) {
            return;
        }
        Map<Integer, List<ItemListing>> levels = new LinkedHashMap<>();
        for (int level = 1; level <= 5; level++) {
            levels.put(level, trades.computeIfAbsent(level, k -> new ArrayList<>()));
        }
        apply(key.toString(), levels);
    }

    private static void wanderer(List<ItemListing> generic, List<ItemListing> rare) {
        if (!TradeOps.any()) {
            return;
        }
        Map<Integer, List<ItemListing>> levels = new LinkedHashMap<>();
        levels.put(1, generic);
        levels.put(2, rare);
        apply(TradeOps.WANDERING_TRADER, levels);
    }

    private static void apply(String profession, Map<Integer, List<ItemListing>> levels) {
        for (TradeOps.Op op : TradeOps.snapshot()) {
            if (!op.profession().equals(profession)) {
                continue;
            }
            if (op.level() > levels.size() && op.level() != 0) {
                LOGGER.warn("[villagers] {} has no level {} on this version; the change is ignored", profession, op.level());
                continue;
            }
            int from = op.level() == 0 ? 1 : op.level();
            int to = op.level() == 0 ? levels.size() : op.level();
            for (int index = from; index <= to; index++) {
                final int level = index;
                List<ItemListing> list = levels.get(level);
                switch (op.kind()) {
                    case CLEAR -> list.clear();
                    case REPLACE -> {
                        list.clear();
                        op.trades().forEach(spec -> list.add(new ScriptListing(spec, profession, level)));
                    }
                    case ADD -> op.trades().forEach(spec -> list.add(new ScriptListing(spec, profession, level)));
                    case REMOVE -> list.removeIf(listing -> matches(op.filter(), listing, profession, level));
                }
            }
        }
    }

    /** Whether the filter matches a listing, judged from the offer it makes with no villager. A listing that needs a villager is kept. */
    private static boolean matches(TradeFilter filter, ItemListing listing, String profession, int level) {
        if (filter.isUnconditional()) {
            return true;
        }
        Map<String, Object> view = listing instanceof ScriptListing own ? own.spec.view(profession, level, "") : probe(listing, profession, level);
        return view != null && filter.matches(view);
    }

    private static Map<String, Object> probe(ItemListing listing, String profession, int level) {
        MerchantOffer offer;
        try {
            offer = listing.getOffer(null, RandomSource.create(1L));
        } catch (RuntimeException e) {
            return null;
        }
        if (offer == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profession", profession);
        out.put("level", level);
        out.put("id", "");
        out.put("give", item(offer.getResult()));
        List<Object> wants = new ArrayList<>();
        wants.add(item(offer.getBaseCostA()));
        if (!offer.getCostB().isEmpty()) {
            wants.add(item(offer.getCostB()));
        }
        out.put("want", wants);
        out.put("maxUses", offer.getMaxUses());
        out.put("xp", offer.getXp());
        return out;
    }

    private static Map<String, Object> item(ItemStack stack) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        out.put("count", stack.getCount());
        return out;
    }

    // -- a trade from a script ----------------------------------------------------------------------------------------------------

    /** A trade scripts added: made into an offer when a villager rolls it, if its conditions fit that villager. */
    private static final class ScriptListing implements ItemListing {
        final TradeSpec spec;
        private final String profession;
        private final int level;

        ScriptListing(TradeSpec spec, String profession, int level) {
            this.spec = spec;
            this.profession = profession;
            this.level = level;
        }

        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            if (trader == null || !fits(trader)) {
                return null;
            }
            return offer(spec, trader);
        }

        private boolean fits(Entity trader) {
            if (!spec.villagerTypes.isEmpty()) {
                if (!(trader instanceof Villager villager)) {
                    return false;
                }
                var type = BuiltInRegistries.VILLAGER_TYPE.getKey(villager.getVillagerData().getType());
                if (type == null || !spec.villagerTypes.contains(type.toString())) {
                    return false;
                }
            }
            if (!spec.biomes.isEmpty()) {
                var biome = trader.level().getBiome(trader.blockPosition()).unwrapKey();
                if (biome.isEmpty() || !spec.biomes.contains(biome.get().location().toString())) {
                    return false;
                }
            }
            if (spec.predicate != null) {
                Scriptable scope = spec.predicate.getParentScope();
                try {
                    return Boolean.TRUE.equals(ModJSContextFactory.INSTANCE.call(cx -> Context.toBoolean(spec.predicate.call(cx, scope, scope,
                        new Object[] {ConfigHandle.plain(spec.view(profession, level, ""), scope), Context.javaToJS(trader, scope)}))));
                } catch (RhinoException e) {
                    ScriptManager.of(ScriptType.SERVER).reportError("Villagers trade 'when'", e);
                    return false;
                }
            }
            return true;
        }
    }

    private static MerchantOffer offer(TradeSpec spec, Entity trader) {
        ItemStack give = stack(spec.give, trader);
        ItemCost costA = cost(spec.want);
        ItemCost costB = spec.want2 == null ? null : cost(spec.want2);
        if (give.isEmpty() || costA == null || (spec.want2 != null && costB == null)) {
            return null;
        }
        return new MerchantOffer(costA, Optional.ofNullable(costB), give, 0, spec.maxUses, spec.xp, spec.priceMultiplier, spec.demand);
    }

    private static ItemCost cost(TradeItem item) {
        ResourceLocation id = ResourceLocation.tryParse(item.id());
        var found = id == null ? Optional.<net.minecraft.world.item.Item>empty() : BuiltInRegistries.ITEM.getOptional(id);
        if (found.isEmpty()) {
            LOGGER.warn("[villagers] a trade uses the item {}, which does not exist; the trade is not offered", item.id());
            return null;
        }
        return new ItemCost(found.get(), item.count());
    }

    private static ItemStack stack(TradeItem item, Entity trader) {
        JsonObject json = new JsonObject();
        json.addProperty("id", item.id());
        json.addProperty("count", item.count());
        if (item.components() != null) {
            json.add("components", item.components());
        }
        if (item.nbt() != null) {
            LOGGER.warn("[villagers] 'nbt' on {} is a 1.20.1 option; use 'components' on 1.21.1", item.id());
        }
        var parsed = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, trader.level().registryAccess()), json);
        if (parsed.result().isEmpty()) {
            LOGGER.warn("[villagers] the result {} is not valid: {}", item.id(), parsed.error().map(e -> e.message()).orElse("unknown"));
            return ItemStack.EMPTY;
        }
        return parsed.result().get();
    }
}
