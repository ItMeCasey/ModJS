package com.itmecasey.modjs.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One entry of a config schema: its default, how a raw value (read from the file or given by a script) is checked and converted, and
 * the comment written above it in the file. Values are plain Java objects: {@code Double} or {@code Long} for numbers, {@code Boolean},
 * {@code String}, {@code List} and {@code Map} (for a group).
 */
public abstract class ConfigSpec {
    /** Text written above the entry in the file. */
    public String comment = "";

    /** A value checked and converted, or the reason it was refused (then the caller uses the default). */
    public record Result(Object value, String problem) {
        public boolean ok() {
            return problem == null;
        }

        static Result of(Object value) {
            return new Result(value, null);
        }

        static Result bad(String problem) {
            return new Result(null, problem);
        }
    }

    public abstract Object defaultValue();

    /** Converts a raw value (number, boolean, text, list, map) to this entry's type. */
    public abstract Result coerce(Object raw);

    /** The type, limits and default of the entry as text; two entries with the same signature behave the same. */
    public String signature() {
        return getClass().getSimpleName() + "(" + constraint() + ";" + String.valueOf(defaultValue()) + ")";
    }

    /** Facts about the entry for the file comment, such as {@code "0 to 10"}, or an empty text. */
    public String constraint() {
        return "";
    }

    static String show(Object raw) {
        if (raw instanceof CharSequence) {
            return "\"" + raw + "\"";
        }
        return String.valueOf(raw);
    }

    static String number(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    // -- numbers --------------------------------------------------------------------------------------------------------

    public static final class NumberSpec extends ConfigSpec {
        public final boolean integer;
        public final double fallback;
        public final Double min;
        public final Double max;

        public NumberSpec(boolean integer, double fallback, Double min, Double max) {
            this.integer = integer;
            this.fallback = fallback;
            this.min = min;
            this.max = max;
        }

        @Override
        public Object defaultValue() {
            return integer ? (Object) (long) fallback : (Object) fallback;
        }

        @Override
        public Result coerce(Object raw) {
            double d;
            if (raw instanceof Number n) {
                d = n.doubleValue();
            } else if (raw instanceof CharSequence text) {
                try {
                    d = Double.parseDouble(text.toString().trim());
                } catch (NumberFormatException e) {
                    return Result.bad("expected a number, got " + show(raw));
                }
            } else {
                return Result.bad("expected a number, got " + show(raw));
            }
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return Result.bad("expected a finite number, got " + raw);
            }
            if (integer && d != Math.rint(d)) {
                return Result.bad("expected a whole number, got " + number(d));
            }
            if (min != null && d < min) {
                return Result.bad(number(d) + " is below the minimum " + number(min));
            }
            if (max != null && d > max) {
                return Result.bad(number(d) + " is above the maximum " + number(max));
            }
            return Result.of(integer ? (Object) (long) d : (Object) d);
        }

        @Override
        public String constraint() {
            String kind = integer ? "whole number" : "number";
            if (min != null && max != null) {
                return kind + " from " + number(min) + " to " + number(max);
            }
            if (min != null) {
                return kind + " of at least " + number(min);
            }
            if (max != null) {
                return kind + " of at most " + number(max);
            }
            return kind;
        }
    }

    // -- booleans -------------------------------------------------------------------------------------------------------

    public static final class BoolSpec extends ConfigSpec {
        public final boolean fallback;

        public BoolSpec(boolean fallback) {
            this.fallback = fallback;
        }

        @Override
        public Object defaultValue() {
            return fallback;
        }

        @Override
        public Result coerce(Object raw) {
            if (raw instanceof Boolean b) {
                return Result.of(b);
            }
            if (raw instanceof CharSequence text) {
                String t = text.toString().trim().toLowerCase(Locale.ROOT);
                if (t.equals("true")) {
                    return Result.of(true);
                }
                if (t.equals("false")) {
                    return Result.of(false);
                }
            }
            return Result.bad("expected true or false, got " + show(raw));
        }

        @Override
        public String constraint() {
            return "true or false";
        }
    }

    // -- text -----------------------------------------------------------------------------------------------------------

    public static final class StringSpec extends ConfigSpec {
        public final String fallback;
        public final int maxLength;
        public final Pattern pattern;

        public StringSpec(String fallback, int maxLength, Pattern pattern) {
            this.fallback = fallback;
            this.maxLength = maxLength;
            this.pattern = pattern;
        }

        @Override
        public Object defaultValue() {
            return fallback;
        }

        @Override
        public Result coerce(Object raw) {
            String text;
            if (raw instanceof CharSequence cs) {
                text = cs.toString();
            } else if (raw instanceof Number || raw instanceof Boolean) {
                text = raw instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && Math.abs(n.doubleValue()) < 1e15
                    ? String.valueOf(n.longValue()) : String.valueOf(raw);
            } else {
                return Result.bad("expected text, got " + show(raw));
            }
            if (maxLength > 0 && text.length() > maxLength) {
                return Result.bad("text is longer than " + maxLength + " characters");
            }
            if (pattern != null && !pattern.matcher(text).matches()) {
                return Result.bad(show(text) + " does not match " + pattern.pattern());
            }
            return Result.of(text);
        }

        @Override
        public String constraint() {
            return pattern != null ? "text matching " + pattern.pattern() : maxLength > 0 ? "text of at most " + maxLength + " characters" : "text";
        }
    }

    // -- choices --------------------------------------------------------------------------------------------------------

    public static final class ChoiceSpec extends ConfigSpec {
        public final String fallback;
        public final List<String> options;

        public ChoiceSpec(String fallback, List<String> options) {
            this.fallback = fallback;
            this.options = List.copyOf(options);
        }

        @Override
        public Object defaultValue() {
            return fallback;
        }

        @Override
        public Result coerce(Object raw) {
            if (raw instanceof CharSequence cs) {
                String text = cs.toString().trim();
                for (String option : options) {
                    if (option.equals(text)) {
                        return Result.of(option);
                    }
                }
                for (String option : options) {
                    if (option.equalsIgnoreCase(text)) {
                        return Result.of(option);
                    }
                }
            }
            return Result.bad("expected one of " + String.join(", ", options) + ", got " + show(raw));
        }

        @Override
        public String constraint() {
            return "one of " + String.join(", ", options);
        }
    }

    // -- lists ----------------------------------------------------------------------------------------------------------

    /** A list of one element type: {@code string}, {@code number}, {@code int} or {@code bool}. */
    public static final class ListSpec extends ConfigSpec {
        public final String element;
        public final List<Object> fallback;
        public final int maxSize;
        private final ConfigSpec each;

        public ListSpec(String element, List<Object> fallback, int maxSize, Double min, Double max) {
            this.element = element;
            this.maxSize = maxSize;
            this.each = switch (element) {
                case "number" -> new NumberSpec(false, 0, min, max);
                case "int" -> new NumberSpec(true, 0, min, max);
                case "bool" -> new BoolSpec(false);
                default -> new StringSpec("", 0, null);
            };
            this.fallback = new ArrayList<>(fallback);
        }

        @Override
        public Object defaultValue() {
            return new ArrayList<>(fallback);
        }

        @Override
        public Result coerce(Object raw) {
            if (!(raw instanceof List<?> items)) {
                return Result.bad("expected a list, got " + show(raw));
            }
            if (maxSize > 0 && items.size() > maxSize) {
                return Result.bad("the list has " + items.size() + " entries, at most " + maxSize + " are allowed");
            }
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                Result r = each.coerce(items.get(i));
                if (!r.ok()) {
                    return Result.bad("entry " + (i + 1) + ": " + r.problem());
                }
                out.add(r.value());
            }
            return Result.of(out);
        }

        @Override
        public String constraint() {
            return "list of " + (element.equals("int") ? "whole numbers" : element.equals("bool") ? "booleans" : element + "s");
        }
    }

    // -- groups ---------------------------------------------------------------------------------------------------------

    /** A named set of entries (the schema itself, or a nested object). */
    public static final class GroupSpec extends ConfigSpec {
        public final Map<String, ConfigSpec> children = new LinkedHashMap<>();

        @Override
        public Object defaultValue() {
            Map<String, Object> out = new LinkedHashMap<>();
            children.forEach((key, spec) -> out.put(key, spec.defaultValue()));
            return out;
        }

        @Override
        public Result coerce(Object raw) {
            // groups are checked key by key by ConfigFormat.validate
            return raw instanceof Map<?, ?> ? Result.of(raw) : Result.bad("expected an object, got " + show(raw));
        }

        @Override
        public String signature() {
            StringBuilder out = new StringBuilder("Group{");
            children.forEach((key, child) -> out.append(key).append('=').append(child.signature()).append(','));
            return out.append('}').toString();
        }

        /** The entry at a dotted path such as {@code "nested.speed"}, or null. */
        public ConfigSpec find(String path) {
            ConfigSpec current = this;
            for (String part : path.split("\\.", -1)) {
                if (!(current instanceof GroupSpec group) || !group.children.containsKey(part)) {
                    return null;
                }
                current = group.children.get(part);
            }
            return current;
        }
    }
}
