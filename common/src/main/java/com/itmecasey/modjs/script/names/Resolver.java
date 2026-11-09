package com.itmecasey.modjs.script.names;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

/**
 * The shared part of {@link MappedJavaObject} and {@link MappedJavaClass}: when a script asks for a member by its official name
 * and the Java object has none (because the game only knows its own names), find it under the real-game name.
 */
final class Resolver {
    private Resolver() {
    }

    /**
     * @param raw reads a member of the underlying Rhino wrapper by exact name, returning {@link Scriptable#NOT_FOUND} when absent
     * @param type the Java class whose members are read
     * @param isStatic true for class objects, where beans (getFoo as foo) do not apply
     */
    static Object find(NameMap map, Class<?> type, String name, Scriptable start, BiFunction<String, Scriptable, Object> raw, boolean isStatic) {
        List<String> fieldNames = map.fieldNames(type, name);
        for (String srg : fieldNames) {
            Object value = raw.apply(srg, start);
            if (value != Scriptable.NOT_FOUND) {
                return value;
            }
        }
        List<String> methodNames = map.methodNames(type, name);
        Object method = methods(type, methodNames, start, raw);
        if (method != Scriptable.NOT_FOUND) {
            return method;
        }
        if (!isStatic) {
            // A script may write player.health for getHealth(), or isAlive for isAlive(); Rhino only knows that for names it can see.
            String bean = beanGetter(name);
            if (bean != null) {
                Object result = callGetter(map, type, bean, start, raw);
                if (result != Scriptable.NOT_FOUND) {
                    return result;
                }
            }
            String is = "is" + capitalize(name);
            Object result = callGetter(map, type, is, start, raw);
            if (result != Scriptable.NOT_FOUND) {
                return result;
            }
        }
        return Scriptable.NOT_FOUND;
    }

    private static String beanGetter(String name) {
        return name.isEmpty() ? null : "get" + capitalize(name);
    }

    private static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Object callGetter(NameMap map, Class<?> type, String getter, Scriptable start, BiFunction<String, Scriptable, Object> raw) {
        Object method = methods(type, map.methodNames(type, getter), start, raw);
        if (!(method instanceof Function function)) {
            return Scriptable.NOT_FOUND;
        }
        Context cx = Context.getCurrentContext();
        try {
            return function.call(cx, org.mozilla.javascript.ScriptableObject.getTopLevelScope(start), start, new Object[0]);
        } catch (EvaluatorException e) {
            if (isNoSuchMethod(e)) {
                return Scriptable.NOT_FOUND;
            }
            throw e;
        }
    }

    /** One function for every real-game name behind an official method name. */
    private static Object methods(Class<?> type, List<String> names, Scriptable start, BiFunction<String, Scriptable, Object> raw) {
        List<Function> functions = new ArrayList<>(names.size());
        List<List<Class<?>[]>> signatures = new ArrayList<>(names.size());
        for (String srg : names) {
            Object value = raw.apply(srg, start);
            if (value instanceof Function function) {
                functions.add(function);
                List<Class<?>[]> shapes = new ArrayList<>();
                for (java.lang.reflect.Method m : type.getMethods()) {
                    if (m.getName().equals(srg)) {
                        shapes.add(m.getParameterTypes());
                    }
                }
                signatures.add(shapes);
            }
        }
        if (functions.isEmpty()) {
            return Scriptable.NOT_FOUND;
        }
        return functions.size() == 1 ? functions.get(0) : new Overloads(functions, signatures);
    }

    static boolean isNoSuchMethod(EvaluatorException e) {
        if (e instanceof org.mozilla.javascript.WrappedException) {
            return false;
        }
        String text = e.details() == null ? "" : e.details();
        return text.contains("Can't find method") || text.contains("Can't find") ;
    }

    /**
     * Overloads that had different real-game names. Rhino would pick the best overload of one name by how well the arguments convert;
     * with one name per overload that choice has to be made here, or the first name would swallow every call (a number converts to a
     * string, so literal(5) would reach literal(String)).
     */
    private static final class Overloads extends BaseFunction {
        private final List<Function> candidates;
        private final List<List<Class<?>[]>> signatures;

        Overloads(List<Function> candidates, List<List<Class<?>[]>> signatures) {
            this.candidates = candidates;
            this.signatures = signatures;
        }

        @Override
        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            Integer[] order = new Integer[candidates.size()];
            int[] cost = new int[candidates.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
                int best = Integer.MAX_VALUE;
                for (Class<?>[] shape : signatures.get(i)) {
                    best = Math.min(best, cost(shape, args));
                }
                cost[i] = best;
            }
            java.util.Arrays.sort(order, (a, b) -> Integer.compare(cost[a], cost[b]));
            EvaluatorException last = null;
            for (int index : order) {
                try {
                    return candidates.get(index).call(cx, scope, thisObj, args);
                } catch (EvaluatorException e) {
                    if (!isNoSuchMethod(e)) {
                        throw e;
                    }
                    last = e;
                }
            }
            throw last;
        }

        private static int cost(Class<?>[] shape, Object[] args) {
            if (shape.length != args.length) {
                return 100_000;
            }
            int total = 0;
            for (int i = 0; i < shape.length; i++) {
                total += score(args[i], shape[i]);
            }
            return total;
        }

        private static int score(Object raw, Class<?> p) {
            Object a = raw instanceof org.mozilla.javascript.Wrapper w ? w.unwrap() : raw;
            if (a == null || a instanceof org.mozilla.javascript.Undefined) {
                return p.isPrimitive() ? 10_000 : 2;
            }
            if (p == Object.class) {
                return 3;
            }
            if (a instanceof Number) {
                if (p.isPrimitive() ? p != boolean.class && p != char.class : Number.class.isAssignableFrom(p)) {
                    return 1;
                }
                return p == String.class || p == CharSequence.class ? 8 : (org.mozilla.javascript.NativeJavaObject.canConvert(raw, p) ? 6 : 10_000);
            }
            if (a instanceof CharSequence) {
                if (p == String.class || p == CharSequence.class) {
                    return 1;
                }
                return org.mozilla.javascript.NativeJavaObject.canConvert(raw, p) ? 6 : 10_000;
            }
            if (a instanceof Boolean) {
                if (p == boolean.class || p == Boolean.class) {
                    return 1;
                }
                return p == String.class ? 8 : 10_000;
            }
            if (p.isInstance(a)) {
                return 1;
            }
            return org.mozilla.javascript.NativeJavaObject.canConvert(raw, p) ? 6 : 10_000;
        }

        @Override
        public String getFunctionName() {
            return candidates.get(0).getClassName();
        }
    }
}
