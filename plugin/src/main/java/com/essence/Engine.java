package com.essence;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Executes ability payloads.
 *
 * Every active ability in essences.json names a payload type; this class is the
 * only place that knows how to perform one. Adding a new ability is therefore
 * a data change, not a code change - unless it needs a genuinely new effect.
 */
public final class Engine {

    private final EssencePlugin plugin;

    public Engine(EssencePlugin plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------ helpers */

    public static Particle particle(String name) {
        try {
            return Particle.valueOf(name);
        } catch (Exception e) {
            return Particle.SOUL;
        }
    }

    public static PotionEffectType effect(String name) {
        PotionEffectType t = PotionEffectType.getByName(name);
        return t != null ? t : PotionEffectType.SLOWNESS;
    }

    public List<LivingEntity> enemiesNear(Player player, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
            if (e instanceof LivingEntity le && !(e instanceof Player) && e.isValid()) {
                out.add(le);
            }
        }
        out.sort(Comparator.comparingDouble(a -> a.getLocation().distanceSquared(player.getLocation())));
        return out;
    }

    public LivingEntity targetOf(Player player, double range) {
        RayTraceResult hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), range,
                0.6, e -> e instanceof LivingEntity && !(e instanceof Player));
        if (hit != null && hit.getHitEntity() instanceof LivingEntity le) {
            return le;
        }
        List<LivingEntity> near = enemiesNear(player, range);
        return near.isEmpty() ? null : near.get(0);
    }

    private void burst(Player player, Location at, Particle p, int count) {
        player.getWorld().spawnParticle(p, at, count, 0.6, 0.6, 0.6, 0.02);
    }

    private void ring(Player player, Particle p, double radius, int points) {
        Location c = player.getLocation();
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 / points) * i;
            Location l = c.clone().add(Math.cos(a) * radius, 0.3, Math.sin(a) * radius);
            player.getWorld().spawnParticle(p, l, 1, 0, 0, 0, 0);
        }
    }

    private void damage(Player source, LivingEntity target, double amount) {
        if (target instanceof Player p && !plugin.config().allowPvp) {
            return;
        }
        target.damage(amount, source);
    }

    /* ------------------------------------------------------------ payloads */

    /** Runs one active ability. Returns a short result string for feedback. */
    public String runActive(Player player, Content.Ability ability) {
        Map<String, Object> p = ability.payload();
        if (p == null) {
            return null;
        }
        String type = Content.s(p, "type", "");
        switch (type) {
            case "dash" -> {
                double dist = Content.d(p, "distance", 8);
                Vector dir = player.getEyeLocation().getDirection().normalize().multiply(dist);
                dir.setY(Math.max(0.15, dir.getY() * 0.4));
                player.setVelocity(dir);
                burst(player, player.getLocation(), particle(Content.s(p, "particles", "SOUL")), 30);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
                return "dashed";
            }
            case "blink" -> {
                double dist = Content.d(p, "distance", 12);
                Vector dir = player.getEyeLocation().getDirection().normalize();
                Location from = player.getLocation().clone();
                Location to = from.clone().add(dir.multiply(dist));
                // never blink into a wall
                to = safeSpot(player, from, to);
                player.teleport(to);
                burst(player, from, Particle.SOUL, 25);
                burst(player, to, Particle.SOUL, 25);
                player.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                LivingEntity target = targetOf(player, 3);
                double dmg = Content.d(p, "damage", 0);
                if (target != null && dmg > 0) {
                    damage(player, target, dmg);
                    return "blinked and struck";
                }
                if (Content.d(p, "invisible", 0) > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                            (int) Content.d(p, "invisible", 100), 0, false, false));
                }
                return "blinked";
            }
            case "nova", "quake" -> {
                double radius = Content.d(p, "radius", 7);
                double dmg = Content.d(p, "damage", 10);
                double launch = Content.d(p, "launch", 0);
                double knock = Content.d(p, "knockback", 0);
                Particle par = particle(Content.s(p, "particles",
                        type.equals("quake") ? "EXPLOSION" : "SOUL_FIRE_FLAME"));
                ring(player, par, radius, 48);
                burst(player, player.getLocation().add(0, 1, 0), par, 60);
                for (LivingEntity e : enemiesNear(player, radius)) {
                    damage(player, e, dmg);
                    if (Content.s(p, "effect", "") != null) {
                        Object ef = p.get("effect");
                        if (ef instanceof List<?> list && list.size() >= 3) {
                            e.addPotionEffect(new PotionEffect(effect(String.valueOf(list.get(0))),
                                    ((Number) list.get(1)).intValue(), ((Number) list.get(2)).intValue()));
                        }
                    }
                    Vector push = e.getLocation().toVector().subtract(player.getLocation().toVector());
                    push.setY(0);
                    if (push.lengthSquared() > 0) {
                        push.normalize().multiply(knock);
                    }
                    push.setY(launch);
                    e.setVelocity(e.getVelocity().add(push));
                }
                player.getWorld().playSound(player.getLocation(),
                        type.equals("quake") ? Sound.ENTITY_GENERIC_EXPLODE : Sound.ENTITY_WITHER_SHOOT,
                        1f, type.equals("quake") ? 0.6f : 1.1f);
                return type;
            }
            case "heal" -> {
                heal(player, Content.d(p, "amount", 8));
                burst(player, player.getLocation().add(0, 1, 0), Particle.HEART, 20);
                return "healed";
            }
            case "blood_pact" -> {
                double cost = Content.d(p, "cost", 6);
                double heal = Content.d(p, "heal", 16);
                player.setHealth(Math.max(1.0, player.getHealth() - cost));
                heal(player, heal);
                burst(player, player.getLocation().add(0, 1, 0), Particle.HEART, 40);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.7f, 0.7f);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 1f, 0.8f);
                return "paid in blood";
            }
            case "siphon" -> {
                double radius = Content.d(p, "radius", 8);
                double dmg = Content.d(p, "damage", 6);
                double per = Content.d(p, "heal_per_target", 4);
                List<LivingEntity> targets = enemiesNear(player, radius);
                double total = 0;
                for (LivingEntity e : targets) {
                    damage(player, e, dmg);
                    // draw a line of particles from the victim to the caster
                    Vector dir = player.getLocation().toVector().subtract(e.getLocation().toVector());
                    double len = dir.length();
                    Vector step = dir.normalize().multiply(0.4);
                    Location at = e.getLocation().add(0, 1, 0).clone();
                    for (double d = 0; d < len; d += 0.4) {
                        player.getWorld().spawnParticle(Particle.DUST_PLUME, at, 1, 0, 0, 0, 0);
                        at.add(step);
                    }
                    total += per;
                }
                if (total > 0) {
                    heal(player, total);
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 0.8f, 1.4f);
                return "siphoned " + targets.size();
            }
            case "steal_buff" -> {
                double radius = Content.d(p, "radius", 7);
                Object ef = p.get("effect");
                for (LivingEntity e : enemiesNear(player, radius)) {
                    if (ef instanceof List<?> list && list.size() >= 3) {
                        e.addPotionEffect(new PotionEffect(effect(String.valueOf(list.get(0))),
                                ((Number) list.get(1)).intValue(), ((Number) list.get(2)).intValue()));
                    }
                    e.getWorld().spawnParticle(Particle.WITCH, e.getLocation().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0.02);
                }
                if (ef instanceof List<?> list && list.size() >= 3) {
                    player.addPotionEffect(new PotionEffect(effect(String.valueOf(list.get(0))),
                            ((Number) list.get(1)).intValue(), ((Number) list.get(2)).intValue()));
                }
                return "devoured";
            }
            case "fear" -> {
                double radius = Content.d(p, "radius", 8);
                Object ef = p.get("effect");
                for (LivingEntity e : enemiesNear(player, radius)) {
                    if (ef instanceof List<?> list && list.size() >= 3) {
                        e.addPotionEffect(new PotionEffect(effect(String.valueOf(list.get(0))),
                                ((Number) list.get(1)).intValue(), ((Number) list.get(2)).intValue()));
                    }
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 1));
                    // make mobs flee
                    if (e instanceof Mob mob) {
                        Vector away = mob.getLocation().toVector()
                                .subtract(player.getLocation().toVector());
                        if (away.lengthSquared() > 0) {
                            away.normalize().multiply(0.8).setY(0.3);
                            mob.setVelocity(mob.getVelocity().add(away));
                        }
                    }
                }
                ring(player, Particle.SCULK_SOUL, radius, 40);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.7f, 1.5f);
                return "terror";
            }
            case "shield" -> {
                double absorption = Content.d(p, "absorption", 16);
                int seconds = (int) Content.d(p, "seconds", 12);
                player.setAbsorptionAmount(player.getAbsorptionAmount() + absorption);
                burst(player, player.getLocation().add(0, 1, 0), Particle.END_ROD, 30);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.4f);
                return "shielded";
            }
            case "execute" -> {
                double range = Content.d(p, "range", 4);
                double threshold = Content.d(p, "threshold", 0.35);
                LivingEntity target = targetOf(player, range);
                if (target == null) {
                    return "no target";
                }
                double max = maxHealth(target);
                if (target.getHealth() / max <= threshold) {
                    target.setHealth(0);
                    burst(player, target.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 40);
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 1.6f);
                    return "executed";
                }
                damage(player, target, 4);
                return "not wounded enough";
            }
            case "mark" -> {
                double range = Content.d(p, "range", 16);
                LivingEntity target = targetOf(player, range);
                if (target == null) {
                    return "no target";
                }
                plugin.marks().put(target.getUniqueId(),
                        System.currentTimeMillis() + (long) (Content.d(p, "seconds", 12) * 1000));
                burst(player, target.getLocation().add(0, 1, 0), Particle.FLASH, 20);
                player.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
                return "marked";
            }
            case "storm" -> {
                plugin.storms().add(new Storm(player, Content.d(p, "radius", 8),
                        Content.d(p, "damage", 4), (int) Content.d(p, "duration", 8),
                        particle(Content.s(p, "particles", "DUST_PLUME"))));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.4f);
                return "storm called";
            }
            case "leap" -> {
                double power = Content.d(p, "power", 1.6);
                Vector v = player.getLocation().getDirection().multiply(0.5);
                v.setY(power);
                player.setVelocity(v);
                int sf = (int) Content.d(p, "slow_fall", 200);
                if (sf > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, sf, 0, false, false));
                }
                burst(player, player.getLocation(), Particle.PORTAL, 40);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.3f);
                return "leapt";
            }
            case "cleanse" -> {
                List<PotionEffectType> bad = List.of(
                        PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS,
                        PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA,
                        PotionEffectType.HUNGER, PotionEffectType.MINING_FATIGUE,
                        PotionEffectType.DARKNESS, PotionEffectType.LEVITATION,
                        PotionEffectType.INSTANT_DAMAGE, PotionEffectType.UNLUCK);
                int removed = 0;
                for (PotionEffectType t : bad) {
                    if (player.hasPotionEffect(t)) {
                        player.removePotionEffect(t);
                        removed++;
                    }
                }
                player.setFireTicks(0);
                burst(player, player.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 30);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
                return "cleansed " + removed;
            }
            case "chain_bolt" -> {
                double radius = Content.d(p, "radius", 8);
                int max = (int) Content.d(p, "targets", 5);
                double dmg = Content.d(p, "damage", 7);
                List<LivingEntity> targets = enemiesNear(player, radius);
                int n = 0;
                Location from = player.getEyeLocation().clone();
                for (LivingEntity e : targets) {
                    if (n >= max) {
                        break;
                    }
                    damage(player, e, dmg);
                    Vector dir = e.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
                    double len = dir.length();
                    Vector step = dir.normalize().multiply(0.3);
                    Location at = from.clone();
                    for (double d = 0; d < len; d += 0.3) {
                        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 1, 0, 0, 0, 0);
                        at.add(step);
                    }
                    from = e.getLocation().add(0, 1, 0).clone();
                    n++;
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7f, 1.7f);
                return "chained " + n;
            }
            case "lifesteal_burst" -> {
                double range = Content.d(p, "range", 12);
                double dmg = Content.d(p, "damage", 10);
                double heal = Content.d(p, "heal", 10);
                LivingEntity target = targetOf(player, range);
                if (target == null) {
                    return "no target";
                }
                damage(player, target, dmg);
                heal(player, heal);
                burst(player, target.getLocation().add(0, 1, 0), Particle.HEART, 20);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.8f, 0.8f);
                return "drained";
            }
            case "summon" -> {
                plugin.familiars().summon(player, ability);
                return "summoned";
            }
            default -> {
                return null;
            }
        }
    }

    private Location safeSpot(Player player, Location from, Location want) {
        // step back along the path until the spot is not inside a block
        Vector dir = want.toVector().subtract(from.toVector()).normalize();
        Location at = want.clone();
        for (int i = 0; i < 12; i++) {
            if (at.getBlock().isPassable() && at.clone().add(0, 1, 0).getBlock().isPassable()) {
                return at;
            }
            at.subtract(dir.multiply(0.5));
        }
        return from;
    }

    public static double maxHealth(LivingEntity e) {
        var inst = e.getAttribute(Attribute.MAX_HEALTH);
        return inst == null ? 20.0 : inst.getValue();
    }

    public void heal(Player player, double amount) {
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount));
    }

    /** A running damage-over-time field. */
    public static final class Storm {
        public final Player owner;
        public final double radius;
        public final double damage;
        public int ticksLeft;
        public final Particle particle;
        public final Location centre;

        public Storm(Player owner, double radius, double damage, int seconds, Particle particle) {
            this.owner = owner;
            this.radius = radius;
            this.damage = damage;
            this.ticksLeft = seconds * 20;
            this.particle = particle;
            this.centre = owner.getLocation().clone();
        }
    }

    /** True if the entity is a monster (used by utility glow). */
    public static boolean isHostile(Entity e) {
        return e instanceof Monster || e instanceof org.bukkit.entity.Slime
                || e instanceof org.bukkit.entity.Phantom;
    }
}
