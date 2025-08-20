package com.nowheberg.bosses;

import org.bukkit.boss.BossBar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossInstance {
    public final String id;
    public final String definitionKey;
    public final UUID entityId;
    public final BossBar bar;
    public final Map<String, Long> lastCast = new HashMap<>();

    public BossInstance(String id, String definitionKey, UUID entityId, BossBar bar) {
        this.id = id;
        this.definitionKey = definitionKey;
        this.entityId = entityId;
        this.bar = bar;
    }
}