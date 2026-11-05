package com.itmecasey.modjs.compat.jade;

import com.itmecasey.modjs.info.InfoBridge;
import com.itmecasey.modjs.info.InfoDispatch;
import com.itmecasey.modjs.info.InfoEventJS;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Connects {@code InfoEvents} to Jade. Jade finds this class by its annotation, so it only loads when Jade is installed;
 * nothing else in ModJS refers to it. The server half runs the handlers that see real state and sends their lines along
 * with the rest of Jade's server data; the client half adds them to the tooltip (see {@link ModJSJadeClient}).
 */
@WailaPlugin
public class ModJSJadePlugin implements IWailaPlugin {
    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("modjs", path);
    }

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new IServerDataProvider<BlockAccessor>() {
            @Override
            public void appendServerData(CompoundTag data, BlockAccessor accessor) {
                if (accessor.getBlockEntity() == null || !InfoDispatch.active("block")) {
                    return;
                }
                InfoEventJS event = InfoBridge.blockEvent(accessor.getLevel(), accessor.getPosition(), accessor.getBlockState(),
                    accessor.getBlockEntity(), "server", false);
                InfoBridge.write(data, InfoDispatch.onServer(event));
            }

            @Override
            public ResourceLocation getUid() {
                return id("info_block_data");
            }
        }, Block.class);

        registration.registerEntityDataProvider(new IServerDataProvider<EntityAccessor>() {
            @Override
            public void appendServerData(CompoundTag data, EntityAccessor accessor) {
                if (!InfoDispatch.active("entity")) {
                    return;
                }
                InfoBridge.write(data, InfoDispatch.onServer(InfoBridge.entityEvent(accessor.getEntity(), "server", false)));
            }

            @Override
            public ResourceLocation getUid() {
                return id("info_entity_data");
            }
        }, Entity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        ModJSJadeClient.register(registration);
    }
}
