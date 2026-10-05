package com.essence;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /essence - essences, awakening stones, abilities. */
public final class EssenceCommand implements CommandExecutor, TabCompleter {

    private final EssencePlugin plugin;

    public EssenceCommand(EssencePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "essences" -> listEssences(sender);
            case "stones" -> listStones(sender);
            case "abilities" -> listAbilities(sender);
            case "awaken" -> awaken(sender, args);
            case "stone" -> giveStone(sender, args);
            case "doom" -> giveDoom(sender);
            case "reset" -> reset(sender, args);
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(Component.text("Config reloaded.", NamedTextColor.GREEN));
            }
            default -> usage(sender, label);
        }
        return true;
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("EssenceOfDoom", NamedTextColor.DARK_PURPLE));
        sender.sendMessage(Component.text("/" + label + " essences", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " stones", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " abilities", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " stone <id> [player]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " doom [player]  - grants all three essences",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " awaken [player]  - awakens a starter stone",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " reset <player>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.GRAY));
    }

    private void listEssences(CommandSender sender) {
        sender.sendMessage(Component.text("Essences", NamedTextColor.DARK_PURPLE));
        for (Content.Essence e : plugin.content().essences.values()) {
            sender.sendMessage(Component.text("  " + e.name() + (e.confluence() ? "  [CONFLUENCE]" : ""),
                    e.confluence() ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY));
            sender.sendMessage(Component.text("    " + e.hint(), NamedTextColor.DARK_GRAY));
        }
    }

    private void listStones(CommandSender sender) {
        sender.sendMessage(Component.text("Awakening Stones (" + plugin.content().stones.size() + ")",
                NamedTextColor.DARK_PURPLE));
        for (Content.Stone s : plugin.content().stones.values()) {
            sender.sendMessage(Component.text("  " + s.id() + " - " + s.name()
                    + " (" + s.rarity() + ")", NamedTextColor.GRAY));
        }
    }

    private void listAbilities(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        Profile profile = plugin.profile(player);
        sender.sendMessage(Component.text("Your essences: "
                + (profile.essences.isEmpty() ? "none" : String.join(", ", profile.essences)),
                NamedTextColor.DARK_PURPLE));
        if (profile.doom) {
            sender.sendMessage(Component.text("DOOM CONFLUENCE AWAKENED", NamedTextColor.LIGHT_PURPLE));
        }
        if (profile.abilities.isEmpty()) {
            sender.sendMessage(Component.text("  No abilities yet.", NamedTextColor.GRAY));
            return;
        }
        long now = System.currentTimeMillis();
        for (String id : profile.abilities) {
            Content.Ability a = plugin.content().abilities.get(id);
            if (a == null) {
                continue;
            }
            String cd = a.kind().equals("active")
                    ? (profile.ready(id, now) ? "  ready" : "  " + profile.remaining(id, now) / 1000 + "s")
                    : "";
            sender.sendMessage(Component.text("  [" + a.kind() + "] " + a.name() + cd,
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text("      " + a.desc(), NamedTextColor.DARK_GRAY));
        }
    }

    private void awaken(CommandSender sender, String[] args) {
        Player target = resolve(sender, args, 1);
        if (target == null) {
            return;
        }
        if (!sender.hasPermission("essence.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        // give the three essences then awaken the first stone
        for (String id : List.of("dark", "sin", "blood")) {
            Content.Essence e = plugin.content().essences.get(id);
            if (e != null) {
                plugin.give(target, plugin.items().essenceItem(e));
            }
        }
        Content.Stone stone = plugin.content().stones.values().iterator().next();
        plugin.give(target, plugin.items().stoneItem(stone));
        sender.sendMessage(Component.text("Gave " + target.getName()
                + " the three essences and an " + stone.name() + ".", NamedTextColor.GREEN));
    }

    private void giveStone(CommandSender sender, String[] args) {
        if (!sender.hasPermission("essence.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /essence stone <id> [player]",
                    NamedTextColor.YELLOW));
            return;
        }
        Content.Stone stone = plugin.content().stones.get(args[1].toLowerCase(Locale.ROOT));
        if (stone == null) {
            sender.sendMessage(Component.text("Unknown stone. Try /essence stones",
                    NamedTextColor.RED));
            return;
        }
        Player target = resolve(sender, args, 2);
        if (target == null) {
            return;
        }
        plugin.give(target, plugin.items().stoneItem(stone));
        sender.sendMessage(Component.text("Gave " + target.getName() + " " + stone.name(),
                NamedTextColor.GREEN));
    }

    private void giveDoom(CommandSender sender) {
        if (!sender.hasPermission("essence.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        for (String id : List.of("dark", "sin", "blood")) {
            Content.Essence e = plugin.content().essences.get(id);
            if (e != null) {
                plugin.give(player, plugin.items().essenceItem(e));
            }
        }
        sender.sendMessage(Component.text("Hold all three and awaken a stone to gain Doom.",
                NamedTextColor.LIGHT_PURPLE));
    }

    private void reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("essence.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /essence reset <player>", NamedTextColor.YELLOW));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }
        plugin.reset(target);
        sender.sendMessage(Component.text("Reset " + target.getName() + "'s essences.",
                NamedTextColor.GREEN));
    }

    private Player resolve(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player p = plugin.getServer().getPlayerExact(args[index]);
            if (p == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            }
            return p;
        }
        if (sender instanceof Player p) {
            return p;
        }
        sender.sendMessage(Component.text("Specify a player.", NamedTextColor.RED));
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("essences", "stones", "abilities", "stone", "doom",
                    "awaken", "reset", "reload")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stone")) {
            for (String id : plugin.content().stones.keySet()) {
                if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(id);
                }
            }
        } else if (args.length == 2) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                out.add(p.getName());
            }
        }
        return out;
    }
}
