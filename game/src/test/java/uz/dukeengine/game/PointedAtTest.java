package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/** A game reads what the window's pointer is on, with nothing of the player's selected. */
class PointedAtTest {

    @Test
    void whatThePointerIsOnIsReadWhateverIsSelected() {
        var game = DukeGame.create("Pointed");
        assertEquals(-1, game.getPointedAt(), "nothing under the pointer yet");

        game.setPointedAt(7);
        assertEquals(7, game.getPointedAt(), "a chest on the floor, nothing selected");

        game.setPointedAt(-1, new Coord3D(40f, 60f, 0f));
        assertEquals(-1, game.getPointedAt(), "the bare ground");
    }

    @Test
    void theGroundUnderThePointerIsReadAndIsNoneWhereItIsOnAThing() {
        var game = DukeGame.create("PointedGround");
        assertNull(game.getPointedGround(), "nothing said yet");

        var place = new Coord3D(40f, 60f, 0f);
        game.setPointedAt(-1, place);
        assertEquals(place, game.getPointedGround());

        game.setPointedAt(7, place);
        assertNull(game.getPointedGround(), "on a chest, the ground behind it is not what the pointer is on");
    }
}
