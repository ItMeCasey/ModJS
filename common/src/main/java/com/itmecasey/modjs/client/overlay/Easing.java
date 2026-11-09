package com.itmecasey.modjs.client.overlay;

/** Easing curves for overlay animations: a function of progress 0..1 that returns progress 0..1 (back may overshoot). */
public final class Easing {
    private Easing() {
    }

    public static double apply(String name, double t) {
        double p = Math.max(0.0, Math.min(1.0, t));
        switch (name == null ? "linear" : name) {
            case "easeIn":
                return p * p;
            case "easeOut":
                return 1.0 - (1.0 - p) * (1.0 - p);
            case "easeInOut":
                return p < 0.5 ? 2.0 * p * p : 1.0 - Math.pow(-2.0 * p + 2.0, 2) / 2.0;
            case "easeOutBack": {
                double c1 = 1.70158, c3 = c1 + 1.0;
                return 1.0 + c3 * Math.pow(p - 1.0, 3) + c1 * Math.pow(p - 1.0, 2);
            }
            case "bounce": {
                double n1 = 7.5625, d1 = 2.75;
                if (p < 1.0 / d1) return n1 * p * p;
                if (p < 2.0 / d1) { p -= 1.5 / d1; return n1 * p * p + 0.75; }
                if (p < 2.5 / d1) { p -= 2.25 / d1; return n1 * p * p + 0.9375; }
                p -= 2.625 / d1;
                return n1 * p * p + 0.984375;
            }
            default:
                return p;
        }
    }

    public static boolean known(String name) {
        return name == null || java.util.List.of("linear", "easeIn", "easeOut", "easeInOut", "easeOutBack", "bounce").contains(name);
    }
}
