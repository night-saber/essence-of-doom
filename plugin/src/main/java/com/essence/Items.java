package com.essence;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Builds the essence, stone and ability items. */
public final class Items {

    private final EssencePlugin plugin;
    public final NamespacedKey kindKey;    // "essence" | "stone" | "token"
    public final NamespacedKey idKey;      // essence id / stone id / ability id

    public Items(EssencePlugin plugin) {
        this.plugin = plugin;
        this.kindKey = plugin.key("kind");
        this.idKey = plugin.key("id");
    }

    private static Component plain(String text, NamedTextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }

    private static NamedTextColor colourOf(String hex) {
        try {
            return NamedTextColor.nearestTo(net.kyori.adventure.text.format.TextColor
                    .fromHexString(hex));
        } catch (Exception e) {
            return NamedTextColor.LIGHT_PURPLE;
        }
    }

    /** An essence vial - the physical form of a dark / sin / blood / doom essence. */
    public ItemStack essenceItem(Content.Essence e) {
        ItemStack stack = new ItemStack(Material.POTION);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plain(e.name(), colourOf(e.colour()))
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(plain("Essence", NamedTextColor.DARK_GRAY));
        lore.add(plain(e.hint(), NamedTextColor.GRAY));
        if (e.confluence()) {
            lore.add(Component.empty());
            lore.add(plain("CONFLUENCE - the union of three.", NamedTextColor.LIGHT_PURPLE));
            lore.add(plain("Awakening with this grants its own power.", NamedTextColor.DARK_PURPLE));
        }
        lore.add(Component.empty());
        lore.add(plain("Right-click an Awakening Stone while holding", NamedTextColor.DARK_GRAY));
        lore.add(plain("your essences to awaken an ability.", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, "essence");
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, e.id());
        stack.setItemMeta(meta);
        return stack;
    }

    /** An awakening stone. */
    public ItemStack stoneItem(Content.Stone s) {
        ItemStack stack = new ItemStack(switch (s.rarity()) {
            case "legendary" -> Material.NETHER_STAR;
            case "epic" -> Material.AMETHYST_SHARD;
            case "rare" -> Material.ECHO_SHARD;
            default -> Material.QUARTZ;
        });
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plain(s.name(), colourOf(s.colour()))
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(plain(s.rarity().toUpperCase() + " AWAKENING STONE", NamedTextColor.DARK_GRAY));
        lore.add(plain(s.desc(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(plain("Hold this and right-click with an essence in hand", NamedTextColor.DARK_GRAY));
        lore.add(plain("to awaken one ability from its pool.", NamedTextColor.DARK_GRAY));
        lore.add(plain("Holding several essences awakens them all at once.", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, "stone");
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, s.id());
        stack.setItemMeta(meta);
        return stack;
    }

    /** The token handed to the player for each awakened ability. */
    public ItemStack abilityToken(Content.Ability a, String essenceId, String stoneName) {
        Material mat = switch (a.kind()) {
            case "aura" -> Material.BEACON;
            case "familiar" -> Material.SOUL_LANTERN;
            case "passive" -> Material.SHIELD;
            case "utility" -> Material.ENDER_EYE;
            default -> Material.BLAZE_ROD;
        };
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plain(a.name(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(plain(a.kind().toUpperCase() + " ABILITY", NamedTextColor.DARK_GRAY));
        lore.add(plain("Awakened from the " + essenceId + " essence", NamedTextColor.GRAY));
        lore.add(plain("via " + stoneName, NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(plain(a.desc(), NamedTextColor.GRAY));
        if (a.kind().equals("active")) {
            lore.add(Component.empty());
            lore.add(plain("Right-click to use. Cooldown " + a.cooldown() + "s",
                    NamedTextColor.DARK_GRAY));
        } else if (a.kind().equals("aura")) {
            lore.add(Component.empty());
            lore.add(plain("Always active while awakened.", NamedTextColor.DARK_GRAY));
        } else if (a.kind().equals("familiar")) {
            lore.add(Component.empty());
            lore.add(plain("Right-click to summon. Right-click again to dismiss.",
                    NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Component.empty());
            lore.add(plain("Always active while awakened.", NamedTextColor.DARK_GRAY));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, "token");
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, a.id());
        stack.setItemMeta(meta);
        return stack;
    }

    public String kindOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(kindKey, PersistentDataType.STRING);
    }

    public String idOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(idKey, PersistentDataType.STRING);
    }
}
