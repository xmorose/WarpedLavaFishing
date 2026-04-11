# WarpedLavaFishing

A Paper plugin that lets players fish in lava with full vanilla-style bobbing, biting, and reel-in mechanics.

## Features

- Cast into lava and watch the bobber float on the surface
- Fish state machine with wait → approach → bite phases
- Lure enchantment support
- Per-world gating
- Themed particles and sounds (soul fire, dripping lava, creaking heart)
- Fires standard `PlayerFishEvent.CAUGHT_FISH` so other plugins (loot tables, stats, advancements) still see the catch

## Requirements

- Paper 1.21+
- Java 21
- A server resource pack (see below)

## Installation

1. Drop `WarpedLavaFishing-1.0.0.jar` into your server's `plugins/` folder
2. Start the server once to generate the default config
3. Edit `plugins/WarpedLavaFishing/config.yml`
4. Reload or restart

## Configuration

```yaml
allowed-worlds: []           # empty = all worlds

min-wait-ticks: 100          # fastest wait before a fish takes interest
max-wait-ticks: 600          # slowest wait
lure-ticks-per-level: 100    # Lure subtracts this per level (100 = vanilla)

vehicle-item:
  material: MAP
  custom-model-data: 1013
```

### The vehicle item

Vanilla FishHooks ignore velocity packets, so the plugin mounts them on a snowball projectile and drives that instead. Snowballs can't actually be made invisible through the API — `setInvisible(true)` is silently ignored by the client renderer for projectile entities. The workaround is to set the snowball's carried item to something your resource pack renders as empty.

**You need a server resource pack** that defines an empty/invisible model for the configured `material` + `custom-model-data`. Without it, players will see this item bobbing alongside their fishing hook.

If you don't have a resource pack, set `custom-model-data: 0` and pick a small thematic material — it will still be visible, but at least intentional.

## How it works

Vanilla's `FishingHook.tick()` only recognizes water as a fluid, so a hook cast into lava just sinks. On top of that, `setVelocity()` on a FishHook is overwritten every tick by its own movement code, so you can't bob it from outside.

To work around both, the plugin:

1. Polls the hook after a cast; if it touches lava, spawns an invisible `Snowball` with physics disabled
2. Mounts the FishHook as a passenger on the snowball
3. Drives the snowball's velocity each tick with its own spring-based buoyancy math
4. Runs a fishing state machine (wait → approach → bite)
5. Intercepts `PlayerFishEvent.REEL_IN` and spawns a caught item flying toward the player

The hook visually rides the snowball because vanilla's passenger system positions passengers to match their vehicle.

## License

[AGPL-3.0](LICENSE)
