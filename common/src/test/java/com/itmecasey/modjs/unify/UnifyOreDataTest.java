package com.itmecasey.modjs.unify;

import com.itmecasey.modjs.pack.GeneratedData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifyOreDataTest {
    @AfterEach
    void reset() {
        UnifyConfig.setOres(false, false);
    }

    @Test
    void writesNothingWhenOreSwappingIsOff() {
        UnifyConfig.setOres(false, true);
        GeneratedData data = new GeneratedData();
        UnifyOreData.contribute(data);
        assertEquals(0, data.size());
    }

    @Test
    void writesTheModifierWhenOreSwappingIsOn() {
        UnifyConfig.setOres(true, false);
        GeneratedData data = new GeneratedData();
        UnifyOreData.contribute(data);
        assertTrue(data.has("modjs", "forge/biome_modifier/unify_ores.json"));
        assertTrue(new String(data.get("modjs", "forge/biome_modifier/unify_ores.json")).contains("modjs:unify_ores"));
    }
}
