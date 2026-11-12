package com.itmecasey.modjs.registry;

import com.itmecasey.modjs.block.ScriptBlockData;
import com.itmecasey.modjs.block.ScriptBlockEntityJS;
import com.itmecasey.modjs.block.ScriptBlockLogic;
import com.itmecasey.modjs.client.TitleScreenConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Blocks made with {@code event.create(...).inventory({...})}, {@code entity()}, {@code onTick}, {@code screen} or
 * {@code model('orientable')}: the block, its block entity (slots, data map, synced values), and the menu behind its screen.
 */
public final class ScriptBlocks {
    private static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final ThreadLocal<BlockBuilderJS> PENDING = new ThreadLocal<>();
    private static BlockEntityType<ScriptEntity> type;
    private static final Map<String, MenuType<ScriptMenu>> MENUS = new HashMap<>();

    private ScriptBlocks() {
    }

    static void setType(BlockEntityType<ScriptEntity> value) {
        type = value;
    }

    static BlockEntityType<ScriptEntity> type() {
        return type;
    }

    static void putMenu(String blockPath, MenuType<ScriptMenu> menu) {
        MENUS.put(blockPath, menu);
    }

    /** The menu type of a scripted block, or null when it has no slots. */
    public static MenuType<ScriptMenu> menu(String blockPath) {
        return MENUS.get(blockPath);
    }

    /** Menu factory used for the client side of every scripted block's menu type. */
    static ScriptMenu clientMenu(BlockBuilderJS def, int id, Inventory inventory) {
        return new ScriptMenu(MENUS.get(def.fullId()), id, inventory, new SimpleContainer(def.slotCount()), new SimpleContainerData(Math.max(1, def.valueNames().size())), def, null);
    }

    static ScriptBlock createBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties, BlockBuilderJS def) {
        PENDING.set(def);
        try {
            return new ScriptBlock(properties, def);
        } finally {
            PENDING.remove();
        }
    }

    /** The block. */
    public static final class ScriptBlock extends BaseEntityBlock {
        private final BlockBuilderJS def;

        ScriptBlock(Properties properties, BlockBuilderJS def) {
            super(properties);
            this.def = def;
            if (def != null && def.isOrientable()) {
                registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
            }
        }

        BlockBuilderJS definition() {
            return def;
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(properties -> new ScriptBlock(properties, null));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
            BlockBuilderJS pending = PENDING.get();
            if (pending != null && pending.isOrientable()) {
                builder.add(FACING);
            }
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockState state = defaultBlockState();
            return def != null && def.isOrientable() ? state.setValue(FACING, context.getHorizontalDirection().getOpposite()) : state;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return def != null && def.hasEntity() ? new ScriptEntity(pos, state) : null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> entityType) {
            if (level.isClientSide || def == null || def.tickFunction() == null || entityType != type) {
                return null;
            }
            return (l, p, s, entity) -> ((ScriptEntity) entity).serverTick();
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (def == null || !def.hasScriptInventory()) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (level.getBlockEntity(pos) instanceof MenuProvider provider) {
                player.openMenu(provider);
            }
            return InteractionResult.CONSUME;
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ScriptEntity entity) {
                Containers.dropContents(level, pos, entity);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    /** The block entity: slots, a synced data map and synced values. */
    public static final class ScriptEntity extends BlockEntity implements Container, MenuProvider, ScriptBlockEntityJS {
        private final BlockBuilderJS def;
        private final NonNullList<ItemStack> items;
        private final int[] values;
        private ScriptBlockData data = new ScriptBlockData();
        private boolean needsSync;

        public ScriptEntity(BlockPos pos, BlockState state) {
            super(type, pos, state);
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            this.def = ScriptBlockLogic.definition(path);
            this.items = NonNullList.withSize(def == null ? 0 : def.slotCount(), ItemStack.EMPTY);
            this.values = new int[def == null ? 0 : def.valueNames().size()];
        }

        BlockBuilderJS definition() {
            return def;
        }

        public void serverTick() {
            if (level == null || level.isClientSide || def == null) {
                return;
            }
            ScriptBlockLogic.tick(def, this, level.getGameTime());
            flushSync();
        }

        private void flushSync() {
            if (needsSync && level != null && !level.isClientSide) {
                needsSync = false;
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        // -- saving and syncing ---------------------------------------------------------------------------------------

        /** The data map plus the named values, as the JSON clients see. */
        private String syncedJson() {
            ScriptBlockData merged = ScriptBlockData.fromJson(data.toJson());
            if (def != null) {
                for (int i = 0; i < values.length; i++) {
                    merged.set(def.valueNames().get(i), values[i]);
                }
            }
            return merged.toJson();
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            ContainerHelper.saveAllItems(tag, items, registries);
            tag.putString("ModJSData", data.toJson());
            tag.putIntArray("ModJSValues", values);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            if (tag.contains("Items")) {
                for (int i = 0; i < items.size(); i++) {
                    items.set(i, ItemStack.EMPTY);
                }
                ContainerHelper.loadAllItems(tag, items, registries);
            }
            if (tag.contains("ModJSData")) {
                data = ScriptBlockData.fromJson(tag.getString("ModJSData"));
            }
            int[] saved = tag.getIntArray("ModJSValues");
            System.arraycopy(saved, 0, values, 0, Math.min(saved.length, values.length));
            if (level != null && level.isClientSide) {
                clientUpdated();
            }
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("ModJSData", data.toJson());
            tag.putIntArray("ModJSValues", values);
            return tag;
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        private void clientUpdated() {
            com.itmecasey.modjs.client.overlay.WorldOverlay.blockUpdated(
                BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).toString(),
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), syncedJson());
        }

        @Override
        public void onLoad() {
            super.onLoad();
            if (level != null && level.isClientSide) {
                clientUpdated();
            }
        }

        @Override
        public void setRemoved() {
            super.setRemoved();
            if (level != null && level.isClientSide) {
                com.itmecasey.modjs.client.overlay.WorldOverlay.blockRemoved(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
            }
        }

        // -- Container ------------------------------------------------------------------------------------------------

        @Override
        public int getContainerSize() {
            return items.size();
        }

        @Override
        public boolean isEmpty() {
            return items.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
            if (!removed.isEmpty()) {
                setChanged();
            }
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(items, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            items.set(slot, stack);
            if (stack.getCount() > getMaxStackSize()) {
                stack.setCount(getMaxStackSize());
            }
            setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return def == null ? 64 : def.stackLimit();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(this, player);
        }

        @Override
        public void clearContent() {
            items.clear();
            setChanged();
        }

        // -- menu -----------------------------------------------------------------------------------------------------

        @Override
        public Component getDisplayName() {
            return Component.translatable(getBlockState().getBlock().getDescriptionId());
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            MenuType<ScriptMenu> menuType = def == null ? null : MENUS.get(def.fullId());
            if (menuType == null) {
                return null;
            }
            ContainerData syncedValues = new ContainerData() {
                @Override
                public int get(int index) {
                    return index < values.length ? values[index] : 0;
                }

                @Override
                public void set(int index, int value) {
                    if (index < values.length) {
                        values[index] = value;
                    }
                }

                @Override
                public int getCount() {
                    return Math.max(1, values.length);
                }
            };
            return new ScriptMenu(menuType, id, inventory, this, syncedValues, def, this);
        }

        // -- ScriptBlockEntityJS --------------------------------------------------------------------------------------

        @Override
        public int getX() {
            return worldPosition.getX();
        }

        @Override
        public int getY() {
            return worldPosition.getY();
        }

        @Override
        public int getZ() {
            return worldPosition.getZ();
        }

        @Override
        public String getBlockId() {
            return BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).toString();
        }

        @Override
        public int getSize() {
            return items.size();
        }

        private int checkSlot(int slot) {
            if (slot < 0 || slot >= items.size()) {
                throw new IllegalArgumentException("Slot " + slot + " is out of range 0-" + (items.size() - 1));
            }
            return slot;
        }

        @Override
        public String getItemId(int slot) {
            return BuiltInRegistries.ITEM.getKey(items.get(checkSlot(slot)).getItem()).toString();
        }

        @Override
        public int getCount(int slot) {
            return items.get(checkSlot(slot)).getCount();
        }

        private static Item item(String itemId) {
            ResourceLocation id = ResourceLocation.tryParse(itemId.contains(":") ? itemId : "minecraft:" + itemId);
            Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) {
                throw new IllegalArgumentException("Unknown item '" + itemId + "'");
            }
            return item;
        }

        @Override
        public void setItem(int slot, String itemId, int count) {
            setItem(checkSlot(slot), new ItemStack(item(itemId), Math.max(0, count)));
        }

        @Override
        public void clearSlot(int slot) {
            setItem(checkSlot(slot), ItemStack.EMPTY);
        }

        @Override
        public int shrink(int slot, int amount) {
            ItemStack stack = items.get(checkSlot(slot));
            int removed = Math.min(Math.max(0, amount), stack.getCount());
            if (removed > 0) {
                stack.shrink(removed);
                if (stack.isEmpty()) {
                    items.set(slot, ItemStack.EMPTY);
                }
                setChanged();
            }
            return removed;
        }

        @Override
        public int insert(String itemId, int count) {
            Item item = item(itemId);
            int left = Math.max(0, count);
            for (int slot = 0; slot < items.size() && left > 0; slot++) {
                ItemStack stack = items.get(slot);
                int room = Math.min(getMaxStackSize(), new ItemStack(item).getMaxStackSize());
                if (stack.isEmpty()) {
                    int put = Math.min(room, left);
                    items.set(slot, new ItemStack(item, put));
                    left -= put;
                } else if (stack.is(item) && stack.getCount() < room) {
                    int put = Math.min(room - stack.getCount(), left);
                    stack.grow(put);
                    left -= put;
                }
            }
            if (left != count) {
                setChanged();
            }
            return left;
        }

        @Override
        public Object getData(String key) {
            return data.get(key);
        }

        @Override
        public void setData(String key, Object value) {
            if (data.set(key, value)) {
                setChanged();
                needsSync = true;
                if (level != null && level.isClientSide) {
                    clientUpdated();
                }
            }
        }

        private int valueIndex(String name) {
            int index = def == null ? -1 : def.valueNames().indexOf(name);
            if (index < 0) {
                throw new IllegalArgumentException("Unknown value '" + name + "'; declare it with inventory({ values: ['" + name + "'] })");
            }
            return index;
        }

        @Override
        public int getValue(String name) {
            return values[valueIndex(name)];
        }

        @Override
        public void setValue(String name, int value) {
            int index = valueIndex(name);
            if (values[index] != value) {
                values[index] = value;
                setChanged();
                needsSync = true;
            }
        }

        @Override
        public void markChanged() {
            setChanged();
        }

        @Override
        public void sync() {
            needsSync = true;
            flushSync();
        }

        @Override
        public boolean isClientSide() {
            return level != null && level.isClientSide;
        }
    }

    /** The menu: the block's slots at the script's positions, then the player inventory. */
    public static final class ScriptMenu extends AbstractContainerMenu {
        private final BlockBuilderJS def;
        private final Container container;
        private final ScriptEntity entity;
        private final ContainerData values;
        private final int blockSlots;

        ScriptMenu(MenuType<ScriptMenu> menuType, int id, Inventory inventory, Container container, ContainerData values, BlockBuilderJS def, ScriptEntity entity) {
            super(menuType, id);
            this.def = def;
            this.container = container;
            this.entity = entity;
            this.values = values;
            this.blockSlots = def.slotCount();
            container.startOpen(inventory.player);
            for (int i = 0; i < blockSlots; i++) {
                int[] position = def.slotPositions().get(i);
                addSlot(new Slot(container, i, position[0], position[1]));
            }
            TitleScreenConfig screen = def.screenConfig();
            int left = (screen.panelWidth() - 176) / 2 + 8;
            int top = screen.panelHeight() - 82;
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    addSlot(new Slot(inventory, column + row * 9 + 9, left + column * 18, top + row * 18));
                }
            }
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column, left + column * 18, top + 58));
            }
            addDataSlots(values);
        }

        public BlockBuilderJS definition() {
            return def;
        }

        /** The current value of a synced number as the open screen sees it (0 for an unknown name). */
        public int value(String name) {
            int index = def.valueNames().indexOf(name);
            return index < 0 ? 0 : values.get(index);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            ItemStack moved = ItemStack.EMPTY;
            Slot slot = slots.get(index);
            if (slot != null && slot.hasItem()) {
                ItemStack stack = slot.getItem();
                moved = stack.copy();
                if (index < blockSlots) {
                    if (!moveItemStackTo(stack, blockSlots, slots.size(), true)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(stack, 0, blockSlots, false)) {
                    return ItemStack.EMPTY;
                }
                if (stack.isEmpty()) {
                    slot.setByPlayer(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
            }
            return moved;
        }

        @Override
        public boolean stillValid(Player player) {
            return container.stillValid(player);
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            container.stopOpen(player);
        }

        /** A screen button was pressed: the id comes from the button's position in the screen definition. */
        @Override
        public boolean clickMenuButton(Player player, int buttonIndex) {
            java.util.List<TitleScreenConfig.Button> buttons = def.screenConfig().buttons();
            if (entity == null || buttonIndex < 0 || buttonIndex >= buttons.size() || buttons.get(buttonIndex).id() == null) {
                return false;
            }
            if (player instanceof ServerPlayer) {
                ScriptBlockLogic.button(def, buttons.get(buttonIndex).id(), entity, player);
                entity.setChanged();
                entity.flushSync();
            }
            return true;
        }
    }
}
