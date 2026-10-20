package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jme3.scene.Node;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.view.UnitView;

/** Tracks left by a moving tank and fading, none by a stopped one; and a wave sliding in to the shore. */
class GroundTrailsTest {

    private static final Visuals.TrackLook TREADS = new Visuals.TrackLook("textures/tread.png", 2f, 240, 60, 1f, 2);

    private static UnitView tank(float x, float speed) {
        return new UnitView(1, "Tank", 0, x, 50f, 0f, 100f, 100f, false, true, speed > 0f, false, -1, 0f, 0f, 0f,
                false, 0, List.of(), List.of(), 1f, -1, false, null, true, null, 0, 1f, false, speed);
    }

    @Test
    void aMovingTankLeavesTwoLinesThatFadeAndAStoppedOneNone() {
        var marks = new TrackMarks(new Node(), _ -> null); // no picture: laid, not drawn
        var looks = Map.of("Tank", TREADS);
        marks.see(List.of(tank(10f, 0f)), looks, 0, (_, _) -> 0f, (_, _) -> false);
        marks.see(List.of(tank(20f, 0f)), looks, 1, (_, _) -> 0f, (_, _) -> false);
        assertNull(marks.ring(TREADS), "stopped: nothing laid");

        marks.see(List.of(tank(30f, 60f)), looks, 2, (_, _) -> 0f, (_, _) -> false);
        marks.see(List.of(tank(33f, 60f)), looks, 3, (_, _) -> 0f, (_, _) -> false);
        var ring = marks.ring(TREADS);
        assertEquals(2, ring.next, "a piece behind each side");
        assertEquals(1f, ring.alpha(0, 3));
        assertEquals(0.5f, ring.alpha(0, 3 + 210), 1e-4f, "half through its fade");
        assertEquals(0f, ring.alpha(1, 3 + 240), "gone at its life's end");

        marks.see(List.of(tank(40f, 60f)), looks, 4, (_, _) -> 0f, (_, _) -> true);
        assertEquals(2, ring.next, "afloat: none");
    }

    @Test
    void aWaveSlidesInFadesAndStartsAgain() {
        var look = new Visuals.WaveLook("textures/wave.png", new Coord3D(0f, 0f, 0f), new Coord3D(10f, 0f, 0f),
                new Coord3D(0f, 20f, 0f), new Coord3D(10f, 20f, 0f), 60, 20, 90, 4f);
        assertEquals(0f, ShoreWaves.at(look, 0)[1]);
        assertEquals(10f, ShoreWaves.at(look, 30)[1], 1e-4f, "halfway in");
        assertEquals(1f, ShoreWaves.at(look, 30)[4]);
        assertEquals(0.5f, ShoreWaves.at(look, 50)[4], 1e-4f, "fading");
        assertEquals(0f, ShoreWaves.at(look, 70)[4], "spent until it starts again");
        assertEquals(1f, ShoreWaves.at(look, 90)[4], "again");
    }
}
