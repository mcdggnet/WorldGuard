/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.bukkit;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.config.YamlConfigurationManager;
import com.sk89q.worldedit.util.report.Unreported;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BukkitConfigurationManager extends YamlConfigurationManager {

    @Unreported private WorldGuardPlugin plugin;
    @Unreported private ConcurrentMap<String, BukkitWorldConfiguration> worlds = new ConcurrentHashMap<>();

    private boolean hasCommandBookGodMode;
    boolean extraStats;

    /**
     * Construct the object.
     *
     * @param plugin The plugin instance
     */
    public BukkitConfigurationManager(WorldGuardPlugin plugin) {
        super();
        this.plugin = plugin;
    }

    public Collection<BukkitWorldConfiguration> getWorldConfigs() {
        return worlds.values();
    }

    @Override
    public void load() {
        super.load();
        this.extraStats = getConfig().getBoolean("custom-metrics-charts", true);
    }

    @Override
    public File getDataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public void copyDefaults() {
        // Create the default configuration file
        plugin.createDefaultConfiguration(new File(plugin.getDataFolder(), "config.yml"), "config.yml");
    }

    @Override
    public void unload() {
        worlds.clear();
    }

    @Override
    public void postLoad() {
        // Load configurations for each world
        for (World world : WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getWorlds()) {
            get(world);
        }
        getConfig().save();
    }

    /**
     * Get the configuration for a world.
     *
     * @param world The world to get the configuration for
     * @return {@code world}'s configuration
     */
    @Override
    public BukkitWorldConfiguration get(World world) {
        String worldName = world.getName();
        return get(worldName);
    }

    public BukkitWorldConfiguration get(String worldName) {
        BukkitWorldConfiguration config = worlds.get(worldName);
        if (config == null) {
            // mcdgg/Folia: worlds load CONCURRENTLY on region threads, and
            // BukkitWorldConfiguration's constructor both reads and WRITES
            // yaml through the shared global YAMLProcessor. Upstream's
            // lock-free putIfAbsent dance still lets two loading worlds run
            // the constructor at once — they interleave inside snakeyaml
            // (Node.getNodeId NPE on WorldLoadEvent) and can corrupt
            // config.yml on disk with anchor/complex-key garbage, which then
            // fails the NEXT boot ("aliases ... exceeds the specified max").
            // Both happened 2026-08-18; serialize creation instead.
            synchronized (this) {
                config = worlds.get(worldName);
                if (config == null) {
                    config = new BukkitWorldConfiguration(plugin, worldName, this.getConfig());
                    worlds.put(worldName, config);
                }
            }
        }
        return config;
    }

    public void updateCommandBookGodMode() {
        try {
            if (plugin.getServer().getPluginManager().isPluginEnabled("CommandBook")) {
                Class.forName("com.sk89q.commandbook.GodComponent");
                hasCommandBookGodMode = true;
                return;
            }
        } catch (ClassNotFoundException ignore) {}
        hasCommandBookGodMode = false;
    }

    public boolean hasCommandBookGodMode() {
        return hasCommandBookGodMode;
    }
}
