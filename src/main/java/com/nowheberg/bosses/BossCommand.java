package com.nowheberg.bosses;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BossCommand implements CommandExecutor {
    private final BossManager manager;
    public BossCommand(BossManager manager) { this.manager = manager; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!s.hasPermission("nhbosses.admin")) return true;
        if (args.length == 0) { s.sendMessage("/boss <list|spawn|killall>"); return true; }

        switch (args[0].toLowerCase()) {
            case "list" -> {
                s.sendMessage("Boss dispos: " + String.join(", ", manager.listIds()));
            }
            case "spawn" -> {
                if (args.length < 2) { s.sendMessage("/boss spawn <id> [x y z]"); return true; }
                String id = args[1];
                Location loc;
                if (args.length >= 5 && s instanceof Player p) {
                    try {
                        double x = Double.parseDouble(args[2]);
                        double y = Double.parseDouble(args[3]);
                        double z = Double.parseDouble(args[4]);
                        loc = new Location(p.getWorld(), x, y, z);
                    } catch (Exception ex) { s.sendMessage("Coordonnées invalides."); return true; }
                } else if (s instanceof Player p) {
                    loc = p.getLocation();
                } else {
                    s.sendMessage("Exécute depuis le jeu ou donne des coords.");
                    return true;
                }
                boolean ok = manager.spawnBoss(id, loc);
                s.sendMessage(ok ? "Boss spawn." : "ID inconnu.");
            }
            case "killall" -> {
                manager.despawnAll();
                s.sendMessage("Tous les boss ont été retirés.");
            }
            default -> s.sendMessage("/boss <list|spawn|killall>");
        }
        return true;
    }
}