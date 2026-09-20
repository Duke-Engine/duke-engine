package uz.dukeengine.dungeon.stage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import uz.dukeengine.core.pathfind.MapLoader;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Placement;

/**
 * Everything that can be wrong with a stage, said out loud.
 *
 * <p>The generator does not need this. It guarantees a walkable dungeon by
 * construction — the corridors are a spanning tree — and re-checks the guarantee
 * itself the moment height can take it away. A stage has no such guarantee:
 * somebody opened the file and moved something, and a file is a place where a
 * monster can be put inside a wall.
 *
 * <p>So the guarantee is kept the only way it can be once a person is holding the
 * pen: the dungeon is walked with <b>the engine's own rule for what a step is</b>
 * ({@link PathGrid#canStep}), and anything it cannot reach is reported. Not the
 * builder's idea of a step, and not a copy of it that would drift — the same
 * method the pathfinder will use when the stage is played. A check that agreed
 * with itself and disagreed with the game would be worse than no check.
 *
 * <p>The answer is a <em>list</em> rather than the first problem found. The world
 * builder shows it while the author works, and an author who has to fix one
 * mistake, save, and be told about the next one has been told the truth one
 * sentence at a time.
 */
public final class StageCheck {

    private StageCheck() {
    }

    /** What is wrong with this stage, in the order it was found; empty if nothing. */
    public static List<String> problems(Stage stage, DungeonSettings settings) {
        var problems = new ArrayList<String>();
        var floor = stage.floor();

        problems.addAll(layersAgree(floor.asciiMap(), floor.levelMap()));
        if (!problems.isEmpty()) {
            return List.copyOf(problems); // no grid to build, so nothing else can be asked
        }

        PathGrid grid;
        try {
            grid = MapLoader.fromText(floor.asciiMap());
            MapLoader.levels(grid, floor.levelMap());
            grid.setRelief(floor.relief());
        } catch (RuntimeException e) {
            problems.add("the map itself cannot be read: " + e.getMessage());
            return List.copyOf(problems);
        }

        var kinds = new HashSet<String>();
        for (var kind : settings.monsters()) {
            kinds.add(kind.name());
        }
        var propKinds = new HashSet<String>();
        for (var kind : settings.propKinds()) {
            propKinds.add(kind.template());
        }

        // One cell, one thing. Kept as a map rather than a set so the complaint can
        // name what was already standing there — "on top of something" sends an
        // author looking, "on top of the Pillar" sends him to the right line.
        var taken = new HashMap<Long, String>();

        // The difficulty is the depth it is fought at, so zero is not "easy" — it
        // is a floor above the first one, which the scaling has no meaning for.
        if (stage.difficulty() < 1) {
            problems.add("a difficulty of " + stage.difficulty() + " is not a depth;"
                    + " the shallowest floor in this game is 1");
        }

        var entrance = floor.hero();
        if (entrance == null) {
            problems.add("no entrance: nowhere for the hero to come in");
        } else {
            standsOn(problems, grid, taken, "the entrance", entrance);
        }

        var boss = floor.boss();
        if (boss == null || boss.kind() == null || boss.kind().isBlank()) {
            problems.add("no boss: a stage is won by killing one, so it cannot be won");
        } else {
            if (!kinds.contains(boss.kind())) {
                problems.add("the boss is a " + boss.kind()
                        + ", which is not a kind this game has — see the Monster"
                        + " blocks in data/units/");
            }
            if (boss.at() == null) {
                problems.add("the boss " + boss.kind() + " has no cell to stand in");
            } else {
                standsOn(problems, grid, taken, "the boss " + boss.kind(), boss.at());
            }
        }

        for (var monster : floor.monsters()) {
            if (!kinds.contains(monster.kind())) {
                problems.add("there is no such monster as a " + monster.kind()
                        + " — see the Monster blocks in data/units/");
            }
            standsOn(problems, grid, taken, "the " + monster.kind(), monster.at());
        }

        for (var prop : floor.props()) {
            if (!propKinds.contains(prop.kind())) {
                problems.add("there is no such thing as a " + prop.kind()
                        + " — see the Prop blocks in data/props/");
            }
            standsOn(problems, grid, taken, "the " + prop.kind(), prop.at());
            // A prop is solid, and a solid thing on a stair is a stair the widest
            // creature cannot climb. See PropsTest, which pins the same rule on the
            // generator.
            if (prop.at() != null && inBounds(grid, prop.at()) && grid.isRamp(
                    prop.at().cellX(), prop.at().cellY())) {
                problems.add("the " + prop.kind() + " at " + where(prop.at())
                        + " is standing on the stairs");
            }
        }

        if (entrance != null && inBounds(grid, entrance)) {
            problems.addAll(unreachable(grid, entrance, stage));
        }
        return List.copyOf(problems);
    }

    /**
     * Everywhere the hero cannot walk to from the way in.
     *
     * <p>A flood fill rather than a path each time: the question is not how to get
     * somewhere but whether anywhere is cut off, and one walk answers it for the
     * whole floor at once.
     */
    private static List<String> unreachable(PathGrid grid, Placement entrance, Stage stage) {
        var problems = new ArrayList<String>();
        var floor = stage.floor();
        var reached = reachable(grid, entrance.cellX(), entrance.cellY());

        for (int i = 0; i < floor.rooms().size(); i++) {
            var room = floor.rooms().get(i);
            if (!at(grid, reached, room.centerCellX(), room.centerCellY())) {
                problems.add("room " + i + " at " + room.centerCellX() + "," + room.centerCellY()
                        + " cannot be walked to from the entrance");
            }
        }
        if (floor.boss() != null && floor.boss().at() != null
                && !at(grid, reached, floor.boss().at().cellX(), floor.boss().at().cellY())) {
            problems.add("the boss at " + where(floor.boss().at())
                    + " cannot be walked to, so the stage cannot be won");
        }
        for (var monster : floor.monsters()) {
            if (monster.at() != null
                    && !at(grid, reached, monster.at().cellX(), monster.at().cellY())) {
                problems.add("the " + monster.kind() + " at " + where(monster.at())
                        + " is walled off from the rest of the floor");
            }
        }
        return problems;
    }

    /** Every cell the engine would let the hero walk to, starting from the way in. */
    private static boolean[] reachable(PathGrid grid, int fromX, int fromY) {
        var reached = new boolean[grid.getWidth() * grid.getHeight()];
        if (grid.isBlocked(fromX, fromY)) {
            return reached;
        }
        var queue = new ArrayDeque<int[]>();
        reached[fromY * grid.getWidth() + fromX] = true;
        queue.add(new int[] {fromX, fromY});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            var here = queue.poll();
            for (var step : steps) {
                int x = here[0] + step[0];
                int y = here[1] + step[1];
                if (!grid.inBounds(x, y) || reached[y * grid.getWidth() + x]
                        || !grid.canStep(here[0], here[1], x, y)) {
                    continue;
                }
                reached[y * grid.getWidth() + x] = true;
                queue.add(new int[] {x, y});
            }
        }
        return reached;
    }

    private static boolean at(PathGrid grid, boolean[] reached, int cx, int cy) {
        return grid.inBounds(cx, cy) && reached[cy * grid.getWidth() + cx];
    }

    /**
     * Whether something can stand where the file puts it — on the map, on floor,
     * and on its own.
     *
     * <p>The last of the three is the one that has already cost this game a run:
     * a solid thing dropped on a skeleton leaves the skeleton inside an obstacle,
     * a search that begins on blocked ground finds no path at all, and that
     * skeleton never moves again.
     */
    private static void standsOn(List<String> problems, PathGrid grid, Map<Long, String> taken,
            String what, Placement at) {
        if (at == null) {
            return; // already reported as missing
        }
        if (!inBounds(grid, at)) {
            problems.add(what + " at " + where(at) + " is off the edge of the map");
            return;
        }
        if (grid.isBlocked(at.cellX(), at.cellY())) {
            problems.add(what + " at " + where(at) + " is inside stone");
        } else if (grid.getRelief() != null && grid.getRelief().isCliff(at.cellX(), at.cellY())) {
            problems.add(what + " at " + where(at) + " is on a slope too steep to stand on");
        }
        var already = taken.putIfAbsent(key(at), what);
        if (already != null) {
            problems.add(what + " at " + where(at) + " is standing on " + already);
        }
    }

    /**
     * Whether the two layers are pictures of the same building.
     *
     * <p>{@code MapLoader} reads them separately and neither knows the other's
     * size, so a storey map one row short is a bottom row that quietly reads as
     * the ground floor — a stage that plays almost right, which is the worst kind.
     */
    private static List<String> layersAgree(String walls, String heights) {
        var problems = new ArrayList<String>();
        if (walls == null || walls.isBlank() || heights == null || heights.isBlank()) {
            problems.add("a stage needs both of its map layers, and one of them is empty");
            return problems;
        }
        var wallRows = walls.strip().split("\n");
        var heightRows = heights.strip().split("\n");
        if (wallRows.length != heightRows.length) {
            problems.add("the two map layers are different heights: the terrain is "
                    + wallRows.length + " rows and the storeys are " + heightRows.length);
            return problems;
        }
        for (int row = 0; row < wallRows.length; row++) {
            if (wallRows[row].length() != heightRows[row].length()) {
                problems.add("row " + row + " is " + wallRows[row].length()
                        + " cells of terrain and " + heightRows[row].length() + " of storeys");
                return problems; // one is enough; the file was cut wrong, not mistyped
            }
        }
        return problems;
    }

    private static boolean inBounds(PathGrid grid, Placement at) {
        return grid.inBounds(at.cellX(), at.cellY());
    }

    private static long key(Placement at) {
        return ((long) at.cellY() << 32) | at.cellX();
    }

    private static String where(Placement at) {
        return at.cellX() + "," + at.cellY();
    }
}
