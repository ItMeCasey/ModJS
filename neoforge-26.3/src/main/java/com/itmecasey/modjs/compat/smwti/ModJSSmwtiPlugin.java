package com.itmecasey.modjs.compat.smwti;

import com.itmecasey.modjs.info.InfoBridge;
import com.itmecasey.modjs.info.InfoDispatch;
import com.itmecasey.modjs.info.InfoEventJS;
import com.itmecasey.modjs.info.InfoLine;
import com.itmecasey.smwti.api.CommonRegistration;
import com.itmecasey.smwti.api.ClientRegistration;
import com.itmecasey.smwti.api.ISmwtiPlugin;
import com.itmecasey.smwti.api.Line;
import com.itmecasey.smwti.api.Position;
import com.itmecasey.smwti.api.SmwtiPlugin;
import com.itmecasey.smwti.api.Tooltip;

import java.util.Locale;

/**
 * Connects {@code InfoEvents} to Show Me What This Is, our look-at overlay. SMWTI finds this class by its annotation, so it
 * only loads when SMWTI is installed; nothing else in ModJS refers to it. Handlers that see real state run on the server
 * (block entities and entities), the rest on the client; see {@link InfoDispatch}.
 */
@SmwtiPlugin
public class ModJSSmwtiPlugin implements ISmwtiPlugin {
    @Override
    public void register(CommonRegistration registration) {
        registration.serverBlock((tooltip, level, pos, entity, detailed) -> {
            if (entity == null || !InfoDispatch.active("block")) {
                return;
            }
            InfoEventJS event = InfoBridge.blockEvent(level, pos, entity.getBlockState(), entity, "server", detailed);
            fill(tooltip, InfoDispatch.onServer(event));
        });
        registration.serverEntity((tooltip, level, entity, detailed) -> {
            if (!InfoDispatch.active("entity")) {
                return;
            }
            fill(tooltip, InfoDispatch.onServer(InfoBridge.entityEvent(entity, "server", detailed)));
        });
    }

    @Override
    public void registerClient(ClientRegistration registration) {
        registration.block((tooltip, view) -> {
            if (!InfoDispatch.active("block")) {
                return;
            }
            InfoEventJS event = InfoBridge.blockEvent(view.level(), view.pos(), view.state(), view.entity(), "client", view.detailed());
            fill(tooltip, InfoDispatch.onClient(event, view.entity() != null));
        });
        registration.entity((tooltip, view) -> {
            if (!InfoDispatch.active("entity")) {
                return;
            }
            fill(tooltip, InfoDispatch.onClient(InfoBridge.entityEvent(view.entity(), "client", view.detailed()), true));
        });
    }

    private static void fill(Tooltip tooltip, InfoEventJS event) {
        for (InfoLine line : event.lines()) {
            Position position = Position.valueOf(line.position().toUpperCase(Locale.ROOT));
            tooltip.add(new Line(InfoBridge.text(line), line.fraction(), line.barColor(), position, line.item(), line.count()));
        }
    }
}
