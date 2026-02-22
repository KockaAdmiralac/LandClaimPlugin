package org.ayosynk.landClaimPlugin.hooks;

import org.ayosynk.landClaimPlugin.LandClaimPlugin;
import org.ayosynk.landClaimPlugin.managers.ClaimManager;
import org.ayosynk.landClaimPlugin.models.ChunkPosition;
import org.bukkit.Bukkit;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.*;

/**
 * Integrates with Dynmap to display claim area markers on the web map.
 * Each claimed chunk is rendered as an area marker rectangle with owner labels.
 */
public class DynmapHook {
    private final LandClaimPlugin plugin;
    private final ClaimManager claimManager;
    private MarkerAPI markerAPI;
    private MarkerSet markerSet;
    private boolean active = false;

    public DynmapHook(LandClaimPlugin plugin, ClaimManager claimManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;

        try {
            DynmapCommonAPI dynmapAPI = (DynmapCommonAPI) Bukkit.getPluginManager().getPlugin("dynmap");
            if (dynmapAPI == null) {
                plugin.getLogger().warning("Dynmap plugin not found.");
                return;
            }

            markerAPI = dynmapAPI.getMarkerAPI();
            if (markerAPI == null) {
                plugin.getLogger().warning("Dynmap Marker API not available.");
                return;
            }

            // Create or get marker set
            markerSet = markerAPI.getMarkerSet("landclaims");
            if (markerSet == null) {
                markerSet = markerAPI.createMarkerSet("landclaims", "Land Claims", null, false);
            }
            if (markerSet == null) {
                plugin.getLogger().warning("Failed to create Dynmap marker set.");
                return;
            }

            markerSet.setHideByDefault(false);
            markerSet.setLayerPriority(10);
            markerSet.setMinZoom(0);

            active = true;
            plugin.getLogger().info("Dynmap integration enabled.");
            update();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize Dynmap integration: " + e.getMessage());
        }
    }

    /**
     * Refresh all claim markers on Dynmap. Called on claim/unclaim events.
     */
    public void update() {
        if (!active || markerSet == null)
            return;

        // Clear existing markers
        for (AreaMarker marker : markerSet.getAreaMarkers()) {
            marker.deleteMarker();
        }

        // Get config colors
        String fillColorHex = plugin.getConfig().getString("dynmap.fill-color", "3366FF");
        double fillOpacity = plugin.getConfig().getDouble("dynmap.fill-opacity", 0.3);
        String borderColorHex = plugin.getConfig().getString("dynmap.border-color", "3366FF");
        double borderOpacity = plugin.getConfig().getDouble("dynmap.border-opacity", 0.8);

        int fillColor = parseHexColor(fillColorHex);
        int borderColor = parseHexColor(borderColorHex);

        // Create markers for all claims
        for (UUID playerId : getAllPlayerIds()) {
            String playerName = Bukkit.getOfflinePlayer(playerId).getName();
            if (playerName == null)
                playerName = "Unknown";

            Set<ChunkPosition> claims = claimManager.getPlayerClaims(playerId);
            int i = 0;
            for (ChunkPosition pos : claims) {
                int minX = pos.getX() * 16;
                int minZ = pos.getZ() * 16;
                int maxX = minX + 16;
                int maxZ = minZ + 16;

                String markerId = "lc_" + playerId.toString() + "_" + i;
                String label = playerName + "'s Claim";

                double[] xCorners = { minX, maxX, maxX, minX };
                double[] zCorners = { minZ, minZ, maxZ, maxZ };

                AreaMarker marker = markerSet.createAreaMarker(
                        markerId, label, false,
                        pos.getWorld(),
                        xCorners, zCorners, false);

                if (marker != null) {
                    marker.setFillStyle(fillOpacity, fillColor);
                    marker.setLineStyle(2, borderOpacity, borderColor);
                    marker.setDescription("<b>" + playerName + "'s Claim</b><br>"
                            + "Chunk: " + pos.getX() + ", " + pos.getZ());
                }

                i++;
            }
        }
    }

    private int parseHexColor(String hex) {
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0x3366FF;
        }
    }

    private Set<UUID> getAllPlayerIds() {
        Set<UUID> playerIds = new HashSet<>();
        for (var offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.hasPlayedBefore()) {
                UUID playerId = offlinePlayer.getUniqueId();
                if (!claimManager.getPlayerClaims(playerId).isEmpty()) {
                    playerIds.add(playerId);
                }
            }
        }
        return playerIds;
    }

    public boolean isActive() {
        return active;
    }
}
