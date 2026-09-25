package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.view.BeamView;

/** Beams the simulation owns: made, moved and ended by it, shown to whoever sees either end, and outside the sums. */
class BeamTest {

    private static final String UNITS = """
            Object
              Name = Derrick
              KindOf = [STRUCTURE]
              VisionRange = 150
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

    private static DukeGame game() {
        var game = DukeGame.create("Beams").loadUnits(UNITS).map(80, 80);
        var me = game.addPlayer("Me", Color.CYAN);
        var them = game.addPlayer("Them", Color.RED);
        game.enemies(me, them).localPlayer(me).spawn("Derrick", me, 100f, 100f).spawn("Derrick", them, 700f, 700f);
        game.runHeadless(1);
        return game;
    }

    @Test
    void aBeamIsShownFromTheFrameItIsMadeWhereItMovesAndGoneWhenEnded() {
        var game = game();
        var beam = new AtomicInteger();
        game.runOnSimThread(() -> beam.set(game.getLogic().beam("OrbitalBeam", new Coord3D(100f, 100f, 500f),
                new Coord3D(100f, 100f, 0f), 0f)));
        game.runHeadless(1);
        assertEquals(new BeamView(beam.get(), "OrbitalBeam", new Coord3D(100f, 100f, 500f),
                new Coord3D(100f, 100f, 0f), 0f), game.getSnapshot().beams().getFirst(), "upright, not yet wide");

        game.runOnSimThread(() -> game.getLogic().moveBeam(beam.get(), new Coord3D(110f, 100f, 500f),
                new Coord3D(110f, 100f, 0f), 0.5f));
        game.runHeadless(1);
        var moved = game.getSnapshot().beams().getFirst();
        assertEquals(110f, moved.to().x(), "drawn where it went, that frame");
        assertEquals(0.5f, moved.width());

        game.runOnSimThread(() -> game.getLogic().endBeam(beam.get()));
        game.runHeadless(1);
        assertTrue(game.getSnapshot().beams().isEmpty(), "ended, gone");
    }

    @Test
    void aBeamIsShownToWhoeverSeesEitherEnd() {
        var game = game();
        game.runOnSimThread(() -> {
            game.getLogic().beam("Theirs", new Coord3D(700f, 700f, 500f), new Coord3D(700f, 700f, 0f), 1f);
            game.getLogic().beam("Across", new Coord3D(700f, 700f, 10f), new Coord3D(100f, 100f, 10f), 1f);
        });
        game.runHeadless(1);

        assertEquals(java.util.List.of("Across"), game.getSnapshot().beams().stream().map(BeamView::look).toList(),
                "their own, in my fog, not; one reaching to me, yes");
    }

    @Test
    void beamsTakeNoPartInTheChecksum() {
        var quiet = game();
        var busy = game();
        busy.runOnSimThread(() -> busy.getLogic().beam("OrbitalBeam", new Coord3D(100f, 100f, 500f),
                new Coord3D(100f, 100f, 0f), 1f));
        for (int frame = 0; frame < 10; frame++) {
            quiet.runHeadless(1);
            busy.runHeadless(1);
        }
        assertEquals(quiet.getLogic().checksum(), busy.getLogic().checksum());
    }
}
