package com.itmecasey.modjs.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;

/** Storage blocks made with {@code BlockBuilder.container(rows)}: a chest-like block entity and its block. */
public final class ContainerBlocks {
    private static BlockEntityType<ContainerEntity> type;

    private ContainerBlocks() {
    }

    static void setType(BlockEntityType<ContainerEntity> value) {
        type = value;
    }

    static BlockEntityType<ContainerEntity> type() {
        return type;
    }

    private static MenuType<ChestMenu> menuFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    /** The block: opens its menu on right-click, drops its contents when broken. */
    public static final class ContainerBlock extends BaseEntityBlock {
        private final int rows;

        ContainerBlock(Properties properties, int rows) {
            super(properties);
            this.rows = rows;
        }

        int rows() {
            return rows;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new ContainerEntity(pos, state, rows);
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (level.getBlockEntity(pos) instanceof MenuProvider provider) {
                NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, provider, pos);
            }
            return InteractionResult.CONSUME;
        }

        @Override
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof Container container) {
                Containers.dropContents(level, pos, container);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    /** The block entity: a row-sized item list with a chest menu. */
    public static final class ContainerEntity extends RandomizableContainerBlockEntity {
        private final int rows;
        private NonNullList<ItemStack> items;

        public ContainerEntity(BlockPos pos, BlockState state) {
            this(pos, state, state.getBlock() instanceof ContainerBlock block ? block.rows() : 3);
        }

        ContainerEntity(BlockPos pos, BlockState state, int rows) {
            super(type, pos, state);
            this.rows = rows;
            this.items = NonNullList.withSize(rows * 9, ItemStack.EMPTY);
        }

        @Override
        protected Component getDefaultName() {
            return Component.translatable(getBlockState().getBlock().getDescriptionId());
        }

        @Override
        protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
            return new ChestMenu(menuFor(rows), id, inventory, this, rows);
        }

        @Override
        public int getContainerSize() {
            return rows * 9;
        }

        @Override
        protected NonNullList<ItemStack> getItems() {
            return items;
        }

        @Override
        protected void setItems(NonNullList<ItemStack> newItems) {
            this.items = newItems;
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
            if (!tryLoadLootTable(tag)) {
                ContainerHelper.loadAllItems(tag, items);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            if (!trySaveLootTable(tag)) {
                ContainerHelper.saveAllItems(tag, items);
            }
        }
    }
}
