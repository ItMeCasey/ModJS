package com.itmecasey.modjs.worldcraft;

import com.itmecasey.modjs.worldcraft.WorldHandles.ItemEntityHandle;
import com.itmecasey.modjs.worldcraft.WorldHandles.ItemHandle;
import com.itmecasey.modjs.worldcraft.WorldHandles.LevelHandle;
import com.itmecasey.modjs.worldcraft.WorldHandles.PlayerHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Connects the in-world crafting engine ({@link WorldRecipes}) to this Minecraft version: handles over the level, players, stacks and
 * dropped items, and one entry point per game event. The loader-specific {@code WorldCraftHooks} calls these.
 */
public final class WorldCraftAdapters {
    private static final int ITEM_SCAN_INTERVAL = 10;
    private static final int RANDOM_SAMPLES_PER_PLAYER = 100;

    private static final List<String> RECENT = new java.util.concurrent.CopyOnWriteArrayList<>();

    private WorldCraftAdapters() {
    }

    /** The last items the engine spawned as {@code "minecraft:gravel x2"}; the self test reads them (new entities are not always visible in the same tick). */
    public static List<String> recentSpawns() {
        return RECENT;
    }

    // -- text to game objects -----------------------------------------------------------------------------------------------

    private static String base(String spec) {
        int bracket = spec.indexOf('[');
        return bracket < 0 ? spec : spec.substring(0, bracket);
    }

    /** The {@code key=value} pairs of a bracketed block state, or an empty list. */
    private static List<String[]> properties(String spec) {
        List<String[]> out = new ArrayList<>();
        int bracket = spec.indexOf('[');
        int end = spec.lastIndexOf(']');
        if (bracket >= 0 && end > bracket) {
            for (String pair : spec.substring(bracket + 1, end).split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    out.add(new String[] {pair.substring(0, eq).trim(), pair.substring(eq + 1).trim()});
                }
            }
        }
        return out;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    static Item item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? null : BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }

    // -- handles --------------------------------------------------------------------------------------------------------------

    public static final class Stack implements ItemHandle {
        private final ItemStack stack;

        public Stack(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public String id() {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }

        @Override
        public int count() {
            return stack.isEmpty() ? 0 : stack.getCount();
        }

        @Override
        public boolean matches(String matcher) {
            if (stack.isEmpty()) {
                return false;
            }
            if (matcher.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(matcher.substring(1));
                return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
            }
            return id().equals(matcher);
        }

        @Override
        public void shrink(int amount) {
            stack.shrink(amount);
        }

        @Override
        public void damage(int amount) {
            if (stack.isEmpty() || !stack.isDamageableItem()) {
                return;
            }
            int damage = stack.getDamageValue() + amount;
            if (damage >= stack.getMaxDamage()) {
                stack.shrink(1);
            } else {
                stack.setDamageValue(damage);
            }
        }
    }

    public static final class Player implements PlayerHandle {
        private final ServerPlayer player;
        private final boolean forceSurvival;

        public Player(ServerPlayer player) {
            this(player, false);
        }

        /** {@code forceSurvival} treats the player as a survival player whatever their game mode (the self test's fake player). */
        public Player(ServerPlayer player, boolean forceSurvival) {
            this.player = player;
            this.forceSurvival = forceSurvival;
        }

        ServerPlayer player() {
            return player;
        }

        @Override
        public boolean sneaking() {
            return player.isShiftKeyDown();
        }

        @Override
        public boolean creative() {
            return !forceSurvival && player.isCreative();
        }

        @Override
        public ItemHandle held(boolean offHand) {
            return new Stack(player.getItemInHand(offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));
        }

        @Override
        public void give(String itemId, int count) {
            Item item = item(itemId);
            if (item == null) {
                return;
            }
            int left = count;
            while (left > 0) {
                ItemStack stack = new ItemStack(item, Math.min(left, new ItemStack(item).getMaxStackSize()));
                left -= stack.getCount();
                player.getInventory().add(stack);
                if (!stack.isEmpty() && player.level() instanceof ServerLevel serverLevel) {
                    ItemEntity entity = new ItemEntity(serverLevel, player.getX(), player.getY() + 0.5, player.getZ(), stack);
                    entity.setDefaultPickUpDelay();
                    serverLevel.addFreshEntity(entity);
                }
            }
        }
    }

    public static final class Level implements LevelHandle {
        private final ServerLevel level;

        public Level(ServerLevel level) {
            this.level = level;
        }

        @Override
        public boolean blockMatches(int x, int y, int z, String matcher) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            String id = base(matcher);
            if (id.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(id.substring(1));
                if (tag == null || !state.is(TagKey.create(Registries.BLOCK, tag))) {
                    return false;
                }
            } else if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(id)) {
                return false;
            }
            for (String[] pair : properties(matcher)) {
                boolean found = false;
                for (Property<?> property : state.getProperties()) {
                    if (property.getName().equals(pair[0])) {
                        found = valueName(state, property).equals(pair[1]);
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean setBlock(int x, int y, int z, String spec) {
            ResourceLocation id = ResourceLocation.tryParse(base(spec));
            Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block == null) {
                return false;
            }
            BlockState state = block.defaultBlockState();
            for (String[] pair : properties(spec)) {
                for (Property<?> property : state.getProperties()) {
                    if (property.getName().equals(pair[0])) {
                        state = withValue(state, property, pair[1]);
                        break;
                    }
                }
            }
            level.setBlockAndUpdate(new BlockPos(x, y, z), state);
            return true;
        }

        @Override
        public void removeBlock(int x, int y, int z) {
            level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }

        @Override
        public String blockId(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) {
                return null;
            }
            return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        }

        @Override
        public boolean destroyBlock(int x, int y, int z, boolean drop) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos) || level.getBlockState(pos).isAir()) {
                return false;
            }
            return level.destroyBlock(pos, drop);
        }

        @Override
        public boolean bonemeal(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) {
                return false;
            }
            return net.minecraft.world.item.BoneMealItem.applyBonemeal(new ItemStack(net.minecraft.world.item.Items.BONE_MEAL), level, pos, net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level));
        }

        @Override
        public String dimension() {
            return level.dimension().location().toString();
        }

        @Override
        public java.util.Map<String, String> describe(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
            if (level.hasChunkAt(pos)) {
                BlockState state = level.getBlockState(pos);
                for (Property<?> property : state.getProperties()) {
                    out.put(property.getName(), valueName(state, property));
                }
            }
            return out;
        }

        @Override
        public void spawnItem(double x, double y, double z, String itemId, int count) {
            Item item = item(itemId);
            if (item == null) {
                return;
            }
            if (RECENT.size() > 64) {
                RECENT.remove(0);
            }
            RECENT.add(itemId + " x" + count);
            int left = count;
            while (left > 0) {
                ItemStack stack = new ItemStack(item, Math.min(left, new ItemStack(item).getMaxStackSize()));
                left -= stack.getCount();
                ItemEntity entity = new ItemEntity(level, x, y, z, stack);
                entity.setDefaultPickUpDelay();
                level.addFreshEntity(entity);
            }
        }

        @Override
        public void runCommand(int x, int y, int z, String command) {
            var server = level.getServer();
            if (server != null) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withPosition(Vec3.atCenterOf(new BlockPos(x, y, z))), command);
            }
        }

        @Override
        public double random() {
            return level.getRandom().nextDouble();
        }
    }

    public static final class Drop implements ItemEntityHandle {
        private final ItemEntity entity;
        private final Stack stack;

        public Drop(ItemEntity entity) {
            this.entity = entity;
            this.stack = new Stack(entity.getItem());
        }

        @Override
        public ItemHandle stack() {
            return stack;
        }

        @Override
        public double x() {
            return entity.getX();
        }

        @Override
        public double y() {
            return entity.getY();
        }

        @Override
        public double z() {
            return entity.getZ();
        }

        @Override
        public void changed() {
            if (entity.getItem().isEmpty()) {
                entity.discard();
            }
        }
    }

    // -- game events ----------------------------------------------------------------------------------------------------------

    /** A player clicked a block on the server. Returns true when an in-world recipe happened and the click should be cancelled. */
    public static boolean click(ServerPlayer player, BlockPos pos, boolean left, InteractionHand hand) {
        return click(new Player(player), pos, left, hand);
    }

    public static boolean click(Player player, BlockPos pos, boolean left, InteractionHand hand) {
        if (WorldRecipes.all().isEmpty() || !(player.player().level() instanceof ServerLevel level)) {
            return false;
        }
        return WorldRecipes.click(new Level(level), player, pos.getX(), pos.getY(), pos.getZ(), left, hand == InteractionHand.OFF_HAND);
    }

    /** A player is breaking a block. Returns true when the normal break must be cancelled. */
    public static boolean breakBlock(ServerPlayer player, BlockPos pos) {
        return breakBlock(new Player(player), pos);
    }

    public static boolean breakBlock(Player player, BlockPos pos) {
        if (WorldRecipes.all().isEmpty() || !(player.player().level() instanceof ServerLevel level)) {
            return false;
        }
        return WorldRecipes.breakBlock(new Level(level), player, pos.getX(), pos.getY(), pos.getZ());
    }

    /** An entity is leaving the level: an anvil that landed crushes the block under it. */
    public static void entityLeft(Entity entity) {
        if (!(entity instanceof FallingBlockEntity falling) || !(entity.level() instanceof ServerLevel level) || WorldRecipes.all().isEmpty()
            || !WorldRecipes.any(WorldRecipe.Trigger.ANVIL) || !falling.getBlockState().is(BlockTags.ANVIL)
            || entity.getRemovalReason() != Entity.RemovalReason.DISCARDED || !entity.onGround()) {
            return;
        }
        crush(level, entity.blockPosition().below());
    }

    /** An entity joined the level: lightning runs the lightning recipes at its position. */
    public static void entityJoined(Entity entity) {
        if (!(entity instanceof LightningBolt bolt) || !(entity.level() instanceof ServerLevel level) || !WorldRecipes.any(WorldRecipe.Trigger.LIGHTNING)) {
            return;
        }
        strike(level, bolt.blockPosition());
    }

    /** Runs the items-in-block and items-on-block recipes on the dropped items of a level right now. */
    public static void scanItems(ServerLevel level) {
        List<ItemEntityHandle> items = new ArrayList<>();
        for (ItemEntity item : level.getEntities(EntityTypeTest.forClass(ItemEntity.class), entity -> !entity.isRemoved() && !entity.getItem().isEmpty())) {
            items.add(new Drop(item));
        }
        WorldRecipes.items(new Level(level), items);
    }

    /** An anvil landed on the block at this position. */
    public static void crush(ServerLevel level, BlockPos pos) {
        WorldRecipes.anvil(new Level(level), pos.getX(), pos.getY(), pos.getZ());
    }

    /** Lightning struck at this position. */
    public static void strike(ServerLevel level, BlockPos pos) {
        List<ItemEntityHandle> nearby = new ArrayList<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(3.0))) {
            nearby.add(new Drop(item));
        }
        WorldRecipes.lightning(new Level(level), pos.getX(), pos.getY(), pos.getZ(), nearby);
    }

    /** End of a server level tick: scans dropped items every few ticks and samples blocks for random-tick recipes. */
    public static void levelTick(ServerLevel level) {
        if (WorldRecipes.all().isEmpty()) {
            return;
        }
        if (WorldRecipes.needsItemScan() && level.getGameTime() % ITEM_SCAN_INTERVAL == 0) {
            scanItems(level);
        }
        if (WorldRecipes.any(WorldRecipe.Trigger.RANDOM_TICK)) {
            Level handle = new Level(level);
            for (ServerPlayer player : level.players()) {
                for (int i = 0; i < RANDOM_SAMPLES_PER_PLAYER; i++) {
                    int x = player.getBlockX() + level.getRandom().nextInt(65) - 32;
                    int y = player.getBlockY() + level.getRandom().nextInt(33) - 16;
                    int z = player.getBlockZ() + level.getRandom().nextInt(65) - 32;
                    WorldRecipes.randomTick(handle, x, y, z);
                }
            }
        }
    }
}
