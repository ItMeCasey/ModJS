package com.itmecasey.modjs.villager;

import com.itmecasey.modjs.config.ConfigHandle;
import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.script.ModJSContextFactory;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Chooses trades to remove: a script function {@code trade => boolean}, or an object whose fields all have to match
 * ({@code { level, give, want, id }}; ids may be written with {@code *} as a wildcard).
 */
public final class TradeFilter {
    private final Function function;
    private final Integer level;
    private final Pattern give;
    private final Pattern want;
    private final Pattern id;
    private final ScriptType owner;

    private TradeFilter(Function function, Integer level, Pattern give, Pattern want, Pattern id, ScriptType owner) {
        this.function = function;
        this.level = level;
        this.give = give;
        this.want = want;
        this.id = id;
        this.owner = owner;
    }

    /** Matches every trade. */
    public static TradeFilter all() {
        return new TradeFilter(null, null, null, null, null, null);
    }

    public static TradeFilter parse(Object raw, ScriptType owner) {
        Object value = JsonUtil.unwrap(raw);
        if (value == null) {
            return all();
        }
        if (value instanceof Function fn) {
            return new TradeFilter(fn, null, null, null, null, owner);
        }
        Object level = JsonUtil.property(value, "level");
        if (level != null && !(JsonUtil.unwrap(level) instanceof Number)) {
            throw new IllegalArgumentException("the filter field 'level' must be a number");
        }
        return new TradeFilter(null, level == null ? null : ((Number) JsonUtil.unwrap(level)).intValue(), pattern(value, "give"), pattern(value, "want"), pattern(value, "id"), owner);
    }

    private static Pattern pattern(Object value, String key) {
        Object raw = JsonUtil.property(value, key);
        if (raw == null) {
            return null;
        }
        String text = JsonUtil.asString(raw);
        if (text == null) {
            throw new IllegalArgumentException("the filter field '" + key + "' must be an id such as minecraft:emerald (with * as a wildcard)");
        }
        String full = text.indexOf(':') < 0 && !text.startsWith("*") ? "minecraft:" + text : text;
        StringBuilder regex = new StringBuilder();
        for (String part : full.split("\\*", -1)) {
            if (regex.length() > 0 || full.startsWith("*")) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }

    /** Whether a trade, described as {@link TradeSpec#view}, matches. */
    @SuppressWarnings("unchecked")
    public boolean matches(Map<String, Object> view) {
        if (level != null && !level.equals(view.get("level"))) {
            return false;
        }
        if (id != null && !id.matcher(String.valueOf(view.get("id"))).matches()) {
            return false;
        }
        if (give != null && !give.matcher(String.valueOf(((Map<String, Object>) view.get("give")).get("id"))).matches()) {
            return false;
        }
        if (want != null) {
            boolean any = false;
            for (Object cost : (List<Object>) view.get("want")) {
                any |= want.matcher(String.valueOf(((Map<String, Object>) cost).get("id"))).matches();
            }
            if (!any) {
                return false;
            }
        }
        return function == null || call(view);
    }

    private boolean call(Map<String, Object> view) {
        Scriptable scope = function.getParentScope();
        try {
            return Boolean.TRUE.equals(ModJSContextFactory.INSTANCE.call(cx -> {
                Object trade = ConfigHandle.plain(view, scope);
                return Context.toBoolean(function.call(cx, scope, scope, new Object[] {trade}));
            }));
        } catch (RhinoException e) {
            ScriptManager.of(owner == null ? ScriptType.SERVER : owner).reportError("Villagers.removeTrades filter", e);
            return false;
        }
    }
}
