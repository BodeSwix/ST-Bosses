package com.nowheberg.bosses;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class BossManager {
    private final BossesPlugin plugin;
    private final NamespacedKey keyType, keyId;
    private final Map<UUID, BossInstance> active = new HashMap<>();
    private final Map<String, ConfigurationSection> defs = new HashMap<>();

    public BossManager(BossesPlugin plugin) {
        this.plugin = plugin;
        this.keyType = new NamespacedKey(plugin, "type");
        this.keyId = new NamespacedKey(plugin, "id");
        reloadDefs();
    }

    public void reloadDefs() {
        defs.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("bosses");
        if (sec != null) for (String k : sec.getKeys(false)) defs.put(k, sec.getConfigurationSection(k));
    }

    public Set<String> listIds() { return defs.keySet(); }

    public void tickBosses() {
        Iterator<UUID> it = active.keySet().iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Entity e = Bukkit.getEntity(uuid);
            if (!(e instanceof LivingEntity le) || le.isDead()) {
                BossInstance bi = active.get(uuid);
                if (bi != null) bi.bar.removeAll();
                it.remove();
                continue;
            }
            BossInstance bi = active.get(uuid);
            double hp = le.getHealth();
            double max = Objects.requireNonNull(le.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();
            bi.bar.setProgress(Math.max(0.0, Math.min(1.0, hp / max)));
            // Casting périodique
            ConfigurationSection def = defs.get(bi.definitionKey);
            if (def == null) continue;
            ConfigurationSection abilities = def.getConfigurationSection("abilities");
            if (abilities == null) continue;
            for (String a : abilities.getKeys(false)) {
                ConfigurationSection as = abilities.getConfigurationSection(a);
                long now = System.currentTimeMillis();
                long every = as.getLong("interval_ms", 12000);
                long last = bi.lastCast.getOrDefault(a, 0L);
                if (now - last >= every) {
                    runAbility(a, as, le);
                    bi.lastCast.put(a, now);
                }
            }
        }
    }

    public void tryAutoSpawns() {
        int maxActive = plugin.getConfig().getInt("auto_spawn.max_active", 3);
        if (active.size() >= maxActive) return;

        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.isDead() && p.getWorld().getEnvironment() != World.Environment.CUSTOM)
                .toList();
        if (players.isEmpty()) return;

        for (String id : defs.keySet()) {
            if (active.size() >= maxActive) break;
            ConfigurationSection def = defs.get(id);
            if (!def.getBoolean("spawn.enabled", true)) continue;

            double chance = def.getDouble("spawn.chance", 0.15);
            if (ThreadLocalRandom.current().nextDouble() > chance) continue;

            Player base = players.get(ThreadLocalRandom.current().nextInt(players.size()));
            Location loc = base.getLocation().clone().add(randomXZ(15, 35), 0, randomXZ(15, 35));
            loc = base.getWorld().getHighestBlockAt(loc).getLocation().add(0.5, 1, 0.5);

            if (!matchBiome(def, base.getWorld().getBiome(loc))) continue;

            spawnBoss(id, loc);
        }
    }

    private int randomXZ(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
    }

    private boolean matchBiome(ConfigurationSection def, Biome biome) {
        List<String> allowed = def.getStringList("spawn.biomes");
        if (allowed.isEmpty()) return true;
        String b = biome.name().toLowerCase(Locale.ROOT);
        for (String s : allowed) if (b.contains(s.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    public boolean spawnBoss(String id, Location loc) {
        ConfigurationSection def = defs.get(id);
        if (def == null) return false;

        EntityType type = EntityType.valueOf(def.getString("entity", "HUSK").toUpperCase(Locale.ROOT));
        Entity e = loc.getWorld().spawnEntity(loc, type);
        if (!(e instanceof LivingEntity le)) { e.remove(); return false; }

        // SCALE (taille)
        double scaleVal = def.getDouble("scale", 1.0);
        AttributeInstance sc = le.getAttribute(Attribute.SCALE);
        if (sc == null) { le.registerAttribute(Attribute.SCALE); sc = le.getAttribute(Attribute.SCALE); }
        if (sc != null) sc.setBaseValue(scaleVal);

        double maxHp = def.getDouble("stats.health", 300.0);
        double dmg = def.getDouble("stats.damage", 10.0);
        double armor = def.getDouble("stats.armor", 5.0);
        double speed = def.getDouble("stats.speed", 0.30);

        Objects.requireNonNull(le.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(maxHp);
        le.setHealth(maxHp);
        Objects.requireNonNull(le.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).setBaseValue(dmg);
        Objects.requireNonNull(le.getAttribute(Attribute.GENERIC_ARMOR)).setBaseValue(armor);
        Objects.requireNonNull(le.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).setBaseValue(speed);
        le.setRemoveWhenFarAway(false);
        le.setCanPickupItems(false);
        le.setPersistent(true);

        String nameRaw = def.getString("name", "&cBoss");
        String legacy = ChatColor.translateAlternateColorCodes('&', nameRaw);
        Component comp = LegacyComponentSerializer.legacySection().deserialize(legacy);
        le.customName(comp);
        le.setCustomNameVisible(true);

        // Tag PDC
        le.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, "boss");
        String uid = UUID.randomUUID().toString();
        le.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, uid);

        // BossBar
        BarColor color = BarColor.valueOf(def.getString("bar.color", "RED").toUpperCase(Locale.ROOT));
        BossBar bar = Bukkit.createBossBar(legacy, color, BarStyle.SEGMENTED_10);
        Player np = nearestPlayer(le.getLocation());
        if (np != null) bar.addPlayer(np);
        bar.setVisible(true);

        BossInstance bi = new BossInstance(uid, id, le.getUniqueId(), bar);
        active.put(le.getUniqueId(), bi);
        return true;
    }

    private Player nearestPlayer(Location l) {
        return l.getWorld().getPlayers().stream()
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(l)))
                .orElse(null);
    }

    public void despawnAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof LivingEntity le) le.remove();
        }
        active.values().forEach(b -> b.bar.removeAll());
        active.clear();
    }

    public boolean isBoss(Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        String tag = le.getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
        return "boss".equals(tag);
    }

    public void handleDeath(LivingEntity le) {
        BossInstance bi = active.remove(le.getUniqueId());
        if (bi != null) bi.bar.removeAll();
        if (bi == null) return;
        ConfigurationSection def = defs.get(bi.definitionKey);
        if (def == null) return;
        ConfigurationSection loot = def.getConfigurationSection("loot");
        if (loot == null) return;

        for (String key : loot.getKeys(false)) {
            ConfigurationSection itemSec = loot.getConfigurationSection(key);
            double chance = itemSec.getDouble("chance", 1.0);
            if (ThreadLocalRandom.current().nextDouble() > chance) continue;

            Material mat = Material.valueOf(itemSec.getString("material", "EMERALD"));
            int min = itemSec.getInt("amount.min", 1);
            int max = itemSec.getInt("amount.max", 1);
            int amount = ThreadLocalRandom.current().nextInt(min, max + 1);

            ItemStack it = new ItemStack(mat, Math.max(1, amount));
            ItemMeta meta = it.getItemMeta();
            String display = itemSec.getString("name", null);
            if (display != null) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', display));
            it.setItemMeta(meta);

            ConfigurationSection ench = itemSec.getConfigurationSection("enchants");
            if (ench != null) {
                for (String en : ench.getKeys(false)) {
                    Enchantment enchType = Enchantment.getByName(en.toUpperCase(Locale.ROOT));
                    if (enchType != null) it.addUnsafeEnchantment(enchType, ench.getInt(en, 1));
                }
            }
            le.getWorld().dropItemNaturally(le.getLocation(), it);
        }
        le.getWorld().playSound(le.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        le.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, le.getLocation(), 30, 1, 0.5, 1, 0.05);
    }

    private void runAbility(String key, ConfigurationSection as, LivingEntity caster) {
        double radius = as.getDouble("radius", 8.0);
        switch (key.toLowerCase(Locale.ROOT)) {
            case "sandstorm" -> {
                caster.getWorld().spawnParticle(Particle.BLOCK, caster.getLocation(), 200, radius, 1, radius, 0.1, Material.SAND.createBlockData());
                affectNearby(caster, radius, p -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 4, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 6, 1));
                    Vector kb = p.getLocation().toVector().subtract(caster.getLocation().toVector()).normalize().multiply(0.7).setY(0.2);
                    p.setVelocity(p.getVelocity().add(kb));
                });
            }
            case "freeze_aura" -> {
                caster.getWorld().spawnParticle(Particle.SNOWFLAKE, caster.getLocation(), 180, radius, 1, radius, 0.1);
                affectNearby(caster, radius, p -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 6, 2));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 6, 1));
                });
            }
            case "rage_charge" -> {
                Player target = nearestPlayer(caster.getLocation());
                if (target != null) {
                    Vector dir = target.getLocation().toVector().subtract(caster.getLocation().toVector()).normalize();
                    caster.setVelocity(dir.multiply(1.1).setY(0.3));
                    caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1f, 0.8f);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        caster.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, caster.getLocation(), 30, 1, 0.5, 1, 0.05);
                        affectNearby(caster, 3.5, p -> p.damage(6.0, caster));
                    }, 12L);
                }
            }
            case "root_snare" -> {
                caster.getWorld().spawnParticle(Particle.FALLING_SPORE_BLOSSOM, caster.getLocation(), 120, radius, 0.5, radius, 0.01);
                affectNearby(caster, radius, p -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 5, 3));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 4, 0));
                });
            }
            case "shulker_barrage" -> {
                Player t = nearestPlayer(caster.getLocation());
                if (t != null) {
                    for (int i = 0; i < 4; i++) {
                        ShulkerBullet b = (ShulkerBullet) caster.getWorld().spawnEntity(caster.getLocation().add(0, 1.5, 0), EntityType.SHULKER_BULLET);
                        b.setShooter(caster);
                        b.setTarget(t);
                    }
                }
            }
            // --- NOUVELLES CAPACITÉS ---
            case "wind_burst" -> {
                caster.getWorld().spawnParticle(Particle.CLOUD, caster.getLocation(), 200, radius, 1, radius, 0.05);
                affectNearby(caster, radius, p -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 10, 0, true, true, true));
                    Vector kb = p.getLocation().toVector().subtract(caster.getLocation().toVector())
                            .normalize().multiply(1.1).setY(0.6);
                    p.setVelocity(p.getVelocity().add(kb));
                });
            }
            case "sonic_pulse" -> {
                caster.getWorld().spawnParticle(Particle.SONIC_BOOM, caster.getLocation().add(0,1.2,0), 1, 0,0,0, 0);
                caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
                affectNearby(caster, radius, p -> {
                    p.damage(as.getDouble("damage", 10.0), caster);
                    Vector push = p.getLocation().toVector().subtract(caster.getLocation().toVector())
                            .normalize().multiply(1.0).setY(0.4);
                    p.setVelocity(p.getVelocity().add(push));
                });
            }
            case "darkness_curse" -> {
                int dur = (int)(as.getDouble("seconds", 7.0) * 20);
                affectNearby(caster, radius, p -> p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, dur, 0, true, true, true)));
            }
            case "vex_call" -> {
                int count = as.getInt("count", 3);
                for (int i = 0; i < count; i++) {
                    Vex vex = (Vex) caster.getWorld().spawnEntity(
                            caster.getLocation().add(ThreadLocalRandom.current().nextDouble(-2,2), 1.0, ThreadLocalRandom.current().nextDouble(-2,2)),
                            EntityType.VEX
                    );
                    vex.setLimitedLifetime(true);
                    vex.setLifeTicks(as.getInt("lifetime_ticks", 20*20));
                    vex.setTarget(nearestPlayer(caster.getLocation()));
                }
            }
            case "potion_cloud" -> {
                String potion = as.getString("potion", "POISON");
                int amp = as.getInt("amplifier", 0);
                int dur = (int)(as.getDouble("seconds", 8.0) * 20);
                AreaEffectCloud cloud = (AreaEffectCloud) caster.getWorld().spawnEntity(
                        caster.getLocation(), EntityType.AREA_EFFECT_CLOUD
                );
                cloud.setRadius((float) radius);
                cloud.setRadiusOnUse(-0.5f);
                cloud.setWaitTime(10);
                cloud.setDuration(dur);
                PotionEffectType pet = PotionEffectType.getByName(potion.toUpperCase(Locale.ROOT));
                if (pet == null) pet = PotionEffectType.POISON;
                cloud.addCustomEffect(new PotionEffect(pet, dur, amp, true, true, true), true);
            }
            case "stomp_quake" -> {
                caster.getWorld().spawnParticle(Particle.BLOCK, caster.getLocation(), 150, radius, 0.5, radius, 0.1, Material.DIRT.createBlockData());
                caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.7f);
                affectNearby(caster, radius, p -> {
                    p.damage(as.getDouble("damage", 6.0), caster);
                    Vector kb = p.getLocation().toVector().subtract(caster.getLocation().toVector())
                            .normalize().multiply(0.8).setY(0.5);
                    p.setVelocity(p.getVelocity().add(kb));
                });
            }
        }
    }

    private void affectNearby(LivingEntity caster, double radius, java.util.function.Consumer<Player> effect) {
        List<Player> targets = caster.getLocation().getWorld().getNearbyEntities(caster.getLocation(), radius, radius, radius).stream()
                .filter(e -> e instanceof Player p && !p.isDead() && p.getGameMode() == GameMode.SURVIVAL)
                .map(e -> (Player)e).collect(Collectors.toList());
        for (Player p : targets) effect.accept(p);
    }
}