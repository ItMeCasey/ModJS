package com.itmecasey.modjs.worldcraft;

import com.itmecasey.modjs.json.JsonUtil;
import org.mozilla.javascript.Context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reads the block position lists that scripts pass around: {@code [{x, y, z}, ...]} (what {@code world.connected} returns) or {@code [[x, y, z], ...]}. */
public final class Positions {
    private Positions() {
    }

    /** The positions in order, without repeats, at most {@code limit}. Anything unreadable is a script error that says which entry and which call. */
    public static List<int[]> parse(Object list, int limit, String caller) {
        List<int[]> out = new ArrayList<>();
        if (list == null) {
            return out;
        }
        if (!(JsonUtil.unwrap(list) instanceof List<?> items)) {
            throw Context.reportRuntimeError(caller + ": positions must be an array of {x, y, z} objects or [x, y, z] arrays");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Object item : items) {
            if (out.size() >= limit) {
                break;
            }
            int[] p = position(item);
            if (p == null) {
                throw Context.reportRuntimeError(caller + ": position " + (out.size() + 1) + " needs numbers for x, y and z");
            }
            if (seen.add(p[0] + "," + p[1] + "," + p[2])) {
                out.add(p);
            }
        }
        return out;
    }

    /** One position from {@code {x, y, z}} or {@code [x, y, z]}, or null. */
    public static int[] position(Object item) {
        Object plain = JsonUtil.unwrap(item);
        if (plain instanceof List<?> triple && triple.size() >= 3) {
            return numbers(triple.get(0), triple.get(1), triple.get(2));
        }
        return numbers(JsonUtil.property(plain, "x"), JsonUtil.property(plain, "y"), JsonUtil.property(plain, "z"));
    }

    private static int[] numbers(Object x, Object y, Object z) {
        if (JsonUtil.unwrap(x) instanceof Number a && JsonUtil.unwrap(y) instanceof Number b && JsonUtil.unwrap(z) instanceof Number c) {
            return new int[] {(int) Math.floor(a.doubleValue()), (int) Math.floor(b.doubleValue()), (int) Math.floor(c.doubleValue())};
        }
        return null;
    }
}
