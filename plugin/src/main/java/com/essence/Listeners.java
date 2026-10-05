package com.essence;

import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

/**
 * Wires items to gameplay: awakening stones, ability tokens, and every
 * passive / on-hit / on-kill / on-hurt hook the abilities declare.
 */
public final class Listeners implements Listener {

    private final EssencePlugin plugin;

    public Listeners(EssencePlugin plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------- join / quit */

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Profile profile = plugin.profile(player);
        plugin.applyPassives(player, profile);
        int n = profile.abilities.size();
        if (n > 0) {
            plugin.actionBar(player, "&5" + n + " abilities awakened");
        } else {
            plugin.actionBar(player, "&7Hold an Awakening Stone and an Essence, then right-click.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.familiars().dismiss(event.getPlayer());
    }

    /* ------------------------------------------- awakening stone + tokens */

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        if (held == null) {
            return;
        }
        String kind = plugin.items().kindOf(held);
        if (kind == null) {
            return;
        }
        event.setCancelled(true);

        switch (kind) {
            case "stone" -> plugin.awaken(player, plugin.items().idOf(held));
            case "token" -> plugin.useAbility(player, plugin.items().idOf(held));
            case "essence" -> plugin.actionBar(player,
                    "&7Hold an Awakening Stone in your other hand to awaken.");
            default -> { }
        }
    }

    /* ------------------------------------------------------- combat hooks */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Profile profile = plugin.profile(player);
        if (profile.abilities.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        double extra = 0;

        for (String id : profile.abilities) {
            Content.Ability a = plugin.content().abilities.get(id);
            if (a == null) {
                continue;
            }
            // on-hit passives
            Map<String, Object> hit = a.onHit();
            if (hit != null) {
                if (hit.containsKey("lifesteal")) {
                    plugin.engine().heal(player, Content.d(hit, "lifesteal", 0.15)
                            * event.getDamage());
                }
                if (hit.containsKey("low_health_bonus")) {
                    double max = Engine.maxHealth(victim);
                    if (victim.getHealth() / max < 0.5) {
                        extra += Content.d(hit, "low_health_bonus", 0);
                    }
                }
                if (hit.containsKey("execute_threshold")) {
                    double max = Engine.maxHealth(victim);
                    if (victim.getHealth() / max <= Content.d(hit, "execute_threshold", 0.3)) {
                        extra += Content.d(hit, "execute_bonus", 0);
                    }
                }
                if (hit.containsKey("night_bonus")) {
                    long time = player.getWorld().getTime();
                    boolean night = time > 13000 && time < 23000;
                    if (night || player.getLocation().getBlock().getLightLevel() < 6) {
                        extra += Content.d(hit, "night_bonus", 0);
                    }
                }
            }
            // mark multiplier
            Long until = plugin.marks().get(victim.getUniqueId());
            if (until != null && until > now) {
                extra += event.getDamage() * 0.5;
            }
        }
        if (extra > 0) {
            event.setDamage(event.getDamage() + extra);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0),
                    6, 0.3, 0.3, 0.3, 0.05);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        Profile profile = plugin.profile(killer);
        for (String id : profile.abilities) {
            Content.Ability a = plugin.content().abilities.get(id);
            if (a == null || a.onKill() == null) {
                continue;
            }
            if (a.onKill().containsKey("heal")) {
                plugin.engine().heal(killer, Content.d(a.onKill(), "heal", 4));
                killer.getWorld().spawnParticle(Particle.HEART,
                        killer.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Profile profile = plugin.profile(player);
        for (String id : profile.abilities) {
            Content.Ability a = plugin.content().abilities.get(id);
            if (a == null || a.onHurt() == null) {
                continue;
            }
            if (a.onHurt().containsKey("flat_reduction")) {
                event.setDamage(Math.max(0,
                        event.getDamage() - Content.d(a.onHurt(), "flat_reduction", 0)));
            }
            if (a.onHurt().containsKey("cancel_chance") && player.isSneaking()) {
                if (Math.random() < Content.d(a.onHurt(), "cancel_chance", 0)) {
                    event.setCancelled(true);
                    player.getWorld().spawnParticle(Particle.SMOKE,
                            player.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.02);
                    plugin.actionBar(player, "&5Shadow Meld - attack avoided");
                }
            }
        }
        // doom confluence: passive damage reduction while it burns
        if (profile.doom) {
            event.setDamage(event.getDamage() * 0.95);
        }
    }

    /* --------------------------------------------------- auras + utilities */

    /** Called every 20 ticks. */
    public void auraTick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Profile profile = plugin.profile(player);
            if (profile.abilities.isEmpty()) {
                continue;
            }
            for (String id : profile.abilities) {
                Content.Ability a = plugin.content().abilities.get(id);
                if (a == null) {
                    continue;
                }
                if (a.kind().equals("aura") && a.effects() != null) {
                    applyAura(player, a);
                }
                if (a.kind().equals("utility") && a.payload() != null) {
                    applyUtility(player, a);
                }
            }
        }
    }

    private void applyAura(Player player, Content.Ability a) {
        double radius = a.radius();
        if (a.particles() != null) {
            Particle par = Engine.particle(a.particles());
            player.getWorld().spawnParticle(par, player.getLocation().add(0, 1, 0),
                    8, radius / 3, 0.4, radius / 3, 0.01);
        }
        for (Map<String, Object> e : a.effects()) {
            String target = Content.s(e, "target", "enemy");
            PotionEffectType type = Engine.effect(Content.s(e, "effect", "SLOWNESS"));
            int ticks = Content.i(e, "ticks", 60);
            int amp = Content.i(e, "amp", 0);
            if (target.equals("self")) {
                player.addPotionEffect(new PotionEffect(type, ticks, amp, true, false));
            } else if (target.equals("ally")) {
                player.addPotionEffect(new PotionEffect(type, ticks, amp, true, false));
                for (org.bukkit.entity.Entity near :
                        player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
                    if (near instanceof Player ally && !ally.equals(player)) {
                        ally.addPotionEffect(new PotionEffect(type, ticks, amp, true, false));
                    }
                }
            } else {
                for (LivingEntity enemy : plugin.engine().enemiesNear(player, radius)) {
                    enemy.addPotionEffect(new PotionEffect(type, ticks, amp, true, false));
                }
            }
        }
    }

    private void applyUtility(Player player, Content.Ability a) {
        Map<String, Object> p = a.payload();
        String type = Content.s(p, "type", "");
        switch (type) {
            case "perm_effect" -> player.addPotionEffect(new PotionEffect(
                    Engine.effect(Content.s(p, "effect", "NIGHT_VISION")),
                    120, 0, true, false));
            case "glow_enemies" -> {
                double radius = Content.d(p, "radius", 16);
                for (LivingEntity e : plugin.engine().enemiesNear(player, radius)) {
                    if (Engine.isHostile(e)) {
                        e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, true, false));
                    }
                }
            }
            case "no_hunger" -> {
                if (player.getFoodLevel() < 20) {
                    player.setFoodLevel(20);
                }
                if (player.getSaturation() < 5) {
                    player.setSaturation(5);
                }
            }
            case "attr" -> {
                // attributes are applied once on awaken, not per tick
            }
            default -> { }
        }
    }

    /** Damage-over-time storms, called every 10 ticks. */
    public void stormTick() {
        java.util.Iterator<Engine.Storm> it = plugin.storms().iterator();
        while (it.hasNext()) {
            Engine.Storm storm = it.next();
            storm.ticksLeft -= 10;
            Player owner = storm.owner;
            if (storm.ticksLeft <= 0 || owner == null || !owner.isOnline()) {
                it.remove();
                continue;
            }
            for (int i = 0; i < 20; i++) {
                double a = Math.random() * Math.PI * 2;
                double r = Math.random() * storm.radius;
                var loc = storm.centre.clone().add(Math.cos(a) * r, Math.random() * 2, Math.sin(a) * r);
                owner.getWorld().spawnParticle(storm.particle, loc, 1, 0, 0, 0, 0);
            }
            for (LivingEntity e : plugin.engine().enemiesNear(owner, storm.radius)) {
                if (e.getLocation().distanceSquared(storm.centre) <= storm.radius * storm.radius) {
                    e.damage(storm.damage, owner);
                }
            }
        }
    }
}
