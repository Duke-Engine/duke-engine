package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.content.Laser;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.view.BeamView;

/** A beam the simulation owns, drawn as the reference draws its lasers. */
class LasersTest {

    private static final WorldMoments.Floor FLAT = (x, z) -> 0f;

    /** The orbital beam as the ask measured it: twelve lines, 26 wide outside to 0.6 inside. */
    private static final Laser ORBITAL = new Laser("OrbitalBeam", 12, 0.6f, 26f, 0xFAFFFFFF, 0x960000FF, null,
            -1.75f, true, 0.15f, 1, 0f, 0f);

    /**
     * Its picture tiled as the reference's line holds it: a tiling scalar of -3 is drawn untiled, and the orbital
     * beam's innermost line, 500 over 0.6 times 0.15 — 125 — is tiled 50 times.
     */
    @Test
    void aLinesTilesAreHeldBetweenNoneAndFifty() {
        var pictured = new Laser("OrbitalBeam", 12, 0.6f, 26f, 0xFAFFFFFF, 0x960000FF, "textures/beam.png", -1.75f,
                true, 0.15f, 1, 0f, 0f);
        var innermost = Lasers.lay(pictured, new Coord3D(0f, 0f, 500f), new Coord3D(0f, 0f, 0f), 1f, 1f, FLAT)
                .getLast();
        assertEquals(0.6f, innermost.width(), 1e-6f);
        assertEquals(50f, innermost.tiles(), "125 held to 50");

        var backwards = new Laser("Beacon", 1, 2f, 2f, 0xFFFFFFFF, 0xFFFFFFFF, "textures/beam.png", 0f, true, -3f, 1,
                0f, 0f);
        assertEquals(-3f, backwards.tilingScalar(), "kept as written");
        assertEquals(0f, Lasers.lay(backwards, new Coord3D(0f, 0f, 0f), new Coord3D(100f, 0f, 0f), 1f, 1f, FLAT)
                .getFirst().tiles(), "and drawn untiled");
    }

    @Test
    void aBeamFromHighUpToTheGroundIsLaidUpright() {
        var lines = Lasers.lay(ORBITAL, new Coord3D(100f, 200f, 500f), new Coord3D(100f, 200f, 0f), 1f, 1f, FLAT);

        assertEquals(12, lines.size());
        for (var line : lines) {
            assertEquals(new Vector3f(100f, 500f, 200f), line.from(), "from high over the point (the scene's y is up)");
            assertEquals(new Vector3f(100f, 0f, 200f), line.to(), "down to it");
        }
        assertEquals(26f, lines.getFirst().width(), 1e-4f, "the outermost laid first");
        assertEquals(0.6f, lines.getLast().width(), 1e-4f, "the innermost last");
    }

    @Test
    void itsWidthIsTimesTheShareTheSimulationGivesAndNothingDrawsNothing() {
        var half = Lasers.lay(ORBITAL, new Coord3D(0f, 0f, 500f), new Coord3D(0f, 0f, 0f), 0.5f, 1f, FLAT);
        assertEquals(13f, half.getFirst().width(), 1e-4f);
        assertTrue(Lasers.lay(ORBITAL, new Coord3D(0f, 0f, 500f), new Coord3D(0f, 0f, 0f), 0f, 1f, FLAT).isEmpty());
    }

    @Test
    void oneLineIsItsInnerColourByItsAlpha() {
        var one = new Laser("Red", 1, 2f, 2f, 0x80FF0000, 0xFFFFFFFF, null, 0f, false, 1f, 1, 0f, 0f);
        var colour = Lasers.colourOf(one, 0);
        assertEquals(128f / 255f, colour.r, 1e-4f);
        assertEquals(0f, colour.g, 1e-4f);
    }

    @Test
    void anArcRisesInTheMiddleAndItsEndsStayOverTheGround() {
        var arced = new Laser("Arc", 1, 2f, 2f, 0xFFFFFFFF, 0xFFFFFFFF, null, 0f, false, 1f, 2, 40f, 0f);
        var lines = Lasers.lay(arced, new Coord3D(0f, 0f, 0f), new Coord3D(100f, 0f, 0f), 1f, 1f, FLAT);

        assertEquals(2, lines.size(), "two segments");
        assertEquals(40f, lines.getFirst().to().y, 1e-3f, "the middle raised the whole arc");
        assertEquals(2f, lines.getFirst().from().y, 1e-3f, "an end skims the ground rather than sinking into it");
    }

    @Test
    void theDrawnBeamGoesWhereItIsMovedAndAWidthOfNothingShowsNothing() {
        var node = new Node("lasers");
        var lasers = new Lasers(new DesktopAssetManager(true), node, name -> ORBITAL, FLAT);
        var eye = new Vector3f(0f, 400f, -400f);

        lasers.show(List.of(new BeamView(1, "OrbitalBeam", new Coord3D(100f, 200f, 500f),
                new Coord3D(100f, 200f, 0f), 1f)), eye, 0f);
        assertEquals(12, shown(lasers.linesOf(1)), "its twelve lines");
        assertEquals(100f, middleX(lasers), 1e-3f);

        lasers.show(List.of(new BeamView(1, "OrbitalBeam", new Coord3D(110f, 200f, 500f),
                new Coord3D(110f, 200f, 0f), 1f)), eye, 0.1f);
        assertEquals(110f, middleX(lasers), 1e-3f, "moved ten, drawn there that frame");

        lasers.show(List.of(new BeamView(1, "OrbitalBeam", new Coord3D(110f, 200f, 500f),
                new Coord3D(110f, 200f, 0f), 0f)), eye, 0.2f);
        assertEquals(0, shown(lasers.linesOf(1)), "no width, nothing drawn");

        lasers.show(List.of(), eye, 0.3f);
        assertEquals(0, node.getQuantity(), "and a beam the simulation ended is gone");
    }

    private static int shown(List<com.jme3.scene.Geometry> lines) {
        return (int) lines.stream().filter(line -> line.getCullHint() != Spatial.CullHint.Always).count();
    }

    /** Where the outermost line's four corners stand across, on average. */
    private static float middleX(Lasers lasers) {
        var positions = lasers.linesOf(1).getFirst().getMesh().getFloatBuffer(VertexBuffer.Type.Position);
        float sum = 0f;
        for (int corner = 0; corner < 4; corner++) {
            sum += positions.get(corner * 3);
        }
        return sum / 4f;
    }
}
