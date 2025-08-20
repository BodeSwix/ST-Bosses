package com.nowheberg.bosses;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class BossListeners implements Listener {
    private final BossManager manager;
    public BossListeners(BossManager manager) { this.manager = manager; }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (manager.isBoss(e.getEntity())) {
            e.getDrops().clear(); // géré par config
            manager.handleDeath(e.getEntity());
        }
    }
}