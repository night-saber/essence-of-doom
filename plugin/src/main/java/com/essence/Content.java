package com.essence;

import java.util.List;
import java.util.Map;

/** Parsed content from essences.json: essences, abilities and awakening stones. */
public final class Content {

    /** One essence (dark, sin, blood, doom). */
    public record Essence(String id, String name, String colour, String hint,
                          boolean confluence) {
    }

    /** One awakening ability. */
    public record Ability(String id, String name, String kind, String desc, String essence,
                          int cooldown, Map<String, Object> payload,
                          Map<String, Object> attrs, Map<String, Object> onHit,
                          Map<String, Object> onKill, Map<String, Object> onHurt,
                          int radius, List<Map<String, Object>> effects,
                          String particles, String mob, int count,
                          double speed, double health, double damage) {
    }

    /** One awakening stone: a pool of abilities, one per essence. */
    public record Stone(String id, String name, String colour, String rarity,
                        String desc, List<String> pool) {
    }

    public final Map<String, Essence> essences;
    public final Map<String, Ability> abilities;
    public final Map<String, Stone> stones;

    public Content(Map<String, Essence> essences, Map<String, Ability> abilities,
                   Map<String, Stone> stones) {
        this.essences = essences;
        this.abilities = abilities;
        this.stones = stones;
    }

    public static double d(Map<String, Object> m, String k, double fallback) {
        if (m == null) {
            return fallback;
        }
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    public static int i(Map<String, Object> m, String k, int fallback) {
        if (m == null) {
            return fallback;
        }
        Object v = m.get(k);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    public static String s(Map<String, Object> m, String k, String fallback) {
        if (m == null) {
            return fallback;
        }
        Object v = m.get(k);
        return v == null ? fallback : String.valueOf(v);
    }
}
