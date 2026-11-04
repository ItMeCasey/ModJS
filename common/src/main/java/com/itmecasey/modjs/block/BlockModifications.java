package com.itmecasey.modjs.block;

import com.itmecasey.modjs.event.EventJS;
import org.mozilla.javascript.Context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Changes to existing blocks collected from {@code BlockEvents.modification}; platform modules apply them. */
public final class BlockModifications {
    private static final Map<String, Changes> CHANGES = new LinkedHashMap<>();

    private BlockModifications() {
    }

    /** What a script may change on an existing block. Null fields are left alone. */
    public static final class Changes {
        private Float destroySpeed;
        private Float explosionResistance;
        private Integer lightEmission;
        private Float friction;
        private Float speedFactor;
        private Float jumpFactor;
        private Boolean requiresTool;

        public Float getDestroySpeed() {
            return destroySpeed;
        }

        /** Hardness; -1 makes the block unbreakable. */
        public void setDestroySpeed(Float value) {
            this.destroySpeed = value;
        }

        public Float getExplosionResistance() {
            return explosionResistance;
        }

        public void setExplosionResistance(Float value) {
            if (value < 0) {
                throw Context.reportRuntimeError("explosionResistance cannot be negative, got " + value);
            }
            this.explosionResistance = value;
        }

        public Integer getLightEmission() {
            return lightEmission;
        }

        /** Light level 0 to 15. */
        public void setLightEmission(Integer value) {
            if (value < 0 || value > 15) {
                throw Context.reportRuntimeError("lightEmission must be between 0 and 15, got " + value);
            }
            this.lightEmission = value;
        }

        public Float getFriction() {
            return friction;
        }

        /** Slipperiness; 0.6 is normal, ice is 0.98. */
        public void setFriction(Float value) {
            this.friction = value;
        }

        public Float getSpeedFactor() {
            return speedFactor;
        }

        public void setSpeedFactor(Float value) {
            this.speedFactor = value;
        }

        public Float getJumpFactor() {
            return jumpFactor;
        }

        public void setJumpFactor(Float value) {
            this.jumpFactor = value;
        }

        public Boolean getRequiresTool() {
            return requiresTool;
        }

        public void setRequiresTool(Boolean value) {
            this.requiresTool = value;
        }
    }

    public static synchronized Map<String, Changes> all() {
        return new LinkedHashMap<>(CHANGES);
    }

    static synchronized Changes edit(String id) {
        return CHANGES.computeIfAbsent(id, k -> new Changes());
    }

    public static synchronized void clear() {
        CHANGES.clear();
    }

    /** The event passed to {@code BlockEvents.modification(event => ...)} in startup scripts. */
    public static final class ModificationEventJS extends EventJS {
        /** {@code event.modify('minecraft:stone', block => { block.destroySpeed = 9; })} */
        public void modify(String id, Consumer<Changes> change) {
            String full = id.contains(":") ? id : "minecraft:" + id;
            change.accept(edit(full));
        }

        public void modify(List<String> ids, Consumer<Changes> change) {
            for (String id : ids) {
                modify(id, change);
            }
        }
    }
}
