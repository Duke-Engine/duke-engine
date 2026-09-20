package uz.dukeengine.core.map;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.List;
import uz.dukeengine.core.data.Grid;
import uz.dukeengine.core.data.Relief;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.core.pathfind.MapLoader;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.Layered;

/**
 * The ground a map is played on, laid from the map itself.
 *
 * <p>A game's map is the game's own record — a dungeon's floor, an island, a street — and the engine knows two
 * things about it: the component marked {@link Grid} is its cells, and the one marked {@link Relief} is how
 * those cells rise and fall. That is the whole contract. Everything else on the record is the game's, and the
 * engine never looks at it.
 *
 * <p>So a game that has written a map record gets its pathfinding grid, its storeys, its stairs and its hills
 * for nothing, and an editor that can draw those rows can draw any game's maps — which is the point of putting
 * the marks on the components rather than teaching the engine what a dungeon is.
 */
public final class MapTerrain {

    private MapTerrain() {
    }

    /**
     * The grid a map is played on: its cells as walls and floors, the storey each stands on, and the relief
     * over them.
     *
     * @param map        the game's own map record, already read from its file
     * @param cellSize   how wide a cell is where the map does not say — see {@link Scaled}
     * @param levelHeight how tall a storey is where the map does not say — see {@link Layered}
     */
    public static PathGrid of(Object map, float cellSize, float levelHeight) {
        var cells = rows(map, Grid.class, "cells");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException(named(map) + " is drawn on no cells: a map's rows are the component"
                    + " marked @Grid");
        }
        var solid = solidOf(map);
        var grid = MapLoader.fromText(laid(cells, solid, true), sizeOf(map, cellSize));
        MapLoader.levels(grid, laid(cells, solid, false));
        grid.setLevelHeight(heightOf(map, levelHeight));
        var relief = rows(map, Relief.class, "relief");
        if (!relief.isEmpty()) {
            grid.setRelief(HeightMap.parse(relief));
        }
        return grid;
    }

    /** How wide a cell of this map is: the map's own, where it says, else the one it is offered. */
    public static float sizeOf(Object map, float otherwise) {
        return map instanceof Scaled scaled && scaled.cellSize() > 0f ? scaled.cellSize() : otherwise;
    }

    /** How tall a storey of this map is: the map's own, where it says, else the one it is offered. */
    public static float heightOf(Object map, float otherwise) {
        return map instanceof Layered layered && layered.levelHeight() > 0f ? layered.levelHeight() : otherwise;
    }

    /** The rows of the component that carries this mark, or none where the record has no such component. */
    public static List<String> rows(Object map, Class<? extends Annotation> mark, String what) {
        var component = markedWith(map, mark);
        if (component == null) {
            return List.of();
        }
        try {
            var read = component.getAccessor().invoke(map);
            if (!(read instanceof List<?> rows)) {
                throw new IllegalArgumentException(named(map) + "'s " + what + " is written as rows of text");
            }
            return rows.stream().map(String::valueOf).toList();
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("could not read the " + what + " of " + named(map), e);
        }
    }

    /** The characters this map's cells call rock; everything else is walked on. */
    public static String solidOf(Object map) {
        var component = markedWith(map, Grid.class);
        var mark = component == null ? null : component.getAnnotation(Grid.class);
        return mark == null || mark.solid().isEmpty() ? "#" : mark.solid();
    }

    private static RecordComponent markedWith(Object map, Class<? extends Annotation> mark) {
        if (map == null || !map.getClass().isRecord()) {
            return null;
        }
        for (var component : map.getClass().getRecordComponents()) {
            if (component.getAnnotation(mark) != null) {
                return component;
            }
        }
        return null;
    }

    /**
     * The cells as the engine reads them, whatever characters a game drew them in: what the map calls rock is
     * {@code #}, and the rest is floor — flattened to {@code .} for what may be walked on, kept as it was
     * written for which storey it stands on and where its stairs are.
     */
    private static String laid(List<String> cells, String solid, boolean flat) {
        var rows = new StringBuilder();
        for (var row : cells) {
            if (!rows.isEmpty()) {
                rows.append('\n');
            }
            for (int at = 0; at < row.length(); at++) {
                char cell = row.charAt(at);
                rows.append(solid.indexOf(cell) >= 0 ? '#' : flat ? '.' : cell);
            }
        }
        return rows.toString();
    }

    private static String named(Object map) {
        return map instanceof MapTemplate template ? "the map '" + template.name() + "'"
                : "the map " + (map == null ? "(none)" : map.getClass().getSimpleName());
    }
}
