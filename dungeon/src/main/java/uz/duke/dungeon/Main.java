package uz.duke.dungeon;

import uz.duke.client3d.Duke3D;
import uz.duke.client3d.Visuals;

/**
 * Opens the dungeon in the engine's 3D client.
 *
 * <p>No visuals are bound. Every creature falls back to a coloured primitive,
 * lit and cast into a world the camera looks across rather than straight down —
 * which is the whole difference between a diagram of a game and a game.
 *
 * <p>Controls are the engine's own: left-click the hero to select him,
 * right-click the floor to walk or a skeleton to attack it.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Duke3D.launch(Dungeon.create(), Visuals.create());
    }
}
