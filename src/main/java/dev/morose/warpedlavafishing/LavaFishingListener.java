package dev.morose.warpedlavafishing;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
public class LavaFishingListener implements Listener {

    private final WarpedLavaFishing plugin;
    private final Map<UUID, LavaHook> activeHooks = new ConcurrentHashMap<>();

    public LavaFishingListener(WarpedLavaFishing plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFish(PlayerFishEvent event) {
        switch (event.getState()) {
            case FISHING -> onCast(event.getHook(), event.getPlayer());
            case REEL_IN, FAILED_ATTEMPT, IN_GROUND -> {
                LavaHook lavaHook = activeHooks.get(event.getPlayer().getUniqueId());
                if (lavaHook != null && lavaHook.wind(event.getPlayer(), event.getHand())) {
                    event.setCancelled(true);
                }
            }
            default -> {}
        }
    }

    @EventHandler
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof FishHook hook) {
            UUID ownerUUID = hook.getOwnerUniqueId();
            if (ownerUUID != null) {
                cleanUp(ownerUUID);
            }
        }
    }
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Snowball snowball
                && snowball.getPassengers().stream().anyMatch(p -> p instanceof FishHook)) {
            event.setCancelled(true);
        }
    }

    private void onCast(FishHook hook, Player player) {
        List<String> allowedWorlds = plugin.getConfig().getStringList("allowed-worlds");
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(hook.getWorld().getName())) {
            return;
        }
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks++;
                if (!hook.isValid() || ticks > 100) {
                    cancel();
                    return;
                }
                if (hook.getLocation().getBlock().getType() == Material.LAVA) {
                    cancel();
                    setupLavaHook(hook, player);
                }
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    private void setupLavaHook(FishHook hook, Player player) {
        ItemStack vehicleItem = createVehicleItem();

        Snowball vehicle = hook.getWorld().spawn(hook.getLocation().add(0, -0.25, 0), Snowball.class, snowball -> {
            snowball.setSilent(true);
            snowball.setItem(vehicleItem);
            snowball.setPersistent(false);
            snowball.setInvulnerable(true);
            snowball.setGravity(false);
            snowball.setNoPhysics(true);
            snowball.setVelocity(hook.getVelocity().multiply(new Vector(0.3, 0.1, 0.3)));
            snowball.addPassenger(hook);
        });

        LavaHook lavaHook = new LavaHook(plugin, hook, vehicle, getLureLevel(player));
        activeHooks.put(player.getUniqueId(), lavaHook);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!lavaHook.tick()) {
                    cancel();
                    cleanUp(player.getUniqueId());
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private ItemStack createVehicleItem() {
        String materialName = plugin.getConfig().getString("vehicle-item.material", "MAP");
        int modelData = plugin.getConfig().getInt("vehicle-item.custom-model-data", 0);

        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("Invalid vehicle-item.material '" + materialName
                    + "' — falling back to MAP. The bobber will be visible without a matching resource pack.");
            material = Material.MAP;
        }

        ItemStack item = new ItemStack(material);
        if (modelData > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(modelData);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private int getLureLevel(Player player) {
        int main = lureLevelOf(player.getInventory().getItemInMainHand());
        int off = lureLevelOf(player.getInventory().getItemInOffHand());
        return Math.max(main, off);
    }

    private int lureLevelOf(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) {
            return 0;
        }
        return item.getEnchantmentLevel(Enchantment.LURE);
    }

    private void cleanUp(UUID playerUUID) {
        LavaHook lavaHook = activeHooks.remove(playerUUID);
        if (lavaHook != null) {
            lavaHook.remove();
        }
    }
}
