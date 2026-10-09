package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * A kind of game's part of the window: what the client draws and sends for one kind — an RTS's rally point and build
 * menu, an RPG's hero bar — beside what it does for every game. Found on the classpath as a kind of game is (a
 * {@code META-INF/services} line naming it), so a game has the RTS's part by depending on {@code rts-client-3d} and the
 * RPG's on {@code rpg-client-3d}, both if it has both, and the client's own alone if neither.
 *
 * <p>Every answer has a default that is the client's own with no part at all, so a part says only what it has. Asked
 * on the window's thread; a part never touches the world, it sends commands as the client does.
 */
public interface HudPart {

    /** The parts on the classpath, in the order of their class names, so every machine asks them alike. */
    static List<HudPart> found() {
        return ServiceLoader.load(HudPart.class).stream().map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparing(part -> part.getClass().getName())).toList();
    }

    /**
     * What a click on open ground sends one building of the player's that moves nothing — its rally point, by the
     * client's rule of a game that names no ground orders of its own — or {@code null} where this kind has none.
     */
    default Command rally(int localPlayer, UnitView building, Coord3D place) {
        return null;
    }

    /** The text of a selected building's build menu, as the client writes it along the top; empty for none. */
    default String buildMenu(DukeGame game, UnitView building) {
        return "";
    }

    /** What pressing the digit for the {@code index}-th entry of that menu sends, or {@code null}. */
    default Command build(DukeGame game, UnitView building, int index) {
        return null;
    }

    // ---- a panel at the foot of the window ----

    /** What the client hands a part as the window opens: its means of drawing and of being heard, never the world. */
    interface Window {

        AssetManager assets();

        RenderManager renderManager();

        /** The interface's node, which the part hangs what it draws on. */
        Node gui();

        BitmapFont font();

        /** The face the game named for its menus' titles, or the window's own. */
        BitmapFont titleFont();

        Visuals visuals();

        DukeGame game();

        /** A creature's body built as the client builds the ones in the world, or {@code null}. */
        Spatial body(Visuals.UnitVisual visual, Collection<String> alsoWanted);

        /** A moment of the player's own, heard: a cue of the game's sound bank. */
        void sound(String cue);

        /** The window's clock, in seconds. */
        float now();
    }

    /**
     * What the window is doing this frame, while the client's own HUD is up.
     *
     * @param tpf     how long the frame took, in seconds
     * @param drawing whether what is live is worth redrawing: no menu over it, the match not paused
     * @param picked  the one thing selected, or {@code null} for nothing or several
     * @param playing whether a match is being played rather than a menu shown
     */
    /**
     * The things whose live portraits the game's canvas drew this frame ({@link Canvas#drawPortrait}), in the order
     * asked, every frame there is a world: a part that keeps portraits keeps them up to date from here, and hands their
     * pictures out by {@link #portraitOf}.
     */
    default void portraits(java.util.Set<Integer> wanted, WorldSnapshot snapshot, float tpf) {
    }

    /** The live portrait of {@code unitId} this part keeps for the canvas, or null for none. */
    default com.jme3.texture.Texture portraitOf(int unitId) {
        return null;
    }

    record Frame(WorldSnapshot snapshot, float tpf, boolean drawing, UnitView picked, float cursorX, float cursorY,
            boolean playing) {
    }

    /** The window has opened, {@code width} by {@code height} pixels. */
    default void open(Window window, int width, int height) {
    }

    /** The window is a new size: what is drawn out of quads is built again. */
    default void resized(int width, int height) {
    }

    /** The window is closing. */
    default void close() {
    }

    /** A new world is being shown: nothing about the last is news. */
    default void newWorld() {
    }

    /**
     * Draw this frame, while the client's own HUD is up; whether the part drew the game's status line itself, so the
     * client writes none of it along the top of the window.
     */
    default boolean show(Frame frame) {
        return false;
    }

    /** Put away while a menu or a loading screen is up. */
    default void hide() {
    }

    /** A thing has died, told on the frame it did. */
    default void died(int unitId) {
    }

    /** Where its panel stands in the window, as a game's painter is told it; {@code null} for none. */
    default Canvas.Box panel() {
        return null;
    }

    /** The minimap's place in its panel — x, y, side and depth, in window pixels — or {@code null}. */
    default float[] minimapSocket() {
        return null;
    }

    /** How tall its panel stands, for a minimap in the corner above it. */
    default float panelHeight() {
        return 0f;
    }

    /** Whether a point of the window is on its panel: a click there is not one on the world. */
    default boolean covers(float x, float y) {
        return false;
    }

    // ---- the game's keys, as its panel shows them ----

    /** Whether {@code key} orders what is selected rather than doing what one thing has learnt. */
    default boolean ordersSelection(char key) {
        return false;
    }

    /** Whether what is selected takes the orders at all: it is the player's own. */
    default boolean mayOrder() {
        return true;
    }

    /** Whether {@code key} may be pressed now: what it does is ready, and paid for. */
    default boolean ready(char key) {
        return true;
    }

    /** A press on {@code key} was refused: say why, where the panel does not already show it. */
    default void refused(char key, float now) {
    }

    /** The key that waits for a click, lit on the panel; {@code null} for none. */
    default void armed(Character key) {
    }

    /** Whether a point may be spent on {@code key}. */
    default boolean raises(char key) {
        return false;
    }

    /** The key a point would be spent on by a click here, or {@code null}. */
    default Character raiseAt(float x, float y) {
        return null;
    }

    /** The key whose place on the panel is here, or {@code null}. */
    default Character keyAt(float x, float y) {
        return null;
    }

    /**
     * Who the local player's orders go to: {@code ownSelected}, what he has selected of his own, as in every game
     * until a part says otherwise — or, in a game of one hero, the hero, whatever is picked out to be looked at.
     * {@code null} where this part has no say.
     */
    default List<UnitView> ordered(WorldSnapshot snapshot, int localPlayer, List<UnitView> ownSelected) {
        return null;
    }

    /** What the pause menu says of the match beside its buttons: a word and its value, a row each. */
    default List<String[]> pauseFacts(WorldSnapshot snapshot) {
        return List.of();
    }
}
