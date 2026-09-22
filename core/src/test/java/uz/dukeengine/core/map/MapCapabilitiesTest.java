package uz.dukeengine.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.data.Grid;
import uz.dukeengine.core.data.Paint;
import uz.dukeengine.core.data.Relief;

/**
 * A map record written from nothing, the way a game writes its own, answering every question core can ask of
 * one — and still becoming a {@link uz.dukeengine.core.pathfind.PathGrid} through the same
 * {@link MapTerrain#of} that used to know only {@code @Grid} and {@code @Relief}.
 *
 * <p>Deliberately neither a dungeon's nor a battlefield's: what core may ask has to be answerable by a record
 * that neither game in this repository wrote.
 */
class MapCapabilitiesTest {

    record Zone(String name, boolean water, float height, List<Float> points) implements MapArea {
    }

    record Army(String name, String faction, boolean human) implements MapSide {
    }

    record Standing(String template, float x, float y, float z, float facing, Map<String, String> properties)
            implements MapThing {
    }

    /** Four cells by three, a pond along one side and a bridge lying between two cells. */
    record Island(String name, String displayName, String description, int players, float cellSize,
            @Grid List<String> cells,
            @Relief List<String> relief,
            @Paint List<String> paint,
            Map<String, String> palette,
            List<Zone> areas, List<Army> sides, List<Standing> things)
            implements MapTemplate, Described, Peopled, Scaled, Painted, Zoned, Sided, Furnished {
    }

    private static Island island() {
        var palette = new LinkedHashMap<String, String>();
        palette.put("s", "sand");
        palette.put("w", "water");
        return new Island("cove", "The Cove", "Two sides and a pond.", 2, 8f,
                List.of("....",
                        "..##",
                        "...."),
                List.of("0 0 0 0 0",
                        "0 1 1 0 0",
                        "0 1 2 1 0",
                        "0 0 1 0 0"),
                List.of("ssww",
                        "ssww",
                        "ssss"),
                palette,
                List.of(new Zone("pond", true, 1f, List.of(2f, 0f, 4f, 0f, 4f, 2f, 2f, 2f)),
                        new Zone("landing", false, 0f, List.of(0f, 0f, 1f, 0f, 1f, 1f))),
                List.of(new Army("north", "Sea", true), new Army("south", "Hill", false)),
                List.of(new Standing("Lighthouse", 0.5f, 0.5f, 0f, 90f, Map.of("owner", "north")),
                        new Standing("Bridge", 1.5f, 2f, 3f, 0f, Map.of())));
    }

    @Test
    void aMapAnswersEveryQuestionCoreHasForOne() {
        var map = island();

        assertEquals("The Cove", Described.titleOf(map));
        assertEquals(2, map.players());
        assertEquals(8f, map.cellSize());

        assertEquals(List.of("s", "w"), List.copyOf(map.palette().keySet()), "in the order the file wrote them");
        assertEquals(List.of("ssww", "ssww", "ssss"), MapTerrain.rows(map, Paint.class, "paint"),
                "the paint rows are found by their mark, the way the cells are");

        var pond = map.areas().getFirst();
        assertTrue(pond.water());
        assertEquals(1f, pond.height(), "in steps, as the relief is");
        assertEquals(8, pond.points().size(), "two numbers a corner");
        assertFalse(map.areas().get(1).water());

        assertEquals(List.of("north", "south"), map.sides().stream().map(MapSide::name).toList());
        assertTrue(map.sides().getFirst().human());
        assertEquals("Sea", map.sides().getFirst().faction());

        var bridge = map.things().get(1);
        assertEquals("Bridge", bridge.template());
        assertEquals(1.5f, bridge.x(), "a thing may stand between two cells");
        assertEquals(3f, bridge.z(), "steps up");
        assertEquals(90f, map.things().getFirst().facing());
        assertEquals("north", map.things().getFirst().properties().get("owner"));
    }

    @Test
    void theSameRecordStillLaysItsGround() {
        var grid = MapTerrain.of(island(), 1f, 0f);

        assertEquals(4, grid.getWidth());
        assertEquals(3, grid.getHeight());
        assertEquals(8f, grid.getCellSize(), "the map's own cell size, not the one it was offered");
        assertFalse(grid.isTerrainBlocked(0, 0));
        assertTrue(grid.isTerrainBlocked(2, 1), "the rock in the second row");
        assertEquals(2, grid.getRelief().at(2, 2), "the relief came through untouched");
    }

    /** Each is one question a type, so a map answers only the ones it has — and a bare one still lays ground. */
    @Test
    void aMapThatSaysNothingOfPaintOrWaterIsStillAMap() {
        record Bare(String name, @Grid List<String> cells) implements MapTemplate {
        }
        // Typed as what a caller holds. A local record is final, so asking a Bare directly is a compile
        // error rather than a false — which is the same answer, earlier.
        MapTemplate bare = new Bare("bare", List.of("..", ".."));

        assertFalse(bare instanceof Painted);
        assertFalse(bare instanceof Zoned);
        assertFalse(bare instanceof Sided);
        assertFalse(bare instanceof Furnished);
        assertEquals(List.of(), MapTerrain.rows(bare, Paint.class, "paint"), "no mark, no rows, no complaint");
        assertEquals(2, MapTerrain.of(bare, 1f, 0f).getWidth());
    }
}
