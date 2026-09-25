package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.Span;
import uz.dukeengine.game.view.UnitView;

/** A thing drawn along a line: its line in the snapshot, and the thing shown wherever the ground under it is. */
class SpanTest {

    private static final String UNITS = """
            Object
              Name = Outpost
              KindOf = [STRUCTURE]
              VisionRange = 100
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
            Object
              Name = Bridge
              KindOf = [STRUCTURE]
              Modules = [
                ActiveBody
                  MaxHealth = 1000
                End
              ]
            End
            """;

    @Test
    void aBridgeCarriesItsLineAndIsShownFogOrNot() {
        var game = DukeGame.create("Spans").loadUnits(UNITS).map(80, 80);
        var me = game.addPlayer("Me", Color.CYAN);
        var them = game.addPlayer("Them", Color.RED);
        game.enemies(me, them).localPlayer(me).spawn("Outpost", me, 100f, 100f).spawn("Bridge", them, 600f, 600f);
        game.runHeadless(1);
        var bridge = game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals("Bridge"))
                .findFirst().orElseThrow();
        var line = new Span(new Coord3D(450f, 600f, 10f), new Coord3D(750f, 600f, 30f));
        game.runOnSimThread(() -> bridge.setSpan(line));
        game.runHeadless(1);

        var seen = game.getSnapshot().units().stream().filter(view -> view.templateName().equals("Bridge"))
                .map(UnitView::span).toList();
        assertEquals(java.util.List.of(line), seen, "far out in the fog, and shown as the ground there is");
        assertTrue(game.getSnapshot().units().stream().filter(view -> view.templateName().equals("Outpost"))
                .allMatch(view -> view.span() == null), "a thing drawn at its place has no line");
    }
}
