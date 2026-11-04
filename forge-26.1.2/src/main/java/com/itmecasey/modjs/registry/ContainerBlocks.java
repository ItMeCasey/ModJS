package com.itmecasey.modjs.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(properties -> new ContainerBlock(properties, 3));
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new ContainerEntity(pos, state, rows);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (level.getBlockEntity(pos) instanceof MenuProvider provider) {
                player.openMenu(provider);
            }
            return InteractionResult.CONSUME;
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
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
            if (!tryLoadLootTable(input)) {
                ContainerHelper.loadAllItems(input, items);
            }
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            if (!trySaveLootTable(output)) {
                ContainerHelper.saveAllItems(output, items);
            }
        }

        @Override
        public void preRemoveSideEffects(BlockPos pos, BlockState state) {
            if (level != null) {
                Containers.dropContents(level, pos, this);
            }
            super.preRemoveSideEffects(pos, state);
        }
    }
}
