package com.essence;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Summoned familiars.
 *
 * A familiar is a real mob owned by the player: it is tagged so it never
 * despawns, never attacks its owner, and follows them. This keeps the
 * behaviour native - pathfinding, combat and damage all use vanilla AI.
 */
public final class Familiars {

    private final EssencePlugin plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey abilityKey;
    /** player -> their living familiars */
    private final Map<UUID, List<UUID>> active = new HashMap<>();

    public Familiars(EssencePlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = plugin.key("fam_owner");
        this.abilityKey = plugin.key("fam_ability");
    }

    public void summon(Player player, Content.Ability ability) {
        dismiss(player);

        int count = Math.max(1, ability.count());
        List<UUID> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Location at = player.getLocation().clone().add(
                    (Math.random() - 0.5) * 2.5, 0.2, (Math.random() - 0.5) * 2.5);
            Entity spawnedEntity = player.getWorld().spawnEntity(at, entityType(ability.mob()));
            if (!(spawnedEntity instanceof Mob mob)) {
                continue;
            }
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
            mob.setTarget(null);

            var hp = mob.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(ability.health());
                mob.setHealth(Math.min(ability.health(), hp.getValue()));
            }
            var speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(ability.speed());
            }
            var dmg = mob.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmg != null) {
                dmg.setBaseValue(ability.damage());
            }
            // never target the owner
            if (mob instanceof org.bukkit.entity.Tameable tameable) {
                tameable.setOwner(player);
                tameable.setTamed(true);
            }
            mob.customName(net.kyori.adventure.text.Component.text(ability.name(),
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
            mob.setCustomNameVisible(false);

            var pdc = mob.getPersistentDataContainer();
            pdc.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(abilityKey, PersistentDataType.STRING, ability.id());

            spawned.add(mob.getUniqueId());
        }
        active.put(player.getUniqueId(), spawned);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.2f);
        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0),
                40, 1, 1, 1, 0.05);
    }

    public void dismiss(Player player) {
        List<UUID> ids = active.remove(player.getUniqueId());
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            Entity e = plugin.getServer().getEntity(id);
            if (e != null && e.isValid()) {
                e.getWorld().spawnParticle(Particle.SOUL, e.getLocation().add(0, 0.5, 0),
                        15, 0.3, 0.3, 0.3, 0.02);
                e.remove();
            }
        }
    }

    public boolean hasFamiliars(Player player) {
        List<UUID> ids = active.get(player.getUniqueId());
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return ids.stream().anyMatch(id -> {
            Entity e = plugin.getServer().getEntity(id);
            return e != null && e.isValid();
        });
    }

    /** Called on a timer: keep familiars near their owner and pointed at enemies. */
    public void tick() {
        for (Map.Entry<UUID, List<UUID>> entry : new HashMap<>(active).entrySet()) {
            Player owner = plugin.getServer().getPlayer(entry.getKey());
            if (owner == null || !owner.isOnline()) {
                continue;
            }
            for (UUID id : entry.getValue()) {
                Entity e = plugin.getServer().getEntity(id);
                if (!(e instanceof Mob mob) || !mob.isValid()) {
                    continue;
                }
                String tag = mob.getPersistentDataContainer()
                        .get(ownerKey, PersistentDataType.STRING);
                if (tag == null || !tag.equals(owner.getUniqueId().toString())) {
                    mob.remove();     // not ours any more
                    continue;
                }
                double dist = mob.getLocation().distance(owner.getLocation());
                if (dist > 22) {
                    mob.teleport(owner.getLocation().add(
                            (Math.random() - 0.5) * 2, 0, (Math.random() - 0.5) * 2));
                }
                // find a target if idle
                if (mob.getTarget() == null || !mob.getTarget().isValid()) {
                    LivingEntity best = owner.getWorld()
                            .getNearbyEntities(owner.getLocation(), 14, 8, 14).stream()
                            .filter(x -> x instanceof LivingEntity)
                            .map(x -> (LivingEntity) x)
                            .filter(x -> !(x instanceof Player))
                            .filter(x -> !x.getUniqueId().equals(owner.getUniqueId()))
                            .filter(LivingEntity::isValid)
                            .min(Comparator.comparingDouble(x -> x.getLocation().distanceSquared(mob.getLocation())))
                            .orElse(null);
                    if (best != null) {
                        mob.setTarget(best);
                    }
                }
                if (Math.random() < 0.25) {
                    mob.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                            mob.getLocation().add(0, 1, 0), 1, 0.15, 0.15, 0.15, 0);
                }
            }
        }
    }

    private static org.bukkit.entity.EntityType entityType(String name) {
        try {
            return org.bukkit.entity.EntityType.valueOf(name);
        } catch (Exception e) {
            return org.bukkit.entity.EntityType.VEX;
        }
    }

    public Map<UUID, List<UUID>> active() {
        return active;
    }
}
