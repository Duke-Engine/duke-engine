package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.game.view.StreamView;

/** Things riding a stream, as the reference's {@code ProjectileStreamUpdate} keeps its shots, and the view of it. */
class StreamsTest {

    private record Field(DukeGame game, List<GameObject> shots) {

        StreamView flame() {
            return game.getSnapshot().streams().stream().filter(view -> view.name().equals("Flame")).findFirst()
                    .orElse(null);
        }
    }

    private static Field field(int shots) {
        var game = DukeGame.create("streams").loadUnits(DukeGame.STARTER_UNITS).map(40, 40);
        var me = game.addPlayer("Me", Color.BLUE);
        game.localPlayer(me);
        for (int shot = 0; shot < shots; shot++) {
            game.spawn("Rifleman", me, 100f + 20f * shot, 100f);
        }
        game.runHeadless(1);
        return new Field(game, List.copyOf(game.getLogic().getObjects()));
    }

    @Test
    void fivePointsWithAGapAfterTheSecondAreTwoPiecesNotOneThroughTheGap() {
        var field = field(5);
        var logic = field.game().getLogic();
        for (int shot = 0; shot < 5; shot++) {
            logic.rideStream("Flame", field.shots().get(shot), 20);
            if (shot == 1) {
                logic.breakStream("Flame");
            }
        }
        field.game().runHeadless(1);
        var pieces = field.flame().pieces();
        assertEquals(2, pieces.size(), "two ribbon pieces");
        assertEquals(2, pieces.get(0).size());
        assertEquals(3, pieces.get(1).size());
    }

    @Test
    void aShotThatLandsIsGoneTheNextFrameAndANewOneRidesAtOnce() {
        var field = field(4);
        var logic = field.game().getLogic();
        for (int shot = 0; shot < 3; shot++) {
            logic.rideStream("Flame", field.shots().get(shot), 20);
        }
        field.shots().get(1).markDestroyed();
        logic.rideStream("Flame", field.shots().get(3), 20);
        field.game().runHeadless(1);
        var piece = field.flame().pieces().getFirst();
        assertEquals(List.of(160f, 100f, 140f), List.of(piece.get(2).x(), piece.get(0).x(), piece.get(1).x()),
                "the landed one gone, the new one riding, in the order they started");
        assertEquals(3, piece.size());
    }

    @Test
    void aStreamWithNoGapIsOneRibbonThroughEveryPointInOrderAndKeepsNoMoreThanItsMost() {
        var field = field(5);
        var logic = field.game().getLogic();
        for (var shot : field.shots()) {
            logic.rideStream("Flame", shot, 4);
        }
        field.game().runHeadless(1);
        var pieces = field.flame().pieces();
        assertEquals(1, pieces.size(), "one ribbon");
        assertEquals(List.of(120f, 140f, 160f, 180f), pieces.getFirst().stream().map(at -> at.x()).toList(),
                "the latest four, in the order added");
    }
}
