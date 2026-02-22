package org.ayosynk.landClaimPlugin.hooks;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
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
            double fillOpacity = plugin.getConfig().getDouble("bluemap.fill-opacity", 0.05);
            double borderOpacity = plugin.getConfig().getDouble("bluemap.border-opacity", 0.8);

            // Colors are dynamically generated per-player inside the loop

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

                        // Generate a unique color based on the player's UUID
                        Random rnd = new Random(playerId.getMostSignificantBits());
                        int r = rnd.nextInt(200) + 55; // Keep colors somewhat bright
                        int g = rnd.nextInt(200) + 55;
                        int b = rnd.nextInt(200) + 55;
                        Color pFill = new Color(r, g, b, (int) (fillOpacity * 255));
                        Color pBorder = new Color(r, g, b, (int) (borderOpacity * 255));

                        Set<ChunkPosition> chunks = entry.getValue();

                        // Create one marker per chunk for simplicity
                        int i = 0;
                        for (ChunkPosition pos : chunks) {
                            int minX = pos.getX() * 16;
                            int minZ = pos.getZ() * 16;
                            int maxX = minX + 16;
                            int maxZ = minZ + 16;

                            Shape shape = Shape.createRect(minX, minZ, maxX, maxZ);

                            ExtrudeMarker marker = ExtrudeMarker.builder()
                                    .label(playerName + "'s Claim")
                                    .shape(shape, -64, 319) // 3D box covering the whole world height
                                    .fillColor(pFill)
                                    .lineColor(pBorder)
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
