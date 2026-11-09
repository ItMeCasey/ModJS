package com.itmecasey.modjs.game;

import com.itmecasey.modjs.block.BlockModifications;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Applies {@code BlockEvents.modification} by rewriting the block and block state fields the game reads. Run once after
 * registries are built. Values are set on every state of the block, so they apply everywhere that block is used.
 */
public final class BlockModifier {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");

    private static final java.util.Map<String, String> SRG = java.util.Map.of("explosionResistance", "f_60444_", "friction", "f_60447_", "speedFactor", "f_60448_", "jumpFactor", "f_60449_", "destroySpeed", "f_60599_", "lightEmission", "f_60594_", "requiresCorrectToolForDrops", "f_60600_");

    private BlockModifier() {
    }

    private static Field field(Class<?> type, String name) {
        Field field = ItemModifier.fieldByName(type, name, SRG.getOrDefault(name, name));
        field.setAccessible(true);
        return field;
    }

    public static void apply() {
        Map<String, BlockModifications.Changes> all = BlockModifications.all();
        if (all.isEmpty()) {
            return;
        }
        try {
            Field resistance = field(BlockBehaviour.class, "explosionResistance");
            Field friction = field(BlockBehaviour.class, "friction");
            Field speed = field(BlockBehaviour.class, "speedFactor");
            Field jump = field(BlockBehaviour.class, "jumpFactor");
            Field destroy = field(BlockBehaviour.BlockStateBase.class, "destroySpeed");
            Field light = field(BlockBehaviour.BlockStateBase.class, "lightEmission");
            Field tool = field(BlockBehaviour.BlockStateBase.class, "requiresCorrectToolForDrops");
            for (Map.Entry<String, BlockModifications.Changes> entry : all.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
                if (block == null) {
                    LOGGER.warn("BlockEvents.modification: unknown block '{}'", entry.getKey());
                    continue;
                }
                BlockModifications.Changes changes = entry.getValue();
                if (changes.getExplosionResistance() != null) {
                    resistance.setFloat(block, changes.getExplosionResistance());
                }
                if (changes.getFriction() != null) {
                    friction.setFloat(block, changes.getFriction());
                }
                if (changes.getSpeedFactor() != null) {
                    speed.setFloat(block, changes.getSpeedFactor());
                }
                if (changes.getJumpFactor() != null) {
                    jump.setFloat(block, changes.getJumpFactor());
                }
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    if (changes.getDestroySpeed() != null) {
                        destroy.setFloat(state, changes.getDestroySpeed());
                    }
                    if (changes.getLightEmission() != null) {
                        light.setInt(state, changes.getLightEmission());
                    }
                    if (changes.getRequiresTool() != null) {
                        tool.setBoolean(state, changes.getRequiresTool());
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("BlockEvents.modification could not be applied on this version: {}", e.toString());
        }
    }
}
