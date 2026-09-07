package uz.duke.dungeon;

import uz.duke.client3d.Duke3D;
import uz.duke.client3d.Shell;
import uz.duke.client3d.Visuals;

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
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Duke3D.launch(Dungeon.create(), Visuals.create(), Shell.create()
                .entry(Shell.Entry.PLAY, "Enter the dungeon")
                .entry(Shell.Entry.SETTINGS)
                .entry(Shell.Entry.QUIT));
    }
}
