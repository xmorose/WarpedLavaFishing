package dev.morose.warpedlavafishing;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class LavaHook {
    private static final Vector GRAVITY = new Vector(0.0, -0.03, 0.0);
    private static final double WATER_RESISTANCE = 0.92;
    private static final double HORIZONTAL_DAMPING = 0.9;
    private static final double VERTICAL_ADJUSTMENT = 0.2;
    private static final double BITING_Y_MULTIPLIER = -0.1;
    private static final double Y_VELOCITY_CAP = 0.4;
    private static final double LAVA_SURFACE_OFFSET = 0.85;

    private static final int LURE_TICKS_PER_LEVEL = 100;
    private static final int MIN_WAIT_FLOOR = 20;

    private final WarpedLavaFishing plugin;
    private final FishHook hook;
    private final Snowball vehicle;
    private final int lureSpeed;

    private int timeUntilLured;
    private int timeUntilHooked;
    private int nibble;
    private float fishAngle;
    private boolean biting;

    public LavaHook(WarpedLavaFishing plugin, FishHook hook, Snowball vehicle, int lureSpeed) {
        this.plugin = plugin;
        this.hook = hook;
        this.vehicle = vehicle;
        this.lureSpeed = Math.max(0, lureSpeed);
        resetTimeUntilLured();
    }

    public boolean tick() {
        if (hook.isDead() || !hook.isValid() || !vehicle.isValid()) {
            return false;
        }
        if (hook.getState() == FishHook.HookState.BOBBING) {
            vehicle.remove();
            return false;
        }
        if (hook.getState() == FishHook.HookState.HOOKED_ENTITY) {
            return true;
        }

        doPhysics();
        return true;
    }

    private void doPhysics() {
        Location hookLoc = hook.getLocation();
        Block block = hookLoc.getBlock();
        boolean inLava = block.getType() == Material.LAVA;
        Vector velocity = vehicle.getVelocity();

        if (inLava) {
            double lavaHeight = block.getY() + LAVA_SURFACE_OFFSET;
            Block above = block.getRelative(0, 1, 0);
            while (above.getType() == Material.LAVA) {
                lavaHeight = above.getY() + LAVA_SURFACE_OFFSET;
                above = above.getRelative(0, 1, 0);
            }
            double diff = hookLoc.getY() + velocity.getY() - lavaHeight;
            if (Math.abs(diff) < 0.01) {
                diff += Math.signum(diff) * 0.1;
            }
            velocity.setY(velocity.getY() - diff * ThreadLocalRandom.current().nextFloat() * VERTICAL_ADJUSTMENT);
            velocity.setY(Math.max(-Y_VELOCITY_CAP, Math.min(Y_VELOCITY_CAP, velocity.getY())));

            if (biting) {
                velocity.setX(0);
                velocity.setY(BITING_Y_MULTIPLIER * ThreadLocalRandom.current().nextFloat() * ThreadLocalRandom.current().nextFloat());
                velocity.setZ(0);
            }

            tickFishingState();
        } else {
            velocity.add(GRAVITY);
        }

        velocity.setX(velocity.getX() * HORIZONTAL_DAMPING);
        velocity.setZ(velocity.getZ() * HORIZONTAL_DAMPING);
        velocity.multiply(WATER_RESISTANCE);

        vehicle.setVelocity(velocity);
    }

    private void tickFishingState() {
        if (nibble > 0) {
            nibble--;
            if (nibble <= 0) {
                timeUntilLured = 0;
                timeUntilHooked = 0;
                biting = false;
            }
        } else if (timeUntilHooked > 0) {
            timeUntilHooked--;
            updateFishMovement();
            spawnApproachParticles();

            if (timeUntilHooked <= 0) {
                playBiteEffects();
                nibble = ThreadLocalRandom.current().nextInt(20, 40);
                biting = true;
            }
        } else if (timeUntilLured > 0) {
            timeUntilLured--;
            spawnAmbientParticles();

            if (timeUntilLured <= 0) {
                fishAngle = ThreadLocalRandom.current().nextFloat(0f, 360f);
                timeUntilHooked = ThreadLocalRandom.current().nextInt(20, 80);
            }
        } else {
            resetTimeUntilLured();
        }
    }

    public boolean wind(Player player, EquipmentSlot hand) {
        if (nibble <= 0) {
            return false;
        }

        Location hookLoc = hook.getLocation();

        Item dummyItem = hookLoc.getWorld().spawn(hookLoc, Item.class, item -> {
            item.setItemStack(new ItemStack(Material.COD));
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setInvulnerable(true);
            item.setVisualFire(false);
        });

        double dx = player.getX() - hookLoc.getX();
        double dy = player.getY() - hookLoc.getY();
        double dz = player.getZ() - hookLoc.getZ();
        dummyItem.setVelocity(new Vector(
                dx * 0.1,
                dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08,
                dz * 0.1
        ));

        int exp = ThreadLocalRandom.current().nextInt(1, 7);

        PlayerFishEvent fishEvent = new PlayerFishEvent(player, dummyItem, hook, hand, PlayerFishEvent.State.CAUGHT_FISH);
        fishEvent.setExpToDrop(exp);
        if (!fishEvent.callEvent()) {
            dummyItem.remove();
            return true;
        }

        dummyItem.setPickupDelay(0);
        exp = fishEvent.getExpToDrop();

        if (exp > 0) {
            Location orbLoc = player.getLocation().add(0, 0.5, 0);
            int finalExp = exp;
            player.getWorld().spawn(orbLoc, ExperienceOrb.class, orb -> orb.setExperience(finalExp));
        }

        hookLoc.getWorld().spawnParticle(Particle.LAVA, hookLoc, 15, 0.5, 0.3, 0.5);
        hookLoc.getWorld().playSound(hookLoc, Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.2f);

        hook.remove();
        return true;
    }

    public void remove() {
        if (vehicle.isValid()) {
            vehicle.remove();
        }
    }

    private void updateFishMovement() {
        fishAngle += 9.188f * (ThreadLocalRandom.current().nextFloat() - ThreadLocalRandom.current().nextFloat());
    }

    private void spawnApproachParticles() {
        World world = hook.getWorld();
        double angleRad = Math.toRadians(fishAngle);
        double sin = Math.sin(angleRad);
        double cos = Math.cos(angleRad);
        double x = hook.getX() + sin * timeUntilHooked * 0.1;
        double y = Math.floor(hook.getY()) + 1.0;
        double z = hook.getZ() + cos * timeUntilHooked * 0.1;

        Block block = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y - 1.0), (int) Math.floor(z));
        if (block.getType() != Material.LAVA) {
            return;
        }

        if (ThreadLocalRandom.current().nextFloat() < 0.15f) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, x, y + 0.2, z, 1, sin, 0.1, cos, 0.0);
        }
        double offsetX = sin * 0.04;
        double offsetZ = cos * 0.04;
        world.spawnParticle(Particle.SMALL_FLAME, x, y + 0.15, z, 0, offsetZ, 0.01, -offsetX, 1.0);
        world.spawnParticle(Particle.SMALL_FLAME, x, y + 0.15, z, 0, -offsetZ, 0.01, offsetX, 1.0);
    }

    private void spawnAmbientParticles() {
        float threshold = 0.15f;
        if (timeUntilLured < 20) {
            threshold += (20 - timeUntilLured) * 0.05f;
        } else if (timeUntilLured < 40) {
            threshold += (40 - timeUntilLured) * 0.02f;
        } else if (timeUntilLured < 60) {
            threshold += (60 - timeUntilLured) * 0.01f;
        }

        if (ThreadLocalRandom.current().nextFloat() >= threshold) {
            return;
        }

        float randomAngle = ThreadLocalRandom.current().nextFloat(0f, 360f);
        float randomDist = ThreadLocalRandom.current().nextFloat(25f, 60f);
        double rad = Math.toRadians(randomAngle);
        double x = hook.getX() + Math.sin(rad) * randomDist * 0.1;
        double y = Math.floor(hook.getY()) + 1.0;
        double z = hook.getZ() + Math.cos(rad) * randomDist * 0.1;

        Block block = hook.getWorld().getBlockAt((int) Math.floor(x), (int) Math.floor(y - 1.0), (int) Math.floor(z));
        if (block.getType() != Material.LAVA) {
            return;
        }

        int count = 2 + ThreadLocalRandom.current().nextInt(2);
        hook.getWorld().spawnParticle(Particle.DRIPPING_LAVA, x, y + 0.2, z, count, 0.2f, 0.1, 0.2f, 0.0);
    }

    private void playBiteEffects() {
        World world = hook.getWorld();
        Location loc = hook.getLocation();

        world.playSound(loc, Sound.BLOCK_CREAKING_HEART_SPAWN, 1.0f,
                1.0f + ThreadLocalRandom.current().nextFloat() * 0.5f);

        double y = loc.getY() + 0.8;
        int count = (int) (1.0f + hook.getWidth() * 20.0f);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.getX(), y, loc.getZ(), count, hook.getWidth(), 0.1, hook.getWidth(), 0.2f);
        world.spawnParticle(Particle.DRIPPING_LAVA, loc.getX(), y, loc.getZ(), count, hook.getWidth(), 0.1, hook.getWidth(), 0.2f);
    }

    private void resetTimeUntilLured() {
        int minWait = plugin.getConfig().getInt("min-wait-ticks", 100);
        int maxWait = plugin.getConfig().getInt("max-wait-ticks", 600);
        int lureTicksPerLevel = plugin.getConfig().getInt("lure-ticks-per-level", LURE_TICKS_PER_LEVEL);
        int wait = ThreadLocalRandom.current().nextInt(minWait, maxWait + 1) - lureSpeed * lureTicksPerLevel;
        timeUntilLured = Math.max(MIN_WAIT_FLOOR, wait);
    }
}
