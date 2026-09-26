package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.SightCells.Sight;
import uz.dukeengine.core.math.Coord3D;

/**
 * The simulation's cells of what a player has seen, carried to its viewer in the snapshot for any thread to read, and
 * telling the game's ground rule whether a point was ever seen — over whatever the client said of it.
 */
class SightCellsViewTest {

    @Test
    void theViewersCellsRideTheSnapshotAndTellTheGroundRule() {
        var told = new ArrayList<Boolean>();
        var game = DukeGame.create("sight").loadUnits(DukeGame.STARTER_UNITS).map(60, 60)
                .groundOrder((DukeGame.SeenGroundOrder) (selection, place, seen) -> {
                    told.add(seen);
                    return null;
                });
        var me = game.addPlayer("Me", Color.BLUE);
        game.localPlayer(me).spawn("Rifleman", me, 100f, 100f);
        game.runHeadless(1);
        game.getLogic().setSightCells(40f, 150);
        game.runHeadless(2);

        var sight = game.getSnapshot().sight();
        assertNotNull(sight, "carried to the viewer");
        assertEquals(Sight.IN_SIGHT, sight.at(110f, 100f), "round his rifleman");
        assertEquals(Sight.NEVER_SEEN, sight.at(500f, 500f), "and nowhere near");

        var rifleman = game.getLogic().getObjects().getFirst();
        game.setSelection(List.of(rifleman.getId().value()));
        game.setPointedAt(-1, new Coord3D(500f, 500f, 0f), true); // the client says seen
        game.runHeadless(2);
        assertEquals(false, told.getLast(), "the simulation's own cells say never seen");
    }
}
