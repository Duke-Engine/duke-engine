package uz.dukeengine.game;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.map.Dressed;
import uz.dukeengine.core.map.Linked;
import uz.dukeengine.core.map.Sectored;
import uz.dukeengine.core.map.Subdivided;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.SceneryBox;
import uz.dukeengine.core.pathfind.SceneryFootprint;
import uz.dukeengine.core.pathfind.TraversalLink;

/** The map the simulation walks: its grid, the record it was laid from, and what that record asks of the pathfinder. */
final class TerrainSetup {

    private PathGrid terrain;
    /** The map record {@code terrain} was laid from, for a client that wants more of the map than its cells. */
    private Object mapRecord;

    PathGrid grid() {
        return terrain;
    }

    Object mapRecord() {
        return mapRecord;
    }

    void setGrid(PathGrid grid) {
        this.terrain = grid;
    }

    void set(PathGrid grid, Object map) {
        this.terrain = grid;
        this.mapRecord = map;
    }

    /** Hand the grid and everything the record asks of the pathfinder to the simulation. */
    void applyTo(GameLogic logic) {
        logic.setPathGrid(terrain, mapRecord instanceof Subdivided subdivided
                ? Math.max(1, subdivided.navigationCellsPerCell()) : 1);
        logic.setRouteSectors(mapRecord instanceof Sectored sectored ? Math.max(0, sectored.routeSectors()) : 0);
        logic.setSceneryFootprints(sceneryFootprints());
        logic.setTraversalLinks(traversalLinks());
        logic.setSceneryBoxes(sceneryBoxes());
    }

    /** The map's dressing, where there is a grid to size it by. */
    private Dressed dressed() {
        return terrain != null && mapRecord instanceof Dressed dressed ? dressed : null;
    }

    /** The scenery that stands in the way (each piece with a footprint), in world units. */
    private List<SceneryFootprint> sceneryFootprints() {
        var dressed = dressed();
        if (dressed == null) {
            return List.of();
        }
        float cell = terrain.getCellSize();
        var footprints = new ArrayList<SceneryFootprint>();
        var pieces = dressed.scenery();
        for (int at = 0; at < pieces.size(); at++) {
            var piece = pieces.get(at);
            if (piece.footprint() > 0f) {
                footprints.add(new SceneryFootprint(piece.x() * cell, piece.y() * cell,
                        piece.footprint() * cell, at));
            }
        }
        return footprints;
    }

    /** The map's ways across what no walk crosses ({@code Linked}), in world units. */
    private List<TraversalLink> traversalLinks() {
        if (terrain == null || !(mapRecord instanceof Linked linked)) {
            return List.of();
        }
        float cell = terrain.getCellSize();
        return linked.traversalLinks().stream().map(link -> link.scaled(cell)).toList();
    }

    /** The scenery that stands in the way along its length, in world units. */
    private List<SceneryBox> sceneryBoxes() {
        var dressed = dressed();
        if (dressed == null) {
            return List.of();
        }
        float cell = terrain.getCellSize();
        var boxes = new ArrayList<SceneryBox>();
        for (var piece : dressed.scenery()) {
            if (piece.boxLength() > 0f) {
                boxes.add(new SceneryBox(piece.x() * cell, piece.y() * cell,
                        piece.boxLength() * cell, piece.boxDepth() * cell, piece.facing()));
            }
        }
        return boxes;
    }
}
