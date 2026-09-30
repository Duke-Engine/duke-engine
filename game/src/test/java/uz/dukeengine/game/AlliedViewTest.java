package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.UnitView;

/** The snapshot says of each thing whether it is the viewer's side's, for a look that shows its own side more. */
class AlliedViewTest {

    private static final String UNITS = """
            Object
              Name = Outpost
              KindOf = [STRUCTURE]
              VisionRange = 400
              Geometry = Cylinder
                Radius = 8
                Height = 12
              End
              Modules = [
                ActiveBody
                  MaxHealth = 500
                End
              ]
            End
            """;

    @Test
    void itsOwnAndAnAllysThingsAreAlliedAndAnEnemysAreNot() {
        var game = DukeGame.create("Allied").loadUnits(UNITS).map(80, 80);
        var me = game.addPlayer("Me", Color.CYAN);
        var friend = game.addPlayer("Friend", Color.GREEN);
        var foe = game.addPlayer("Foe", Color.RED);
        game.allies(me, friend).enemies(me, foe).enemies(friend, foe).localPlayer(me)
                .spawn("Outpost", me, 100f, 100f).spawn("Outpost", friend, 150f, 100f)
                .spawn("Outpost", foe, 200f, 100f);
        game.runHeadless(2);

        Map<Integer, Boolean> allied = game.getSnapshot().units().stream()
                .collect(Collectors.toMap(UnitView::playerIndex, UnitView::allied));

        assertEquals(Map.of(me.getIndex(), true, friend.getIndex(), true, foe.getIndex(), false), allied);
    }
}
