package com.itmecasey.modjs.client.overlay;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

/**
 * One thing an overlay draws. Scripts create elements once ({@code Overlay.bar('hp', {...})}), then change the public fields
 * ({@code bar.value = 40}) or animate them ({@code bar.animate({ x: 100 }, 500, 'easeOut')}). Numbers are in scaled GUI pixels,
 * colors are numbers (0xAARRGGBB, or 0xRRGGBB for opaque) or strings such as "#ff8800".
 */
public abstract class OverlayElement {
    public final String id;
    /** Position, relative to {@link #anchor}. */
    public double x;
    public double y;
    /** Where on the screen x and y count from: topleft, top, topright, left, center, right, bottomleft, bottom, bottomright. */
    public String anchor = "topleft";
    /** "hud" is drawn in the world, "screen" on your own screens. */
    public String surface = "hud";
    public boolean visible = true;
    /** 0 (invisible) to 1; multiplies the alpha of every color of the element. */
    public double alpha = 1.0;
    /** Size factor. Bars, rectangles and images multiply their width and height, text and items scale themselves. */
    public double scale = 1.0;
    /** Higher layers draw on top. */
    public int layer;

    private final List<Tween> tweens = new ArrayList<>();
    long lastTick = Long.MIN_VALUE;

    protected OverlayElement(String id) {
        this.id = id;
    }

    // -- layout, read by the renderer -------------------------------------------------------------------------------

    /** Unscaled width of the element in GUI pixels. */
    public abstract double baseWidth(OverlayCanvas canvas);

    public abstract double baseHeight(OverlayCanvas canvas);

    public abstract void draw(OverlayCanvas canvas, int left, int top, int width, int height, double effectiveAlpha);

    /** Called once per frame before drawing, with the current time in milliseconds. */
    protected void onTick(long now, double deltaMs) {
    }

    // -- animation --------------------------------------------------------------------------------------------------

    /** Moves the named numeric fields to new values over a time. Example: {@code el.animate({ x: 100, alpha: 0.5 }, 400, 'easeOut')}. */
    public OverlayElement animate(Object properties, double milliseconds, String easing) {
        if (!Easing.known(easing)) {
            throw Context.reportRuntimeError("Overlay: unknown easing '" + easing + "' (linear, easeIn, easeOut, easeInOut, easeOutBack, bounce)");
        }
        if (!(properties instanceof ScriptableObject object)) {
            throw Context.reportRuntimeError("Overlay: animate needs an object such as { x: 100 }");
        }
        for (Object key : object.getIds()) {
            String name = String.valueOf(key);
            Object value = ScriptableObject.getProperty(object, name);
            if (!(value instanceof Number number) || !hasNumber(name)) {
                throw Context.reportRuntimeError("Overlay: '" + name + "' is not a number field of this element");
            }
            tweens.removeIf(t -> t.field.equals(name));
            tweens.add(new Tween(name, Double.NaN, number.doubleValue(), Overlay.now(), Math.max(0.0, milliseconds), easing, false));
        }
        return this;
    }

    public OverlayElement animate(Object properties, double milliseconds) {
        return animate(properties, milliseconds, "linear");
    }

    /** Fades the named field to a value without needing a script object. Used by fadeIn and fadeOut. */
    public OverlayElement animateTo(String field, double value, double milliseconds, String easing) {
        if (!hasNumber(field)) {
            throw Context.reportRuntimeError("Overlay: '" + field + "' is not a number field of this element");
        }
        tweens.removeIf(t -> t.field.equals(field));
        tweens.add(new Tween(field, Double.NaN, value, Overlay.now(), Math.max(0.0, milliseconds), easing, false));
        return this;
    }

    public OverlayElement fadeIn(double milliseconds) {
        visible = true;
        alpha = 0.0;
        return animateTo("alpha", 1.0, milliseconds, "easeOut");
    }

    public OverlayElement fadeOut(double milliseconds) {
        return animateTo("alpha", 0.0, milliseconds, "easeIn");
    }

    /** Fades the element between two alpha values forever, one full cycle every {@code periodMs}. */
    public OverlayElement pulse(double periodMs, double minAlpha, double maxAlpha) {
        tweens.removeIf(t -> t.field.equals("alpha"));
        alpha = maxAlpha;
        tweens.add(new Tween("alpha", maxAlpha, minAlpha, Overlay.now(), Math.max(1.0, periodMs / 2.0), "easeInOut", true));
        return this;
    }

    public OverlayElement pulse(double periodMs) {
        return pulse(periodMs, 0.35, 1.0);
    }

    /** Stops every running animation and leaves the fields where they are. */
    public OverlayElement stop() {
        tweens.clear();
        return this;
    }

    public boolean isAnimating() {
        return !tweens.isEmpty();
    }

    public void remove() {
        Overlay.remove(id);
    }

    final void tick(long now) {
        double delta = lastTick == Long.MIN_VALUE ? 0.0 : Math.max(0, now - lastTick);
        lastTick = now;
        for (int i = tweens.size() - 1; i >= 0; i--) {
            Tween tween = tweens.get(i);
            if (Double.isNaN(tween.from)) {
                tween.from = getNumber(tween.field);
            }
            double progress = tween.duration <= 0 ? 1.0 : (now - tween.start) / tween.duration;
            if (tween.loop) {
                long cycle = (long) Math.floor(progress);
                double within = progress - cycle;
                double forward = (cycle % 2 == 0) ? within : 1.0 - within;
                setNumber(tween.field, tween.from + (tween.to - tween.from) * Easing.apply(tween.easing, forward));
            } else if (progress >= 1.0) {
                setNumber(tween.field, tween.to);
                tweens.remove(i);
            } else {
                setNumber(tween.field, tween.from + (tween.to - tween.from) * Easing.apply(tween.easing, progress));
            }
        }
        onTick(now, delta);
    }

    // -- fields by name ---------------------------------------------------------------------------------------------

    public boolean hasNumber(String name) {
        Field field = field(name);
        return field != null && field.getType() == double.class;
    }

    public double getNumber(String name) {
        try {
            return field(name).getDouble(this);
        } catch (IllegalAccessException | RuntimeException e) {
            return 0.0;
        }
    }

    public void setNumber(String name, double value) {
        try {
            field(name).setDouble(this, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
            // an unknown name was already refused when the animation was created
        }
    }

    private Field field(String name) {
        for (Class<?> type = getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                if (Modifier.isPublic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
                // try the parent class
            }
        }
        return null;
    }

    /** Sets fields from a script options object. Unknown names are an error, so a typo does not silently do nothing. */
    public void apply(Object options) {
        if (options == null || options instanceof org.mozilla.javascript.Undefined) {
            return;
        }
        if (!(options instanceof ScriptableObject object)) {
            throw Context.reportRuntimeError("Overlay: options must be an object such as { x: 10, y: 10 }");
        }
        for (Object key : object.getIds()) {
            String name = String.valueOf(key);
            Field field = field(name);
            if (field == null) {
                throw Context.reportRuntimeError("Overlay: unknown option '" + name + "' for " + getClass().getSimpleName().replace("Overlay", "").toLowerCase());
            }
            Object value = ScriptableObject.getProperty(object, name);
            try {
                Class<?> type = field.getType();
                if (type == double.class) {
                    field.setDouble(this, Context.toNumber(value));
                } else if (type == int.class) {
                    field.setInt(this, (int) Context.toNumber(value));
                } else if (type == boolean.class) {
                    field.setBoolean(this, Context.toBoolean(value));
                } else if (type == String.class) {
                    field.set(this, value == null ? null : Context.toString(value));
                } else {
                    field.set(this, value);
                }
            } catch (IllegalAccessException | RuntimeException e) {
                throw Context.reportRuntimeError("Overlay: option '" + name + "' has a value of the wrong kind (" + e.getMessage() + ")");
            }
        }
    }

    private static final class Tween {
        final String field;
        double from;
        final double to;
        final long start;
        final double duration;
        final String easing;
        final boolean loop;

        Tween(String field, double from, double to, long start, double duration, String easing, boolean loop) {
            this.field = field;
            this.from = from;
            this.to = to;
            this.start = start;
            this.duration = duration;
            this.easing = easing;
            this.loop = loop;
        }
    }
}
