package com.itmecasey.modjs.script;

import org.mozilla.javascript.NativeJavaClass;
import org.mozilla.javascript.ScriptableObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes common Minecraft and JDK classes as script globals (for example {@code Blocks.STONE}, {@code new Vec3(0, 1, 0)}).
 * Each global lists candidate class names so one table serves every Minecraft version; names that do not exist are skipped.
 */
final class ClassGlobals {
    private static final Map<String, String[]> TABLE = new LinkedHashMap<>();

    static {
        add("UUID", "java.util.UUID");
        add("JavaMath", "java.lang.Math");
        add("Blocks", "net.minecraft.world.level.block.Blocks");
        add("Items", "net.minecraft.world.item.Items");
        add("Fluids", "net.minecraft.world.level.material.Fluids");
        add("BlockPos", "net.minecraft.core.BlockPos");
        add("Vec3", "net.minecraft.world.phys.Vec3");
        add("AABB", "net.minecraft.world.phys.AABB");
        add("Direction", "net.minecraft.core.Direction");
        add("Facing", "net.minecraft.core.Direction");
        add("ResourceLocation", "net.minecraft.resources.ResourceLocation", "net.minecraft.resources.Identifier");
        add("EntityType", "net.minecraft.world.entity.EntityType");
        add("MobEffects", "net.minecraft.world.effect.MobEffects");
        add("MobEffectInstance", "net.minecraft.world.effect.MobEffectInstance");
        add("SoundEvents", "net.minecraft.sounds.SoundEvents");
        add("Mth", "net.minecraft.util.Mth");
        add("InteractionHand", "net.minecraft.world.InteractionHand");
        add("GameType", "net.minecraft.world.level.GameType");
    }

    private ClassGlobals() {
    }

    private static void add(String name, String... classNames) {
        TABLE.put(name, classNames);
    }

    /** Resolves one global on first read so classes that touch client-only code are never loaded on a server. */
    public static final class Lazy {
        private final String[] classNames;
        private Object value;
        private boolean resolved;

        Lazy(String[] classNames) {
            this.classNames = classNames;
        }

        public Object get(ScriptableObject scope) {
            if (!resolved) {
                resolved = true;
                for (String className : classNames) {
                    try {
                        value = com.itmecasey.modjs.script.names.NameMap.classObject(scope, Class.forName(className, false, ClassGlobals.class.getClassLoader()));
                        break;
                    } catch (ClassNotFoundException | LinkageError ignored) {
                        // try the next candidate
                    }
                }
            }
            return value == null ? org.mozilla.javascript.Undefined.instance : value;
        }
    }

    static void install(ScriptableObject scope) {
        try {
            java.lang.reflect.Method getter = Lazy.class.getMethod("get", ScriptableObject.class);
            for (Map.Entry<String, String[]> entry : TABLE.entrySet()) {
                scope.defineProperty(entry.getKey(), new Lazy(entry.getValue()), getter, null, ScriptableObject.PERMANENT);
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
