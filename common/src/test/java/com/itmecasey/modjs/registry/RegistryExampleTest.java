package com.itmecasey.modjs.registry;

import com.itmecasey.modjs.data.DataBuilder;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.pack.GeneratedData;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads examples/registry (the script the wiki shows) and checks that every kind of registration comes out. */
class RegistryExampleTest {
    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        for (ScriptType type : ScriptType.values()) {
            EventGroups.clear(type);
        }
    }

    private static Path exampleFile() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isRegularFile(dir.resolve("examples/registry/startup_scripts/registry_example.js"))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "examples/registry/startup_scripts/registry_example.js not found above the working directory");
        return dir.resolve("examples/registry/startup_scripts/registry_example.js");
    }

    @Test
    void theExampleRegistersOneOfEachKind() throws IOException {
        Path target = root.resolve("startup_scripts/registry_example.js");
        Files.createDirectories(target.getParent());
        Files.copy(exampleFile(), target);
        ScriptManager.of(ScriptType.STARTUP).reload();
        RegistryManager.collect();

        assertEquals(0, ScriptManager.of(ScriptType.STARTUP).errors().size(), ScriptManager.of(ScriptType.STARTUP).errors().toString());
        assertEquals(1, RegistryManager.tabs().size());
        assertEquals(3, RegistryManager.items().size());
        assertEquals(4, RegistryManager.blocks().size());
        assertEquals(1, RegistryManager.toolTiers().size());
        assertEquals(1, RegistryManager.armorMaterials().size());
        assertEquals(1, RegistryManager.fluids().size());
        assertTrue(RegistryManager.blocks().stream().anyMatch(block -> block.id().equals("ruby_crate")));

        GeneratedData assets = new GeneratedData();
        DataBuilder.buildClientAssets(assets);
        String lang = new String(assets.get("modjs", "lang/en_us.json"), StandardCharsets.UTF_8);
        for (String key : new String[] {"item.modjs.ruby", "item.modjs.ruby_pickaxe", "item.modjs.ruby_helmet", "block.modjs.ruby_block",
            "block.modjs.ruby_slab", "block.modjs.ruby_stairs", "block.modjs.ruby_crate", "itemGroup.modjs.ruby_tab"}) {
            assertTrue(lang.contains(key), "lang is missing " + key);
        }
    }
}
