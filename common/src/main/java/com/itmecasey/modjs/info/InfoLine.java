package com.itmecasey.modjs.info;

/**
 * One line of in-world info a script added: text, optionally colored, optionally with a bar under it or an item icon
 * before it. The loader modules turn it into a Show Me What This Is line or a Jade tooltip row.
 *
 * @param text      a string, or a game text component a script built with {@code Text}
 * @param textColor RGB color of the text, or -1 for the default color
 * @param fraction  how full the bar is (0 to 1), or {@link #NO_BAR}
 * @param barColor  RGB color of the bar
 * @param item      registry name of the icon's item, or an empty string
 * @param count     how many of the item the icon shows
 * @param position  {@code head}, {@code body}, {@code tail} or {@code badge}
 */
public record InfoLine(Object text, int textColor, float fraction, int barColor, String item, int count, String position) {
    public static final float NO_BAR = -1.0F;

    public boolean hasBar() {
        return fraction >= 0.0F;
    }
}
