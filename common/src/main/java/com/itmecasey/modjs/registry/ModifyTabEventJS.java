package com.itmecasey.modjs.registry;

import com.itmecasey.modjs.event.EventJS;

import java.util.ArrayList;
import java.util.List;

/** The event passed to {@code StartupEvents.modifyCreativeTab('minecraft:building_blocks', event => event.add('modjs:ruby'))}. */
public final class ModifyTabEventJS extends EventJS {
    private final String tab;
    private final List<String> added = new ArrayList<>();

    public ModifyTabEventJS(String tab) {
        this.tab = tab;
    }

    public String getTab() {
        return tab;
    }

    /** Adds items by id to the end of the tab. */
    public void add(String... itemIds) {
        for (String id : itemIds) {
            added.add(id.contains(":") ? id : "minecraft:" + id);
        }
    }

    public List<String> added() {
        return added;
    }
}
