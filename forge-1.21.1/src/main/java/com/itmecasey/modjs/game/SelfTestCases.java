package com.itmecasey.modjs.game;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

/** The single game test that hosts {@link SelfTest}; registered only when -Dmodjs.selftest=true. */
@GameTestHolder("modjs")
public final class SelfTestCases {
    private SelfTestCases() {
    }

    @GameTest(template = "modjs:empty")
    public static void scriptEvents(GameTestHelper helper) {
        SelfTest.run(helper.getLevel().getServer());
        helper.succeed();
    }
}
