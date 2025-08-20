package com.nowheberg.bosses;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class BossesPlugin extends JavaPlugin {
    private BossManager bossManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.bossManager = new BossManager(this);
        if (getCommand("boss") != null) {
            getCommand("boss").setExecutor(new BossCommand(bossManager));
        }
        Bukkit.getPluginManager().registerEvents(new BossListeners(bossManager), this);

        // Tick des boss (barre, capacités)
        Bukkit.getScheduler().runTaskTimer(this, bossManager::tickBosses, 20L, 20L);
        // Spawn automatique périodique
        Bukkit.getScheduler().runTaskTimer(this, bossManager::tryAutoSpawns, 20L * 30, 20L * 30);

        getLogger().info("NHBosses chargé.");
    }

    @Override
    public void onDisable() {
        if (bossManager != null) bossManager.despawnAll();
    }
}