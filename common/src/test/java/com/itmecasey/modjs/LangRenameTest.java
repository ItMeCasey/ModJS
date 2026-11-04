package com.itmecasey.modjs;

import com.itmecasey.modjs.data.LangEventJS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LangRenameTest {
    @Test
    void renameBuildsVanillaLangKeys() {
        LangEventJS lang = new LangEventJS("en_us");
        lang.renameItem("minecraft:diamond", "Shiny Rock");
        lang.renameBlock("stone", "Hard Rock");
        lang.renameEntity("mymod:big/zombie", "Biter");
        assertEquals("Shiny Rock", lang.entries().get("item.minecraft.diamond"));
        assertEquals("Hard Rock", lang.entries().get("block.minecraft.stone"));
        assertEquals("Biter", lang.entries().get("entity.mymod.big.zombie"));
    }
}
