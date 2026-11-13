package com.itmecasey.modjs.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The config file format: JSON with {@code //} comments, written with a comment above every entry (the file extension is {@code .json5}).
 * Reading accepts JSON5-style leniency (comments, unquoted names, single quotes); writing always produces plain quoted keys.
 */
public final class ConfigFormat {
    private static final Gson GSON = new Gson();

    private ConfigFormat() {
    }

    /** A file that cannot be read as an object. */
    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    /** The checked values, one line per problem that was repaired, and whether the file should be written again. */
    public record Validated(Map<String, Object> values, List<String> problems, boolean rewrite) {
    }

    // -- reading ----------------------------------------------------------------------------------------------------------

    /** Parses file text into plain Java values (map, list, string, double, boolean, null). Empty text is an empty object. */
    public static Object parse(String text) throws ParseException {
        if (text.isBlank()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setLenient(true);
            JsonElement element = JsonParser.parseReader(reader);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) {
                throw new ParseException("extra text after the end of the object");
            }
            return plain(element);
        } catch (ParseException e) {
            throw e;
        } catch (RuntimeException | java.io.IOException e) {
            throw new ParseException(e.getMessage() == null ? e.toString() : e.getMessage().replace('\n', ' '));
        }
    }

    private static Object plain(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element instanceof JsonObject object) {
            Map<String, Object> out = new LinkedHashMap<>();
            object.entrySet().forEach(e -> out.put(e.getKey(), plain(e.getValue())));
            return out;
        }
        if (element instanceof JsonArray array) {
            List<Object> out = new ArrayList<>();
            array.forEach(e -> out.add(plain(e)));
            return out;
        }
        var primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsDouble();
        }
        return primitive.getAsString();
    }

    /**
     * Checks raw file contents against the schema. Missing entries take their default (and the file is rewritten), a wrong type or an
     * out-of-range value is reported and replaced by the default, unknown keys are reported and dropped.
     */
    public static Validated validate(ConfigSpec.GroupSpec schema, Object raw) {
        List<String> problems = new ArrayList<>();
        boolean[] rewrite = {false};
        Map<String, Object> source;
        if (raw instanceof Map<?, ?> m) {
            source = new LinkedHashMap<>();
            m.forEach((k, v) -> source.put(String.valueOf(k), v));
        } else {
            problems.add("the file must hold an object; using the defaults");
            rewrite[0] = true;
            source = Map.of();
        }
        Map<String, Object> values = group(schema, source, "", problems, rewrite);
        return new Validated(values, problems, rewrite[0]);
    }

    private static Map<String, Object> group(ConfigSpec.GroupSpec spec, Map<String, Object> source, String prefix, List<String> problems, boolean[] rewrite) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigSpec> child : spec.children.entrySet()) {
            String key = child.getKey();
            String path = prefix + key;
            ConfigSpec childSpec = child.getValue();
            if (!source.containsKey(key)) {
                out.put(key, childSpec.defaultValue());
                if (!(childSpec instanceof ConfigSpec.GroupSpec)) {
                    problems.add(path + ": missing, added with the default " + display(childSpec.defaultValue()));
                }
                rewrite[0] = true;
                continue;
            }
            Object value = source.get(key);
            if (childSpec instanceof ConfigSpec.GroupSpec nested) {
                if (value instanceof Map<?, ?> m) {
                    Map<String, Object> inner = new LinkedHashMap<>();
                    m.forEach((k, v) -> inner.put(String.valueOf(k), v));
                    out.put(key, group(nested, inner, path + ".", problems, rewrite));
                } else {
                    problems.add(path + ": expected an object, using the defaults");
                    out.put(key, nested.defaultValue());
                    rewrite[0] = true;
                }
                continue;
            }
            ConfigSpec.Result result = value == null ? new ConfigSpec.Result(null, "has no value") : childSpec.coerce(value);
            if (result.ok()) {
                out.put(key, result.value());
            } else {
                problems.add(path + ": " + result.problem() + ", using the default " + display(childSpec.defaultValue()));
                out.put(key, childSpec.defaultValue());
                rewrite[0] = true;
            }
        }
        for (String key : source.keySet()) {
            if (!spec.children.containsKey(key)) {
                problems.add(prefix + key + ": unknown key, ignored");
                rewrite[0] = true;
            }
        }
        return out;
    }

    static String display(Object value) {
        return GSON.toJson(value);
    }

    // -- writing ----------------------------------------------------------------------------------------------------------

    /** The file text for the values: a comment above each entry (the script's comment, then the type and limits), two-space indentation. */
    public static String render(ConfigSpec.GroupSpec schema, Map<String, Object> values) {
        StringBuilder out = new StringBuilder();
        writeGroup(out, schema, values, 0);
        out.append('\n');
        return out.toString();
    }

    private static void writeGroup(StringBuilder out, ConfigSpec.GroupSpec spec, Map<String, Object> values, int depth) {
        String pad = "  ".repeat(depth + 1);
        out.append("{\n");
        int index = 0;
        int count = spec.children.size();
        for (Map.Entry<String, ConfigSpec> child : spec.children.entrySet()) {
            ConfigSpec childSpec = child.getValue();
            Object value = values.get(child.getKey());
            if (index > 0) {
                out.append('\n');
            }
            for (String line : comment(childSpec)) {
                out.append(pad).append("// ").append(line).append('\n');
            }
            out.append(pad).append(GSON.toJson(child.getKey())).append(": ");
            if (childSpec instanceof ConfigSpec.GroupSpec nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> inner = value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
                writeGroup(out, nested, inner, depth + 1);
            } else {
                out.append(value(value));
            }
            index++;
            if (index < count) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append("  ".repeat(depth)).append('}');
    }

    private static List<String> comment(ConfigSpec spec) {
        List<String> lines = new ArrayList<>();
        if (spec.comment != null && !spec.comment.isBlank()) {
            for (String line : spec.comment.split("\\R")) {
                lines.add(line.stripTrailing());
            }
        }
        if (!(spec instanceof ConfigSpec.GroupSpec)) {
            String constraint = spec.constraint();
            String fallback = display(spec.defaultValue());
            lines.add(constraint.isEmpty() ? "Default: " + fallback : constraint.substring(0, 1).toUpperCase() + constraint.substring(1) + ". Default: " + fallback);
        }
        return lines;
    }

    private static String value(Object value) {
        if (value instanceof Double d) {
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? (long) d.doubleValue() + ".0" : String.valueOf(d);
        }
        return GSON.toJson(value);
    }
}
