package uz.dukeengine.client3d;

import com.jme3.math.ColorRGBA;

import java.util.function.IntFunction;

import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;

/**
 * The looks in force: the one the game names for the floor, and the ones its map's cells wear — which kit the ground is
 * built from, the colour of the dark, and what each thing is drawn as.
 *
 * <p>Everything a look changes is presentation. None of it reaches the simulation, which is what lets a floor's look be
 * decided from a seed without the world noticing.
 */
final class Looks {

    private final Visuals visuals;
    private final MissingAssets missing;
    /**
     * The look the game last named, as it wrote it: a theme and a variation.
     */
    private String currentLook;
    private Visuals.Theme currentTheme;
    private Tileset currentKit;
    /** The look each cell of the world wears, by the cell's index, where its map names looks of its own; null for none. */
    private IntFunction<Visuals.Theme> cellLooks;
    /** The grid the cells' looks were read for. */
    private PathGrid cellsOf;

    Looks(Visuals visuals, MissingAssets missing) {
        this.visuals = visuals;
        this.missing = missing;
    }

    /**
     * Take up the look the game says this floor wears.
     *
     * <p>The game names one of the themes it registered at launch, in the status
     * channel, and that is the whole of the conversation: the client holds every
     * look it was given and is told which of them is current. A game with no
     * themes never gets here, and one whose look has not changed does nothing.
     *
     * @return whether the look changed
     */
    boolean adopt(WorldSnapshot snapshot) {
        if (!visuals.hasThemes()) {
            return false;
        }
        var named = lookNamedInStatus(snapshot);
        if (java.util.Objects.equals(named, currentLook)) {
            return false;
        }
        currentLook = named;
        // Whatever the game wrote is the name of a theme it registered. The
        // client does not take the name apart: a look is one thing to it, and
        // whether the game builds that name out of a theme and a variation is
        // the game's own arrangement.
        currentTheme = visuals.getTheme(named);
        currentKit = currentTheme == null ? null : currentTheme.getTiles();
        return true;
    }

    /** The dark the floor's look wears, or null where it names none. */
    ColorRGBA floorFogTint() {
        return currentTheme == null || currentTheme.getFogTint() == null ? null
                : Materials.rgb(currentTheme.getFogTint());
    }

    /**
     * The {@code look=} field of the status line, or {@code null}.
     */
    private static String lookNamedInStatus(WorldSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasStatus()) {
            return null;
        }
        var status = snapshot.status();
        int at = status.indexOf("|look=");
        if (at < 0) {
            return null;
        }
        var field = status.substring(at + "|look=".length());
        int end = field.indexOf('|');
        return end < 0 ? field : field.substring(0, end);
    }

    /** The floor's look's kit, or null where it names none. */
    Tileset kit() {
        return currentKit;
    }

    /** The kit in force: the theme's, or the one the game started with. */
    Tileset activeKit() {
        return currentKit != null ? currentKit : visuals.getTiles();
    }

    /** How a creature is drawn: what the current theme says, or what the game said about it outside any theme. */
    Visuals.UnitVisual visualFor(String templateName) {
        var themed = currentTheme == null ? null : currentTheme.of(templateName);
        return themed != null ? themed : visuals.of(templateName);
    }

    /** How a thing is drawn, by the look of the cell it stands on where it is still — see {@link #lookOf}. */
    Visuals.UnitVisual visualFor(UnitView view) {
        return lookOf(visuals, currentTheme, lookUnder(view.x(), view.y()), view);
    }

    /**
     * How a thing is drawn, of the looks in force: a still thing on a cell that wears a look of its own ({@code
     * Looked}) as that look draws it, or as the game drew it outside any look; anything else as the floor's look draws
     * it, or as the game did. A pillar in the wood is the wood's though the floor's look is the cave's, and a creature
     * walking from one into the other stays what it was.
     */
    static Visuals.UnitVisual lookOf(Visuals visuals, Visuals.Theme floor, Visuals.Theme cell, UnitView view) {
        var theme = view.mobile() || cell == null ? floor : cell;
        var themed = theme == null ? null : theme.of(view.looksAs());
        return themed != null ? themed : visuals.of(view.looksAs());
    }

    /**
     * The kit each cell wears where the world's map names looks of its own ({@link uz.dukeengine.core.map.Looked}),
     * each a theme the game registered; null where it names none, so a map of one look is laid as it always was. A name
     * the game never registered is said once and the cell wears the map's own. On a world built round the camera
     * ({@link Visuals#streamGround}) each cell's look is asked of the map as its ground is built, and as the camera
     * stands over it, never the whole world's at once.
     */
    IntFunction<Tileset> kitsOfTheCells(PathGrid grid, Object mapRecord) {
        cellLooks = null;
        cellsOf = grid;
        if (grid == null || !visuals.hasThemes() || !(mapRecord instanceof uz.dukeengine.core.map.Looked looked)) {
            return null;
        }
        int width = grid.getWidth();
        if (visuals.streamGroundFor(mapRecord) > 0) {
            IntFunction<Visuals.Theme> look = cell -> themeNamed(looked.lookAt(cell % width, cell / width));
            cellLooks = look;
            return cell -> {
                var theme = look.apply(cell);
                return theme == null ? null : theme.getTiles();
            };
        }
        var looks = new Visuals.Theme[width * grid.getHeight()];
        var kits = new Tileset[looks.length];
        boolean any = false;
        for (int cell = 0; cell < looks.length; cell++) {
            var theme = themeNamed(looked.lookAt(cell % width, cell / width));
            if (theme == null) {
                continue;
            }
            looks[cell] = theme;
            kits[cell] = theme.getTiles();
            any = true;
        }
        if (!any) {
            return null;
        }
        cellLooks = cell -> looks[cell];
        return cell -> kits[cell];
    }

    /** The theme a map's look names; null for none, and for one the game never registered, said once. */
    private Visuals.Theme themeNamed(String name) {
        if (name == null) {
            return null;
        }
        var theme = visuals.getTheme(name);
        if (theme == null) {
            missing.warn(name, "look");
        }
        return theme;
    }

    /** Whether the world's cells wear looks of their own. */
    boolean byCell() {
        return cellLooks != null;
    }

    /** The look of the cell under a point of the world, where the world's map names one there; null for none. */
    Visuals.Theme lookUnder(float x, float y) {
        if (cellLooks == null || cellsOf == null) {
            return null;
        }
        float cell = cellsOf.getCellSize();
        int cx = Math.clamp((int) Math.floor(x / cell), 0, cellsOf.getWidth() - 1);
        int cy = Math.clamp((int) Math.floor(y / cell), 0, cellsOf.getHeight() - 1);
        return cellLooks.apply(cy * cellsOf.getWidth() + cx);
    }

    /** The dark a look wears: its own, or the map's look's, or the game's fog's. */
    ColorRGBA fogTintOf(Visuals.Theme look) {
        for (var theme : new Visuals.Theme[] {look, currentTheme}) {
            if (theme != null && theme.getFogTint() != null) {
                return Materials.rgb(theme.getFogTint());
            }
        }
        return visuals.getFog().tintColour();
    }
}
