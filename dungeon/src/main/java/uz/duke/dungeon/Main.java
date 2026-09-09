package uz.duke.dungeon;

import uz.duke.client3d.Duke3D;
import uz.duke.client3d.Shell;
import uz.duke.client3d.Visuals;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * Opens the dungeon in the engine's 3D client.
 *
 * <p>No visuals are bound. Every creature falls back to a coloured primitive,
 * lit and cast into a world the camera looks across rather than straight down —
 * which is the whole difference between a diagram of a game and a game.
 *
 * <p>The front menu is this game's, not the client's. A dungeon has a run to
 * begin and a way out, and nothing else: it is one player against the dungeon,
 * so it does not offer to host a LAN game — which the client used to, on the
 * grounds that the monsters count as a second player.
 *
 * <p>Controls are the engine's own: left-click the hero to select him,
 * right-click the floor to walk or a skeleton to attack it.
 *
 * <p>Each launch draws a different dungeon. The seed is the one thing here the
 * clock touches — and it is outside the simulation, choosing <em>which</em>
 * deterministic dungeon to play rather than reaching into how one is built. From
 * that seed on, generation and the run loop are a pure, reproducible chain.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        var settings = DungeonSettings.load();
        Duke3D.launch(Dungeon.create(System.nanoTime(), settings), looks(settings), Shell.create()
                .entry(Shell.Entry.PLAY, "Enter the dungeon")
                .entry(Shell.Entry.SETTINGS)
                .entry(Shell.Entry.QUIT));
    }

    /**
     * What each kind of monster looks like, taken from the same file that says how
     * it behaves.
     *
     * <p>With no models yet, colour and size are the only things telling one
     * monster from another — and telling a runner from a brute is a decision the
     * player has to make in the second before they reach him. Player colour cannot
     * do it: every monster belongs to the same side, so they would all be one
     * shade of red, on screen and on the minimap alike.
     */
    private static Visuals looks(DungeonSettings settings) {
        var visuals = Visuals.create();
        for (var kind : settings.monsters()) {
            visuals.unit(kind.name(), unit -> unit.colour(kind.awtColour()).scale(kind.scale()));
        }
        // The floor is black until he walks it. Named rather than given a
        // distance: the radius is the hero's own VisionRange from creatures.ini,
        // which is also what the engine's fog uses to decide whether a monster is
        // on screen — so the ground he uncovers and the things he can see are the
        // same number, and re-tuning one cannot leave the other behind.
        visuals.discoveredBy("Hero");
        return visuals;
    }
}
