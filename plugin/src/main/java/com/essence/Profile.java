package com.essence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A player's essence progression and awakened abilities. */
public final class Profile {

    /** Essences the player currently holds, in the order they were gained. */
    public final Set<String> essences = new LinkedHashSet<>();
    /** Ability ids, in the order they were awakened. */
    public final List<String> abilities = new ArrayList<>();
    /** Ability id -> epoch millis when it can be used again. */
    public final java.util.Map<String, Long> cooldowns = new java.util.HashMap<>();
    /** Stones already consumed, so they cannot be reused. */
    public final Set<String> usedStones = new LinkedHashSet<>();
    /** True once the doom confluence has been granted. */
    public boolean doom;

    public boolean has(String essence) {
        return essences.contains(essence);
    }

    public boolean knows(String abilityId) {
        return abilities.contains(abilityId);
    }

    /** Dark + sin + blood, all three. */
    public boolean hasAllThree() {
        return has("dark") && has("sin") && has("blood");
    }

    public boolean ready(String abilityId, long now) {
        Long until = cooldowns.get(abilityId);
        return until == null || until <= now;
    }

    public long remaining(String abilityId, long now) {
        Long until = cooldowns.get(abilityId);
        if (until == null) {
            return 0;
        }
        return Math.max(0, until - now);
    }

    public void trigger(String abilityId, int seconds, long now) {
        cooldowns.put(abilityId, now + seconds * 1000L);
    }
}
