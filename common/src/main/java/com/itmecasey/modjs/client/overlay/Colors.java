package com.itmecasey.modjs.client.overlay;

/** Reads script colors: a number (0xAARRGGBB, or 0xRRGGBB which counts as opaque) or a string such as "#ff8800" or "#80ff8800". */
public final class Colors {
    private Colors() {
    }

    public static int parse(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return fromLong(n.longValue());
        String s = String.valueOf(value).trim();
        try {
            if (s.startsWith("#")) s = s.substring(1);
            else if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
            long v = Long.parseLong(s, 16);
            return s.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int fromLong(long v) {
        return (v & 0xFF000000L) == 0 && v != 0 ? (int) (0xFF000000L | v) : (int) v;
    }

    /** Multiplies the alpha channel by a factor 0..1. */
    public static int withAlpha(int argb, double factor) {
        if (factor >= 1.0) return argb;
        int a = (int) Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0, factor));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int a, int b, double t) {
        double p = Math.max(0.0, Math.min(1.0, t));
        int ar = (int) Math.round(((a >>> 24) & 0xFF) * (1 - p) + ((b >>> 24) & 0xFF) * p);
        int rr = (int) Math.round(((a >> 16) & 0xFF) * (1 - p) + ((b >> 16) & 0xFF) * p);
        int gg = (int) Math.round(((a >> 8) & 0xFF) * (1 - p) + ((b >> 8) & 0xFF) * p);
        int bb = (int) Math.round((a & 0xFF) * (1 - p) + (b & 0xFF) * p);
        return (ar << 24) | (rr << 16) | (gg << 8) | bb;
    }
}
