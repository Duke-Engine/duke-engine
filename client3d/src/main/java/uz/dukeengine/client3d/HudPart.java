package uz.dukeengine.client3d;

import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.view.UnitView;
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
}
