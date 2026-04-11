package dev.morose.warpedlavafishing;

import org.bukkit.plugin.java.JavaPlugin;

public class WarpedLavaFishing extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new LavaFishingListener(this), this);
    }
}
