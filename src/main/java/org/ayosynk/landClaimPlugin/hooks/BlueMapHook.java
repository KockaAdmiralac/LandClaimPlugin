package org.ayosynk.landClaimPlugin.hooks;

import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
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
                        Color pFill = new Color(r, g, b, (float) fillOpacity);
                        Color pBorder = new Color(r, g, b, (float) borderOpacity);

                        // Create a merged marker for each contiguous polygon
                        Set<ChunkPosition> chunks = entry.getValue();
                        List<List<Vector2d>> polygons = createPolygons(chunks);
                        int i = 0;
                        for (List<Vector2d> polygon : polygons) {
                            if (polygon.size() < 3)
                                continue;

                            Shape.Builder shapeBuilder = Shape.builder();
                            for (Vector2d pt : polygon) {
                                shapeBuilder.addPoint(pt);
                            }
                            Shape shape = shapeBuilder.build();

                            ShapeMarker marker = ShapeMarker.builder()
                                    .label(playerName + "'s Claim")
                                    .shape(shape, 64) // Flat marker at Y=64
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

    private record Point(int x, int z) {
    }

    private record Edge(Point from, Point to) {
    }

    private List<List<Vector2d>> createPolygons(Set<ChunkPosition> chunks) {
        Set<Edge> edges = new HashSet<>();
        for (ChunkPosition chunk : chunks) {
            int cx = chunk.getX();
            int cz = chunk.getZ();

            Point p00 = new Point(cx, cz);
            Point p10 = new Point(cx + 1, cz);
            Point p11 = new Point(cx + 1, cz + 1);
            Point p01 = new Point(cx, cz + 1);

            Edge[] chunkEdges = {
                    new Edge(p00, p10), // Top
                    new Edge(p10, p11), // Right
                    new Edge(p11, p01), // Bottom
                    new Edge(p01, p00) // Left
            };

            for (Edge e : chunkEdges) {
                Edge opposite = new Edge(e.to(), e.from());
                if (edges.contains(opposite)) {
                    edges.remove(opposite);
                } else {
                    edges.add(e);
                }
            }
        }

        Map<Point, List<Edge>> adjacency = new HashMap<>();
        for (Edge e : edges) {
            adjacency.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e);
        }

        List<List<Vector2d>> polygons = new ArrayList<>();

        while (!adjacency.isEmpty()) {
            Point start = adjacency.keySet().iterator().next();
            List<Vector2d> polygon = new ArrayList<>();
            Point current = start;

            while (true) {
                polygon.add(new Vector2d(current.x() * 16.0, current.z() * 16.0));

                List<Edge> outEdges = adjacency.get(current);
                if (outEdges == null || outEdges.isEmpty()) {
                    adjacency.remove(current);
                    break;
                }

                Edge nextEdge = outEdges.remove(0);
                if (outEdges.isEmpty()) {
                    adjacency.remove(current);
                }

                current = nextEdge.to();
                if (current.equals(start)) {
                    break;
                }
            }
            if (!polygon.isEmpty()) {
                polygons.add(polygon);
            }
        }
        return polygons;
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
