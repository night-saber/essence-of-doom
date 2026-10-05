package com.essence;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** EssenceOfDoom - essences, awakening stones and the Doom confluence. */
public final class EssencePlugin extends JavaPlugin {

    public static final class Settings {
        public boolean allowPvp = false;
        public boolean announceAwaken = true;
        public boolean doomRequiresAllThree = true;
        public int passiveRefreshSeconds = 5;
        public boolean grantStarterStone = true;
        public double abilityDamageMultiplier = 1.0;
    }

    private final Settings settings = new Settings();
    private Content content;
    private Items items;
    private Engine engine;
    private Familiars familiars;
    private Listeners listeners;

    private final Map<UUID, Profile> profiles = new HashMap<>();
    private final Map<UUID, Long> marks = new HashMap<>();
    private final List<Engine.Storm> storms = new ArrayList<>();
    private final Map<UUID, Map<String, Double>> appliedAttrs = new HashMap<>();

    private BukkitTask auraTask;
    private BukkitTask stormTask;
    private BukkitTask familiarTask;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        content = loadContent();
        if (content == null) {
            getLogger().severe("Could not load essences.json - disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        items = new Items(this);
        engine = new Engine(this);
        familiars = new Familiars(this);
        listeners = new Listeners(this);

        getServer().getPluginManager().registerEvents(listeners, this);

        var cmd = getCommand("essence");
        if (cmd != null) {
            cmd.setExecutor(new EssenceCommand(this));
            cmd.setTabCompleter(new EssenceCommand(this));
        }

        loadAllProfiles();

        auraTask = getServer().getScheduler().runTaskTimer(this, () -> listeners.auraTick(), 40L, 20L);
        stormTask = getServer().getScheduler().runTaskTimer(this, () -> listeners.stormTick(), 20L, 10L);
        familiarTask = getServer().getScheduler().runTaskTimer(this, () -> familiars.tick(), 60L, 40L);
        saveTask = getServer().getScheduler().runTaskTimer(this, this::saveAllProfiles, 600L, 6000L);

        getLogger().info("Loaded " + content.essences.size() + " essences, "
                + content.stones.size() + " awakening stones, "
                + content.abilities.size() + " abilities.");
        getLogger().info("EssenceOfDoom enabled.");
    }

    @Override
    public void onDisable() {
        if (auraTask != null) auraTask.cancel();
        if (stormTask != null) stormTask.cancel();
        if (familiarTask != null) familiarTask.cancel();
        if (saveTask != null) saveTask.cancel();
        saveAllProfiles();
        getLogger().info("EssenceOfDoom disabled.");
    }

    /* ------------------------------------------------------------- config */

    private void loadSettings() {
        FileConfiguration c = getConfig();
        settings.allowPvp = c.getBoolean("allow-pvp", false);
        settings.announceAwaken = c.getBoolean("announce-awakening", true);
        settings.doomRequiresAllThree = c.getBoolean("doom-requires-all-three", true);
        settings.passiveRefreshSeconds = Math.max(1, c.getInt("passive-refresh-seconds", 5));
        settings.grantStarterStone = c.getBoolean("grant-starter-stone", true);
        settings.abilityDamageMultiplier = c.getDouble("ability-damage-multiplier", 1.0);
    }

    public void reload() {
        reloadConfig();
        loadSettings();
    }

    public Settings config() {
        return settings;
    }

    /* ------------------------------------------------------------ content */

    private Content loadContent() {
        try (InputStream in = getResource("essences.json")) {
            if (in == null) {
                getLogger().severe("essences.json missing from the jar.");
                return null;
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> root = Json.parseObject(json);

            Map<String, Content.Essence> essences = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) root.get("essences")).entrySet()) {
                Map<String, Object> v = (Map<String, Object>) e.getValue();
                essences.put(e.getKey(), new Content.Essence(
                        e.getKey(),
                        Content.s(v, "name", e.getKey()),
                        Content.s(v, "colour", "#ffffff"),
                        Content.s(v, "hint", ""),
                        Boolean.TRUE.equals(v.get("confluence"))));
            }

            Map<String, Content.Ability> abilities = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) root.get("abilities")).entrySet()) {
                Map<String, Object> v = (Map<String, Object>) e.getValue();
                abilities.put(e.getKey(), new Content.Ability(
                        Content.s(v, "id", e.getKey()),
                        Content.s(v, "name", e.getKey()),
                        Content.s(v, "kind", "passive"),
                        Content.s(v, "desc", ""),
                        Content.s(v, "essence", "dark"),
                        Content.i(v, "cooldown", 10),
                        cast(v.get("payload")),
                        cast(v.get("attrs")),
                        cast(v.get("onHit")),
                        cast(v.get("onKill")),
                        cast(v.get("onHurt")),
                        Content.i(v, "radius", 8),
                        listOf(v.get("effects")),
                        Content.s(v, "particles", null),
                        Content.s(v, "mob", "VEX"),
                        Content.i(v, "count", 1),
                        Content.d(v, "speed", 0.35),
                        Content.d(v, "health", 14),
                        Content.d(v, "damage", 4)));
            }

            Map<String, Content.Stone> stones = new LinkedHashMap<>();
            for (Object o : (List<Object>) root.get("stones")) {
                Map<String, Object> v = (Map<String, Object>) o;
                List<String> pool = new ArrayList<>();
                for (Object a : (List<Object>) v.get("pool")) {
                    pool.add(String.valueOf(a));
                }
                stones.put(Content.s(v, "id", "stone"), new Content.Stone(
                        Content.s(v, "id", "stone"),
                        Content.s(v, "name", "Awakening Stone"),
                        Content.s(v, "colour", "#ffffff"),
                        Content.s(v, "rarity", "uncommon"),
                        Content.s(v, "desc", ""),
                        pool));
            }
            return new Content(essences, abilities, stones);
        } catch (Exception e) {
            getLogger().severe("Failed to parse essences.json: " + e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object x : list) {
                if (x instanceof Map) {
                    out.add((Map<String, Object>) x);
                }
            }
        }
        return out;
    }

    public Content content() {
        return content;
    }

    public Items items() {
        return items;
    }

    public Engine engine() {
        return engine;
    }

    public Familiars familiars() {
        return familiars;
    }

    public Map<UUID, Long> marks() {
        return marks;
    }

    public List<Engine.Storm> storms() {
        return storms;
    }

    /* ------------------------------------------------------------ profiles */

    public Profile profile(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), k -> {
            Profile p = loadProfile(player.getUniqueId());
            return p == null ? new Profile() : p;
        });
    }

    public void reset(Player player) {
        profiles.put(player.getUniqueId(), new Profile());
        appliedAttrs.remove(player.getUniqueId());
        familiars.dismiss(player);
        clearPassives(player);
        saveProfile(player.getUniqueId(), new Profile());
    }

    private void loadAllProfiles() {
        for (Player p : getServer().getOnlinePlayers()) {
            Profile profile = profile(p);
            applyPassives(p, profile);
        }
    }

    private Profile loadProfile(UUID id) {
        if (!getDataFolder().exists()) {
            return null;
        }
        java.io.File f = new java.io.File(getDataFolder(), "players/" + id + ".json");
        if (!f.isFile()) {
            return null;
        }
        try {
            Map<String, Object> root = Json.parseObject(
                    java.nio.file.Files.readString(f.toPath(), StandardCharsets.UTF_8));
            Profile p = new Profile();
            Object es = root.get("essences");
            if (es instanceof List<?> list) {
                for (Object e : list) {
                    p.essences.add(String.valueOf(e));
                }
            }
            Object ab = root.get("abilities");
            if (ab instanceof List<?> list) {
                for (Object a : list) {
                    p.abilities.add(String.valueOf(a));
                }
            }
            Object us = root.get("usedStones");
            if (us instanceof List<?> list) {
                for (Object u : list) {
                    p.usedStones.add(String.valueOf(u));
                }
            }
            p.doom = Boolean.TRUE.equals(root.get("doom"));
            return p;
        } catch (Exception e) {
            getLogger().warning("Could not read profile for " + id + ": " + e.getMessage());
            return null;
        }
    }

    private void saveProfile(UUID id, Profile p) {
        try {
            java.io.File dir = new java.io.File(getDataFolder(), "players");
            dir.mkdirs();
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append(" \"essences\": ").append(array(p.essences)).append(",\n");
            sb.append(" \"abilities\": ").append(array(p.abilities)).append(",\n");
            sb.append(" \"usedStones\": ").append(array(p.usedStones)).append(",\n");
            sb.append(" \"doom\": ").append(p.doom).append("\n");
            sb.append("}\n");
            java.nio.file.Files.writeString(
                    new java.io.File(dir, id + ".json").toPath(), sb.toString(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            getLogger().warning("Could not save profile for " + id + ": " + e.getMessage());
        }
    }

    private static String array(Iterable<String> items) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String s : items) {
            if (!first) {
                sb.append(", ");
            }
            sb.append('"').append(s.replace("\"", "")).append('"');
            first = false;
        }
        return sb.append("]").toString();
    }

    public void saveAllProfiles() {
        for (Map.Entry<UUID, Profile> e : profiles.entrySet()) {
            saveProfile(e.getKey(), e.getValue());
        }
    }

    /* ----------------------------------------------------------- awakening */

    /** Grants every essence the player is holding, then awakens the stone. */
    public void awaken(Player player, String stoneId) {
        Content.Stone stone = content.stones.get(stoneId);
        if (stone == null) {
            actionBar(player, "&cThat stone is not recognised.");
            return;
        }
        Profile profile = profile(player);

        // which essences does the player hold right now?
        List<String> held = essencesInInventory(player);
        if (held.isEmpty()) {
            actionBar(player, "&7You need an essence to awaken with.");
            return;
        }

        boolean doomHeld = held.contains("doom");

        List<String> gained = new ArrayList<>();
        List<ItemStack> granted = new ArrayList<>();

        for (String essenceId : held) {
            Content.Essence essence = content.essences.get(essenceId);
            if (essence == null) {
                continue;
            }
            // record the essence itself
            if (!profile.has(essenceId)) {
                profile.essences.add(essenceId);
            }
            // pick an ability from this stone's pool that matches the essence
            String abilityId = pickFromPool(stone, essenceId);
            if (abilityId == null || profile.knows(abilityId)) {
                continue;
            }
            Content.Ability ability = content.abilities.get(abilityId);
            if (ability == null) {
                continue;
            }
            profile.abilities.add(abilityId);
            gained.add(ability.name());
            granted.add(items.abilityToken(ability, essenceId, stone.name()));
        }

        // the confluence: holding all three grants Doom
        if (!profile.doom && profile.hasAllThree()) {
            profile.doom = true;
            gained.add("DOOM CONFLUENCE");
            actionBar(player, "&5&lTHE DOOM CONFLUENCE AWAKENS");
            player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1f, 0.7f);
            player.getWorld().spawnParticle(org.bukkit.Particle.SCULK_SOUL,
                    player.getLocation().add(0, 1, 0), 120, 1.2, 1.6, 1.2, 0.06);
            // the confluence itself grants its own power
            String doomAbility = pickFromPool(stone, "doom");
            if (doomAbility != null && !profile.knows(doomAbility)) {
                Content.Ability a = content.abilities.get(doomAbility);
                if (a != null) {
                    profile.abilities.add(doomAbility);
                    gained.add(a.name());
                    granted.add(items.abilityToken(a, "doom", stone.name()));
                }
            }
            getServer().broadcast(Component.text(player.getName()
                    + " has awakened the Doom Confluence.", NamedTextColor.DARK_PURPLE));
        } else if (doomHeld && !profile.doom && !profile.hasAllThree()) {
            actionBar(player, "&7Doom needs dark, sin and blood together.");
        }

        // consume the stone and the essences that were used
        if (!gained.isEmpty()) {
            profile.usedStones.add(stoneId);
            consumeHeld(player, held);
            consumeOne(player, held);
            for (ItemStack s : granted) {
                give(player, s);
            }
            player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
            player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING,
                    player.getLocation().add(0, 1, 0), 50, 0.6, 0.8, 0.6, 0.1);
            applyPassives(player, profile);
            for (String g : gained) {
                player.sendMessage(Component.text("  + " + g, NamedTextColor.LIGHT_PURPLE));
            }
            if (settings.announceAwaken) {
                actionBar(player, "&5Awakened: &d" + String.join("&7, &d", gained));
            }
        } else {
            actionBar(player, "&7That stone has nothing left to teach you.");
        }
    }

    /** Finds an ability in the stone's pool belonging to the given essence. */
    private String pickFromPool(Content.Stone stone, String essenceId) {
        List<String> candidates = new ArrayList<>();
        for (String id : stone.pool()) {
            Content.Ability a = content.abilities.get(id);
            if (a == null) {
                continue;
            }
            // the ability's flavour decides its essence
            if (essenceOf(a).equals(essenceId)) {
                candidates.add(id);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** Which essence an ability belongs to - read from the data, never guessed. */
    public String essenceOf(Content.Ability a) {
        return a.essence() == null ? "dark" : a.essence();
    }

    /* ------------------------------------------------------------- items */

    private List<String> essencesInInventory(Player player) {
        List<String> found = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) {
                continue;
            }
            if ("essence".equals(items.kindOf(stack))) {
                String id = items.idOf(stack);
                if (id != null && !found.contains(id)) {
                    found.add(id);
                }
            }
        }
        return found;
    }

    /** Removes one of each essence the player used. */
    private void consumeHeld(Player player, List<String> used) {
        ItemStack[] contents = player.getInventory().getContents();
        for (String essenceId : used) {
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack == null || !"essence".equals(items.kindOf(stack))) {
                    continue;
                }
                if (!essenceId.equals(items.idOf(stack))) {
                    continue;
                }
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                break;
            }
        }
    }

    /** Removes the stone itself (held item). */
    private void consumeOne(Player player, List<String> unused) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if ("stone".equals(items.kindOf(hand))) {
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            return;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if ("stone".equals(items.kindOf(off))) {
            if (off.getAmount() > 1) {
                off.setAmount(off.getAmount() - 1);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
        }
    }

    public void give(Player player, ItemStack stack) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
        for (ItemStack s : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), s);
        }
    }

    /* ----------------------------------------------------------- abilities */

    public void useAbility(Player player, String abilityId) {
        Content.Ability ability = content.abilities.get(abilityId);
        if (ability == null) {
            return;
        }
        Profile profile = profile(player);
        if (!profile.knows(abilityId)) {
            actionBar(player, "&7You have not awakened that ability.");
            return;
        }
        if (ability.kind().equals("familiar")) {
            if (familiars.hasFamiliars(player)) {
                familiars.dismiss(player);
                actionBar(player, "&7Familiars dismissed.");
            } else {
                familiars.summon(player, ability);
                actionBar(player, "&d" + ability.name() + " answers your call.");
            }
            return;
        }
        if (!ability.kind().equals("active")) {
            actionBar(player, "&7" + ability.name() + " is always active.");
            return;
        }
        long now = System.currentTimeMillis();
        if (!profile.ready(abilityId, now)) {
            long left = profile.remaining(abilityId, now) / 1000;
            actionBar(player, "&7" + ability.name() + " ready in " + left + "s");
            return;
        }
        String result = engine.runActive(player, ability);
        if (result == null) {
            actionBar(player, "&7Nothing happened.");
            return;
        }
        profile.trigger(abilityId, ability.cooldown(), now);
        actionBar(player, "&d" + ability.name() + " &7- " + result
                + " &8(" + ability.cooldown() + "s)");
    }

    /* ------------------------------------------------------------ passives */

    /** Applies attribute passives once, and clears any that no longer apply. */
    public void applyPassives(Player player, Profile profile) {
        Map<String, Double> want = new HashMap<>();
        for (String id : profile.abilities) {
            Content.Ability a = content.abilities.get(id);
            if (a == null || a.attrs() == null) {
                continue;
            }
            for (Map.Entry<String, Object> e : a.attrs().entrySet()) {
                if (e.getValue() instanceof Number n) {
                    want.merge(e.getKey(), n.doubleValue(), Double::sum);
                }
            }
        }
        for (String id : profile.abilities) {
            Content.Ability a = content.abilities.get(id);
            if (a != null && a.kind().equals("utility") && a.payload() != null
                    && "attr".equals(Content.s(a.payload(), "type", ""))) {
                want.merge(Content.s(a.payload(), "attr", "MAX_HEALTH"),
                        Content.d(a.payload(), "amount", 0), Double::sum);
            }
        }

        Map<String, Double> applied = appliedAttrs.computeIfAbsent(player.getUniqueId(),
                k -> new HashMap<>());
        // remove attributes we previously added but no longer want
        for (Map.Entry<String, Double> e : new HashMap<>(applied).entrySet()) {
            if (!want.containsKey(e.getKey())) {
                modify(player, e.getKey(), -e.getValue());
                applied.remove(e.getKey());
            }
        }
        // add or adjust
        for (Map.Entry<String, Double> e : want.entrySet()) {
            double have = applied.getOrDefault(e.getKey(), 0.0);
            double delta = e.getValue() - have;
            if (Math.abs(delta) > 0.0001) {
                modify(player, e.getKey(), delta);
                applied.put(e.getKey(), e.getValue());
            }
        }
    }

    private void modify(Player player, String attributeName, double amount) {
        Attribute attribute;
        try {
            attribute = Attribute.valueOf(attributeName);
        } catch (Exception e) {
            return;
        }
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        // a stable, per-player modifier key so re-applying never stacks
        var key = key("passive_" + attributeName.toLowerCase());
        for (var mod : inst.getModifiers()) {
            if (mod.getKey().equals(key)) {
                inst.removeModifier(mod);
                break;
            }
        }
        if (Math.abs(amount) > 0.0001) {
            inst.addModifier(new org.bukkit.attribute.AttributeModifier(key, amount,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void clearPassives(Player player) {
        Map<String, Double> applied = appliedAttrs.remove(player.getUniqueId());
        if (applied == null) {
            return;
        }
        for (Map.Entry<String, Double> e : applied.entrySet()) {
            modify(player, e.getKey(), -e.getValue());
        }
    }

    /* ------------------------------------------------------------- helper */

    public void actionBar(Player player, String legacy) {
        Component msg = LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
        player.sendActionBar(msg);
    }

    public org.bukkit.NamespacedKey key(String value) {
        return new org.bukkit.NamespacedKey(this, value);
    }
}
