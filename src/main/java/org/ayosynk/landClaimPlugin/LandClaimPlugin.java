package org.ayosynk.landClaimPlugin;

import org.ayosynk.landClaimPlugin.commands.CommandHandler;
import org.ayosynk.landClaimPlugin.commands.ClaimTabCompleter;
import org.ayosynk.landClaimPlugin.gui.GUIListener;
import org.ayosynk.landClaimPlugin.hooks.BlueMapHook;
import org.ayosynk.landClaimPlugin.hooks.DynmapHook;
import org.ayosynk.landClaimPlugin.listeners.CommandBlocker;
import org.ayosynk.landClaimPlugin.listeners.EventListener;
import org.ayosynk.landClaimPlugin.listeners.PlayerJoinListener;
import org.ayosynk.landClaimPlugin.managers.ClaimManager;
import org.ayosynk.landClaimPlugin.managers.ConfigManager;
import org.ayosynk.landClaimPlugin.managers.HomeManager;
import org.ayosynk.landClaimPlugin.managers.TrustManager;
import org.ayosynk.landClaimPlugin.managers.VisualizationManager;
import org.ayosynk.landClaimPlugin.managers.SaveManager;
import org.ayosynk.landClaimPlugin.utils.ConfigUpdater;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class LandClaimPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private ClaimManager claimManager;
    private TrustManager trustManager;
    private VisualizationManager visualizationManager;
    private SaveManager saveManager;
    private HomeManager homeManager;
    private CommandHandler commandHandler;
    private EventListener eventListener;
    private BlueMapHook blueMapHook;
    private DynmapHook dynmapHook;
    private List<String> blockedCommands = new ArrayList<>();
    private List<String> blockedWorlds = new ArrayList<>();
    private boolean worldGuardEnabled = false;

    @Override
    public void onEnable() {
        try {
            // Initialize bStats metrics
            // (https://bstats.org/plugin/bukkit/LandClaimPlugin/28407)
            new Metrics(this, 28407);

            // Check for WorldGuard
            if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
                worldGuardEnabled = true;
                getLogger().info("WorldGuard detected. Enabling region gap protection.");
            }
            // Initialize managers
            configManager = new ConfigManager(this);
            claimManager = new ClaimManager(this, configManager);
            trustManager = new TrustManager(this, claimManager, configManager);

            // Load claims and trust data
            claimManager.initialize();
            trustManager.initialize();

            // Initialize visualization manager
            visualizationManager = new VisualizationManager(this, claimManager, configManager);

            // Initialize home manager
            homeManager = new HomeManager(this, configManager);

            // Initialize save manager with debounced async saves
            saveManager = new SaveManager(this, claimManager, trustManager, homeManager);

            // Register commands
            commandHandler = new CommandHandler(this, claimManager, trustManager, configManager, visualizationManager,
                    homeManager);

            // Register events
            eventListener = new EventListener(this, claimManager, trustManager, configManager);
            getServer().getPluginManager().registerEvents(eventListener, this);

            // Register command blocker
            getServer().getPluginManager().registerEvents(
                    new CommandBlocker(this, claimManager, trustManager),
                    this);

            getServer().getPluginManager().registerEvents(
                    new PlayerJoinListener(this, visualizationManager),
                    this);

            // Register GUI listener
            getServer().getPluginManager().registerEvents(
                    new GUIListener(trustManager),
                    this);

            // Register tab completers
            ClaimTabCompleter tabCompleter = new ClaimTabCompleter();
            if (getCommand("claim") != null) {
                getCommand("claim").setTabCompleter(tabCompleter);
            }
            if (getCommand("unclaim") != null) {
                getCommand("unclaim").setTabCompleter(tabCompleter);
            }
            if (getCommand("unclaimall") != null) {
                getCommand("unclaimall").setTabCompleter(tabCompleter);
            }
            // Register tab completers for aliases
            if (getCommand("c") != null) {
                getCommand("c").setTabCompleter(tabCompleter);
            }
            if (getCommand("uc") != null) {
                getCommand("uc").setTabCompleter(tabCompleter);
            }

            // Load configuration
            reloadConfiguration();

            // Start debounced auto-save task
            saveManager.startAutoSave();

            // Initialize map integrations (after config is loaded)
            if (configManager.getConfig().getBoolean("bluemap.enabled", true)
                    && Bukkit.getPluginManager().isPluginEnabled("BlueMap")) {
                blueMapHook = new BlueMapHook(this, claimManager);
                getLogger().info("BlueMap detected. Enabling map integration.");
            }
            if (configManager.getConfig().getBoolean("dynmap.enabled", true)
                    && Bukkit.getPluginManager().isPluginEnabled("dynmap")) {
                dynmapHook = new DynmapHook(this, claimManager);
                getLogger().info("Dynmap detected. Enabling map integration.");
            }

            getLogger().info("LandClaim has been enabled! Loaded " +
                    claimManager.getTotalClaims() + " claims and " +
                    trustManager.getTotalTrusts() + " trust relationships");
        } catch (Exception e) {
            getLogger().severe("Failed to enable LandClaim: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

    public void reloadConfiguration() {
        // Update config to latest version
        ConfigUpdater.updateConfig(this);

        // Reload config manager
        configManager.reloadMainConfig();

        // Reload blocked commands and worlds
        blockedCommands = configManager.getBlockedCommands();
        blockedWorlds = configManager.getConfig().getStringList("block-world");

        // Convert to lowercase for case-insensitive matching
        blockedCommands = blockedCommands.stream().map(String::toLowerCase).toList();
        blockedWorlds = blockedWorlds.stream().map(String::toLowerCase).toList();

        // Reload claims and trust
        claimManager.loadClaims();
        trustManager.loadTrustedPlayers();
        trustManager.loadPermissions();
        trustManager.loadMembers();
    }

    @Override
    public void onDisable() {
        try {
            // Save all data synchronously on disable
            if (saveManager != null) {
                saveManager.saveAll();
                getLogger().info("Saved " + claimManager.getTotalClaims() + " claims and " +
                        trustManager.getTotalTrusts() + " trust relationships");
            }
            if (homeManager != null) {
                homeManager.save();
                getLogger().info("Saved home data");
            }
            if (commandHandler != null) {
                commandHandler.saveAllPlayerData();
                getLogger().info("Saved player data (auto-claim states)");
            }
            if (visualizationManager != null) {
                visualizationManager.saveAllPlayerData();
                getLogger().info("Saved visualization modes");
            }
            getLogger().info("LandClaim has been disabled!");
        } catch (Exception e) {
            getLogger().severe("Error while disabling LandClaim: " + e.getMessage());
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public TrustManager getTrustManager() {
        return trustManager;
    }

    public VisualizationManager getVisualizationManager() {
        return visualizationManager;
    }

    public SaveManager getSaveManager() {
        return saveManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public CommandHandler getCommandHandler() {
        return commandHandler;
    }

    public List<String> getBlockedCommands() {
        return blockedCommands;
    }

    public List<String> getBlockedWorlds() {
        return blockedWorlds;
    }

    public EventListener getEventListener() {
        return eventListener;
    }

    public BlueMapHook getBlueMapHook() {
        return blueMapHook;
    }

    public DynmapHook getDynmapHook() {
        return dynmapHook;
    }

    /**
     * Refresh all map integrations (called on claim/unclaim)
     */
    public void refreshMapHooks() {
        if (blueMapHook != null && blueMapHook.isActive()) {
            blueMapHook.update();
        }
        if (dynmapHook != null && dynmapHook.isActive()) {
            dynmapHook.update();
        }
    }
}