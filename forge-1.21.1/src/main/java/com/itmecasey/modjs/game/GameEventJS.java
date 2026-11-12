package com.itmecasey.modjs.game;

import com.itmecasey.modjs.event.EventJS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The event object scripts receive for gameplay events. One class with optional parts keeps the script API small:
 * each event fills in what applies ({@code player}, {@code block}, {@code item}, {@code entity}, {@code level}).
 */
public final class GameEventJS extends EventJS {
    private Player player;
    private Level level;
    private BlockPos pos;
    private BlockState state;
    private ItemStack item = ItemStack.EMPTY;
    private Entity entity;
    private MinecraftServer server;
    private String message;
    private float amount;
    private boolean amountChanged;
    private String hand;
    private String face;
    private String damageType;
    private int slot = -1;

    // -- builders used by the Forge bridge -----------------------------------------------------------------------

    GameEventJS player(Player value) {
        this.player = value;
        return this;
    }

    GameEventJS level(Level value) {
        this.level = value;
        return this;
    }

    GameEventJS block(BlockPos blockPos, BlockState blockState) {
        this.pos = blockPos;
        this.state = blockState;
        return this;
    }

    GameEventJS slot(int value) {
        this.slot = value;
        return this;
    }

    /** The inventory slot for inventory events, or -1. */
    public int getSlot() {
        return slot;
    }

    GameEventJS item(ItemStack stack) {
        this.item = stack == null ? ItemStack.EMPTY : stack;
        return this;
    }

    GameEventJS entity(Entity value) {
        this.entity = value;
        return this;
    }

    GameEventJS server(MinecraftServer value) {
        this.server = value;
        return this;
    }

    GameEventJS message(String value) {
        this.message = value;
        return this;
    }

    GameEventJS amount(float value) {
        this.amount = value;
        return this;
    }

    GameEventJS hand(String value) {
        this.hand = value;
        return this;
    }

    GameEventJS face(String value) {
        this.face = value;
        return this;
    }

    GameEventJS damageType(String value) {
        this.damageType = value;
        return this;
    }

    boolean amountChanged() {
        return amountChanged;
    }

    float finalAmount() {
        return amount;
    }

    String finalMessage() {
        return message;
    }

    // -- script API ----------------------------------------------------------------------------------------------

    public Player getPlayer() {
        return player;
    }

    public Level getLevel() {
        return level != null ? level : (player != null ? player.level() : entity != null ? entity.level() : null);
    }

    /** The dimension of the event, such as {@code minecraft:overworld}, or null. */
    public String getDimension() {
        net.minecraft.world.level.Level l = getLevel();
        return l == null ? null : l.dimension().location().toString();
    }

    /** Read and search blocks around the event, flood fill, break blocks with drops and grow plants (see LevelJS); null on the client. */
    public com.itmecasey.modjs.worldcraft.LevelJS getWorld() {
        return getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel
            ? new com.itmecasey.modjs.worldcraft.LevelJS(new com.itmecasey.modjs.worldcraft.WorldCraftAdapters.Level(serverLevel)) : null;
    }

    /** Whether the event's player is crouching right now. */
    public boolean isSneaking() {
        return player != null && player.isShiftKeyDown();
    }

    public MinecraftServer getServer() {
        if (server != null) {
            return server;
        }
        Level l = getLevel();
        return l != null ? l.getServer() : null;
    }

    /** The block entity (chest, furnace...) at the event's block, or null when the block has none. */
    public BlockEntityJS getBlockEntity() {
        return BlockEntityJS.at(level, pos);
    }

    public Entity getEntity() {
        return entity;
    }

    public ItemStack getItem() {
        return item;
    }

    @Override
    public String describe() {
        StringBuilder out = new StringBuilder();
        if (player != null) {
            out.append("player=").append(getPlayerName()).append(' ');
        }
        if (entity != null) {
            out.append("entity=").append(getEntityId()).append(' ');
        }
        if (pos != null) {
            out.append("block=").append(getBlockId()).append(" at ").append(getX()).append(',').append(getY()).append(',').append(getZ()).append(' ');
        }
        String item = getItemId();
        if (item != null && !item.equals("minecraft:air")) {
            out.append("item=").append(item).append(' ');
        }
        return out.toString().trim();
    }

    public String getItemId() {
        return item.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
    }

    public BlockState getBlockState() {
        return state;
    }

    public String getBlockId() {
        return state == null ? null : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    public int getX() {
        return pos != null ? pos.getX() : entity != null ? (int) Math.floor(entity.getX()) : player != null ? (int) Math.floor(player.getX()) : 0;
    }

    public int getY() {
        return pos != null ? pos.getY() : entity != null ? (int) Math.floor(entity.getY()) : player != null ? (int) Math.floor(player.getY()) : 0;
    }

    public int getZ() {
        return pos != null ? pos.getZ() : entity != null ? (int) Math.floor(entity.getZ()) : player != null ? (int) Math.floor(player.getZ()) : 0;
    }

    public BlockPos getPos() {
        return pos;
    }

    public String getEntityId() {
        return entity == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    public String getPlayerName() {
        return player == null ? null : player.getName().getString();
    }

    public String getMessage() {
        return message;
    }

    /** Replaces the chat message (chat events only). */
    public void setMessage(String text) {
        this.message = text;
    }

    public float getAmount() {
        return amount;
    }

    /** Changes the damage dealt (hurt events only). */
    public void setAmount(double value) {
        this.amount = (float) value;
        this.amountChanged = true;
    }

    public String getHand() {
        return hand;
    }

    public String getFace() {
        return face;
    }

    public String getDamageType() {
        return damageType;
    }

    /** Sends a chat line to the player in this event. */
    public void tell(String text) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal(text));
        }
    }

    /** Runs a command as the server console. */
    public void runCommand(String command) {
        MinecraftServer minecraftServer = getServer();
        if (minecraftServer != null) {
            minecraftServer.getCommands().performPrefixedCommand(minecraftServer.createCommandSourceStack(), command);
        }
    }
}
