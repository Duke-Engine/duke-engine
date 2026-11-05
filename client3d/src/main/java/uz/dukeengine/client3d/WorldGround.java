package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;

import java.util.List;
import java.util.function.Supplier;

import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * The world's ground as it is drawn: the terrain and its scenery, and what lies on it — the water and its shore, the
 * marks and roads the game laid, the tracks things leave, and the ring round its edge — built again for each new world.
 */
final class WorldGround {

    private final Visuals visuals;
    private final GroundMaterials groundLooks;
    private final Looks looks;
    private final Supplier<DukeGame> game;
    /** Every noise the game makes, once there are any; footsteps are told the ground under them. */
    private final Supplier<GameSounds> noises;
    private final WorldMoments.Floor floor;
    private final TerrainScene terrain;
    private final Scorches scorches;
    private final Strips strips;
    private final Water water;
    private final TrackMarks trackMarks;
    private final BorderRing border;
    private final ShoreWaves shoreWaves;
    /**
     * The grid the terrain was built from — a different instance means a new world.
     */
    private PathGrid builtFrom;
    /** The paint the ground was last built with, for what is laid over it as it is drawn. */
    private GroundPaint builtPaint;
    /** How many of the game's pieces laid along the ground are laid, and for which game. */
    private int stripsLaid;
    private DukeGame stripsOf;
    /** How many of the game's own marks on the ground are laid, and for which game. */
    private int marksLaid;
    private DukeGame marksOf;

    /**
     * @param terrainNode where the terrain hangs, alone, so a new world can replace it wholesale
     * @param listNode    where what lies on the ground hangs, beside the effects
     */
    WorldGround(AssetManager assetManager, Node terrainNode, Node listNode, Visuals visuals, GroundMaterials groundLooks,
            Looks looks, TileSource kitTiles, Supplier<DukeGame> game, Supplier<GameSounds> noises,
            WorldMoments.Floor floor) {
        this.visuals = visuals;
        this.groundLooks = groundLooks;
        this.looks = looks;
        this.game = game;
        this.noises = noises;
        this.floor = floor;
        this.terrain = new TerrainScene(terrainNode, groundLooks.surfaces, visuals.drawsFog(), visuals.getTiles(),
                kitTiles, true);
        this.scorches = new Scorches(listNode, this::drawnGround, groundLooks::scorchLook);
        this.strips = new Strips(listNode, this::drawnGround, groundLooks::stripLook, visuals::getGroundLight);
        this.water = new Water(listNode, groundLooks::waterLook);
        this.trackMarks = new TrackMarks(listNode, groundLooks::flatPicture);
        this.border = new BorderRing(listNode, assetManager);
        this.shoreWaves = new ShoreWaves(listNode, groundLooks::flatPicture);
    }

    /** The water's edge soft or hard from now — see {@link DetailPreset}. */
    void softWaterEdge(boolean on) {
        water.softEdge(on);
    }

    /** The grid the ground was last built from, or null before there was one. */
    PathGrid builtFrom() {
        return builtFrom;
    }

    /** Where the effects' lists lay their scorches. */
    Scorches scorches() {
        return scorches;
    }

    /** The ground laid for the world the game has now: its terrain, its scenery and what lies on them. */
    void build() {
        var game = this.game.get();
        builtFrom = game.getTerrain();
        builtPaint = GroundPaint.of(game.getMapRecord());
        hear();
        groundLooks.forgetScorches(); // each made for the fog of the world it was laid in
        strips.clear(); // a new world lays its own
        terrain.streamWithin(visuals.streamGroundFor(game.getMapRecord()));
        terrain.onMadeMaterial(groundLooks::adoptFogged);
        terrain.instanceScenery(visuals.getInstancedScenery(), visuals.getScenerySunCasts());
        terrain.blendWith(visuals.getPaintBlend());
        terrain.quarterWith(visuals.isPaintInQuarters());
        terrain.hideScenery(removedScenery(game.getLogic())); // what a save carried felled
        layTheGround();
        groundLooks.forgetWater(); // the new world's water makes its own
        water.rebuild(game.getMapRecord() instanceof uz.dukeengine.core.map.Zoned zoned ? zoned.areas()
                : List.of(), drawnGround(), visuals.getWater());
        border.lay(drawnGround(), visuals.getBorderCells());
        trackMarks.clear();
        shoreWaves.lay(visuals.getShoreWaves());
    }

    /** Footsteps told the ground under them, by the paint of the world now laid — see {@code GameSounds.surfaces}. */
    void hear() {
        var noises = this.noises.get();
        if (noises == null) {
            return; // the sounds are made after the first world's ground, and hear it then
        }
        var paint = builtPaint;
        var grid = builtFrom;
        noises.surfaces(paint == null || grid == null ? null : (x, y) -> paint.surfaceNameAt(
                (int) Math.floor(x / grid.mapCellSize()), (int) Math.floor(y / grid.mapCellSize())));
    }

    /** The scenery a world has taken away, by index — none where there is no world yet, as behind a splash. */
    static List<Integer> removedScenery(uz.dukeengine.core.GameLogic logic) {
        return logic == null ? List.of() : logic.getRemovedScenery();
    }

    /** The ground and its scenery laid again, as the world's terrain stands. */
    private void layTheGround() {
        var map = game.get().getMapRecord();
        terrain.rebuildLooked(builtFrom, looks.kit(), builtPaint, visuals.getGroundLight(),
                looks.kitsOfTheCells(builtFrom, map),
                map instanceof uz.dukeengine.core.map.Dressed dressed ? dressed.scenery() : List.of());
    }

    /** A piece of scenery felled or stood again. */
    void showScenery(int index, boolean shown) {
        if (terrain.showScenery(index, shown)) {
            // ponytail: a ground built whole is built whole again for one tree; streamed worlds rebuild a chunk
            layTheGround();
        }
    }

    /** What grows on the ground round the camera — see {@link Duke3D#groundCover}. */
    void cover(GroundCover cover) {
        terrain.onMadeMaterial(groundLooks::adoptFogged);
        terrain.cover(cover);
    }

    /** The ground as far as the camera sees, on a wide world. */
    void stream(float x, float z) {
        terrain.stream(x, z);
    }

    void applyDiscovery(Discovery discovery) {
        terrain.applyDiscovery(discovery);
    }

    /** What the game laid on its ground since the last frame: its marks burnt in, whatever the player sees, and its roads. */
    void layWhatTheGameLaid() {
        var game = this.game.get();
        if (marksOf != game) {
            marksOf = game;
            marksLaid = 0;
        }
        var marks = game.groundMarks();
        for (; marksLaid < marks.size(); marksLaid++) {
            var mark = marks.get(marksLaid);
            scorches.lay(mark.x(), mark.y(), mark.radius(), mark.picture());
        }
        if (stripsOf != game) {
            stripsOf = game;
            stripsLaid = 0;
        }
        var pieces = game.stripPieces();
        if (stripsLaid < pieces.size()) {
            strips.lay(List.copyOf(pieces.subList(stripsLaid, pieces.size())));
            stripsLaid = pieces.size();
        }
    }

    /** A frame of the water, the tracks things leave in the ground and the waves on its shore. */
    void see(WorldSnapshot snapshot) {
        water.update(snapshot.gameTimeSeconds());
        trackMarks.see(snapshot.units(), visuals.getTrackMarks(), snapshot.frame(), floor::at,
                (x, y) -> !Float.isNaN(water.surfaceAt(x, y)));
        shoreWaves.see(snapshot.frame(), this::waterOrGround);
    }

    /** The ring round the world's edge in the unexplored colour, whatever the look makes it. */
    void colourTheBorder(ColorRGBA unexplored) {
        border.colour(unexplored);
    }

    /** The water's height over a point, else the ground's. */
    private float waterOrGround(float worldX, float worldY) {
        float surface = water.surfaceAt(worldX, worldY);
        return Float.isNaN(surface) ? floor.at(worldX, worldY) : surface;
    }

    /** The ground as it is drawn, for a mark laid on it: its cells, its heights, and the cut each is drawn along. */
    private DrawnGround drawnGround() {
        var grid = builtFrom;
        if (grid == null) {
            return DrawnGround.NONE;
        }
        var paint = builtPaint;
        return new DrawnGround() {
            @Override
            public float cellSize() {
                return grid.getCellSize();
            }

            @Override
            public int columns() {
                return grid.getWidth();
            }

            @Override
            public int rows() {
                return grid.getHeight();
            }

            @Override
            public float heightAt(float x, float z) {
                return floor.at(x, z);
            }

            @Override
            public uz.dukeengine.core.pathfind.HeightMap.Diagonal diagonal(int cx, int cy) {
                return TerrainScene.drawnDiagonal(grid, paint, cx, cy);
            }
        };
    }
}
