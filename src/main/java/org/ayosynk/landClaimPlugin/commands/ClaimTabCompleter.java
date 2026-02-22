package org.ayosynk.landClaimPlugin.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.ayosynk.landClaimPlugin.LandClaimPlugin;
import org.ayosynk.landClaimPlugin.managers.HomeManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ClaimTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        String cmd = command.getName().toLowerCase();
        List<String> completions = new ArrayList<>();

        // Handle aliases
        if (cmd.equals("claim") || cmd.equals("c")) {
            if (args.length == 1) {
                completions.addAll(Arrays.asList(
                        "auto", "trust", "untrust", "unstuck",
                        "visible", "help", "reload", "admin", "trustlist", "info",
                        "visitor", "member", "list", "sethome", "delhome", "home", "homes"));
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("trust") ||
                        args[0].equalsIgnoreCase("untrust")) {
                    // Suggest online players (excluding self)
                    return getOnlinePlayerNames(player, args[1]);
                } else if (args[0].equalsIgnoreCase("visible")) {
                    completions.addAll(Arrays.asList("always", "off"));
                } else if (args[0].equalsIgnoreCase("admin")) {
                    completions.addAll(Arrays.asList("unclaim", "unclaimall"));
                } else if (args[0].equalsIgnoreCase("visitor")) {
                    completions.add("menu");
                } else if (args[0].equalsIgnoreCase("member")) {
                    completions.addAll(Arrays.asList("add", "remove"));
                }
            } else if (args.length == 3) {
                if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("unclaimall")) {
                    return getOnlinePlayerNames(player, args[2]);
                } else if (args[0].equalsIgnoreCase("member")) {
                    return getOnlinePlayerNames(player, args[2]);
                }
            }
            // Home name completions for home/delhome
            if (args.length == 2) {
                if (args[0].equalsIgnoreCase("home") || args[0].equalsIgnoreCase("delhome")) {
                    return getHomeNames(player, args[1]);
                }
            }
        } else if (cmd.equals("unclaim") || cmd.equals("uc")) {
            if (args.length == 1) {
                completions.addAll(Arrays.asList("auto", "all"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("all")) {
                completions.add("confirm");
            }
        } else if (cmd.equals("unclaimall")) {
            if (args.length == 1) {
                completions.add("confirm");
            }
        }

        // Filter based on current input
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Get online player names excluding the sender, filtered by input
     */
    private List<String> getOnlinePlayerNames(Player sender, String input) {
        String lowerInput = input.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(sender))
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(lowerInput))
                .collect(Collectors.toList());
    }

    /**
     * Get home names for the player, filtered by input
     */
    private List<String> getHomeNames(Player player, String input) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(LandClaimPlugin.class);
        if (!(plugin instanceof LandClaimPlugin landClaim))
            return new ArrayList<>();

        HomeManager homeManager = landClaim.getHomeManager();
        if (homeManager == null)
            return new ArrayList<>();

        String lowerInput = input.toLowerCase();
        return homeManager.getHomes(player.getUniqueId()).keySet().stream()
                .filter(name -> name.toLowerCase().startsWith(lowerInput))
                .collect(Collectors.toList());
    }
}