package org.ayosynk.landClaimPlugin.hooks;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import com.flowpowered.math.vector.Vector2d;
import org.ayosynk.landClaimPlugin.LandClaimPlugin;
import org.ayosynk.landClaimPlugin.managers.ClaimManager;
import org.ayosynk.landClaimPlugin.models.ChunkPosition;
import org.bukkit.Bukkit;

import java.util.*;

/**
 * Integrates with BlueMap to display claim markers on the web map.
 * Uses BlueMap API v2 with onEnable/onDisable lifecycle callbacks.
 */
public class BlueMapHook {
    private final LandClaimPlugin plugin;
    private final ClaimManager claimManager;
    private boolean active = false;

    public BlueMapHook(LandClaimPlugin plugin, ClaimManager claimManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;

        BlueMapAPI.onEnable(api -> {
            active = true;
            plugin.getLogger().info("BlueMap integration enabled.");
            update();
        });

        BlueMapAPI.onDisable(api -> {
            active = false;
            plugin.getLogger().info("BlueMap integration disabled.");
        });
    }

    /**
     * Refresh all claim markers on BlueMap. Called on claim/unclaim events.
     */
    public void update() {
        BlueMapAPI.getInstance().ifPresent(api -> {
            // Build markers per world
            // Collect all player claims grouped by world
            Map<String, Map<UUID, Set<ChunkPosition>>> worldPlayerClaims = new HashMap<>();

            for (UUID playerId : getAllPlayerIds()) {
                Set<ChunkPosition> claims = claimManager.getPlayerClaims(playerId);
                for (ChunkPosition pos : claims) {
                    worldPlayerClaims
                            .computeIfAbsent(pos.getWorld(), k -> new HashMap<>())
                            .computeIfAbsent(playerId, k -> new HashSet<>())
                            .add(pos);
                }
            }

            // Get config colors
            String fillColorHex = plugin.getConfig().getString("bluemap.fill-color", "3366FF");
            double fillOpacity = plugin.getConfig().getDouble("bluemap.fill-opacity", 0.3);
            String borderColorHex = plugin.getConfig().getString("bluemap.border-color", "3366FF");
            double borderOpacity = plugin.getConfig().getDouble("bluemap.border-opacity", 0.8);

            Color fillColor = parseColor(fillColorHex, fillOpacity);
            Color borderColor = parseColor(borderColorHex, borderOpacity);

            for (BlueMapMap map : api.getMaps()) {
                String worldId = map.getWorld().getId();

                MarkerSet markerSet = MarkerSet.builder()
                        .label("LandClaims")
                        .defaultHidden(false)
                        .build();

                // Find which world name matches this map's world
                Map<UUID, Set<ChunkPosition>> playerClaimsInWorld = null;
                for (Map.Entry<String, Map<UUID, Set<ChunkPosition>>> entry : worldPlayerClaims.entrySet()) {
                    String worldName = entry.getKey();
                    // BlueMap world IDs can vary, try matching by name
                    if (worldId.contains(worldName) || worldName.equals(worldId)) {
                        playerClaimsInWorld = entry.getValue();
                        break;
                    }
                }

                if (playerClaimsInWorld != null) {
                    for (Map.Entry<UUID, Set<ChunkPosition>> entry : playerClaimsInWorld.entrySet()) {
                        UUID playerId = entry.getKey();
                        String playerName = Bukkit.getOfflinePlayer(playerId).getName();
                        if (playerName == null)
                            playerName = "Unknown";

                        Set<ChunkPosition> chunks = entry.getValue();

                        // Create one marker per chunk for simplicity
                        int i = 0;
                        for (ChunkPosition pos : chunks) {
                            int minX = pos.getX() * 16;
                            int minZ = pos.getZ() * 16;
                            int maxX = minX + 16;
                            int maxZ = minZ + 16;

                            Shape shape = Shape.builder()
                                    .addPoint(new Vector2d(minX, minZ))
                                    .addPoint(new Vector2d(maxX, minZ))
                                    .addPoint(new Vector2d(maxX, maxZ))
                                    .addPoint(new Vector2d(minX, maxZ))
                                    .build();

                            ShapeMarker marker = ShapeMarker.builder()
                                    .label(playerName + "'s Claim")
                                    .shape(shape, 64)
                                    .fillColor(fillColor)
                                    .lineColor(borderColor)
                                    .lineWidth(2)
                                    .depthTestEnabled(false)
                                    .build();

                            String markerId = playerId.toString() + "_" + i;
                            markerSet.getMarkers().put(markerId, marker);
                            i++;
                        }
                    }
                }

                map.getMarkerSets().put("landclaims", markerSet);
            }
        });
    }

    private Color parseColor(String hex, double opacity) {
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            int a = (int) (opacity * 255);
            return new Color(r, g, b, a);
        } catch (Exception e) {
            return new Color(51, 102, 255, (int) (opacity * 255));
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
