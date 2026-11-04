package com.itmecasey.modjs.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The {@code Text} global: {@code Text.of("hi")}, {@code Text.red("warning")}, {@code Text.translate("key")}. */
public final class TextBinding {
    public MutableComponent of(String text) {
        return Component.literal(text);
    }

    public MutableComponent translate(String key) {
        return Component.translatable(key);
    }

    /** Colors text by name, for example {@code "gold"} or {@code "dark_red"}; unknown names leave it unstyled. */
    public MutableComponent color(String color, String text) {
        ChatFormatting formatting = ChatFormatting.getByName(color);
        MutableComponent component = Component.literal(text);
        return formatting == null ? component : component.withStyle(formatting);
    }

    public MutableComponent red(String text) {
        return color("red", text);
    }

    public MutableComponent green(String text) {
        return color("green", text);
    }

    public MutableComponent yellow(String text) {
        return color("yellow", text);
    }

    public MutableComponent gold(String text) {
        return color("gold", text);
    }

    public MutableComponent aqua(String text) {
        return color("aqua", text);
    }

    public MutableComponent gray(String text) {
        return color("gray", text);
    }

    public MutableComponent white(String text) {
        return color("white", text);
    }
}
