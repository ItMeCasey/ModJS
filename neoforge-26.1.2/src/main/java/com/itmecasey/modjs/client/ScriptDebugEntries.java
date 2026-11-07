package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventGroups;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;

/**
 * Brings {@code ClientEvents.leftDebugInfo} and {@code rightDebugInfo} to the F3 screen. This version has one debug entry
 * system and no separate left and right text events, so the lines from both script events show as one entry, left ones first.
 */
public final class ScriptDebugEntries {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("modjs", "script_lines");

    private ScriptDebugEntries() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ScriptDebugEntries::onRegister);
    }

    private static void onRegister(RegisterDebugEntriesEvent event) {
        event.register(ID, (displayer, level, clientChunk, serverChunk) -> {
            for (String name : new String[] {"leftDebugInfo", "rightDebugInfo"}) {
                if (EventGroups.CLIENT.hasHandlers(name)) {
                    DebugInfoEventJS js = new DebugInfoEventJS();
                    EventGroups.CLIENT.post(name, js);
                    js.lines().forEach(displayer::addLine);
                }
            }
        });
        event.includeInProfile(ID, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
        event.includeInProfile(ID, DebugScreenProfile.PERFORMANCE, DebugScreenEntryStatus.IN_OVERLAY);
    }
}
