package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Marks a map burns into its ground as it loads: kept with the ground whatever any player sees, as the reference lays a
 * map's own scorches — not events, which a player who cannot see the place never gets.
 */
class GroundMarksTest {

    @Test
    void aMarkLaidAtLoadWhereNoPlayerSeesIsKeptForTheGround() {
        var game = DukeGame.create("Scorched").loadUnits(DukeGame.STARTER_UNITS).map(60, 60);
        var player = game.addPlayer("USA", Color.BLUE);
        game.localPlayer(player).markGround("textures/scorch_2.png", 500f, 520f, 60f);
        game.runHeadless(3);
        game.markGround("textures/scorch_0.png", 40f, 40f, 9f);

        assertEquals(List.of(new DukeGame.GroundMark("textures/scorch_2.png", 500f, 520f, 60f),
                new DukeGame.GroundMark("textures/scorch_0.png", 40f, 40f, 9f)), game.groundMarks(),
                "both, in the order laid, however far from anything that sees");
        assertEquals(true, game.getSnapshot().events().stream()
                .noneMatch(event -> event.getClass().getSimpleName().contains("Scorch")), "and no event of it");
    }
}
