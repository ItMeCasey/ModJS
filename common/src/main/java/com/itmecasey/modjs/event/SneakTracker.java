package com.itmecasey.modjs.event;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns the per-tick "is this player crouching" state into start and stop moments for {@code PlayerEvents.sneakStarted} and
 * {@code PlayerEvents.sneakStopped}. Only players who are sneaking are remembered, so the set stays tiny.
 */
public final class SneakTracker {
    private static final Set<UUID> SNEAKING = ConcurrentHashMap.newKeySet();

    private SneakTracker() {
    }

    /** Records the player's state this tick: 1 when they just started sneaking, -1 when they just stopped, 0 when nothing changed. */
    public static int update(UUID player, boolean sneaking) {
        if (sneaking) {
            return SNEAKING.add(player) ? 1 : 0;
        }
        return SNEAKING.remove(player) ? -1 : 0;
    }

    public static void clear() {
        SNEAKING.clear();
    }
}
