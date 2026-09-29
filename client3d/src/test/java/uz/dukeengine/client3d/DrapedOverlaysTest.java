package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/**
 * Every drawing on the ground lies over the ground's rise and fall: each point of a ring and its wash, a skill's lane,
 * an aim's picture, an order's arrowheads and the disc under a selected thing stands on the ground under it, and no
 * further than a step from the next, so a hill between two of them cannot rise through the drawing.
 */
class DrapedOverlaysTest {

    private final DesktopAssetManager assets = new DesktopAssetManager(true);

    /** A hill 30 high round (100, 100), its sides falling 0.6 a unit: as steep as the dungeon's rises. */
    private static float hill(float x, float y) {
        float away = (float) Math.sqrt((x - 100f) * (x - 100f) + (y - 100f) * (y - 100f));
        return Math.max(0f, 30f - 0.6f * away);
    }

    /** Every point of {@code geometry}, where it stands in the world. */
    private static List<Vector3f> inTheWorld(Node root, Geometry geometry) {
        root.updateGeometricState();
        var positions = geometry.getMesh().getFloatBuffer(VertexBuffer.Type.Position);
        var points = new ArrayList<Vector3f>();
        for (int i = 0; i < geometry.getMesh().getVertexCount(); i++) {
            var local = new Vector3f(positions.get(i * 3), positions.get(i * 3 + 1), positions.get(i * 3 + 2));
            points.add(geometry.getWorldTransform().transformVector(local, null));
        }
        return points;
    }

    private static void assertOnTheHill(List<Vector3f> points, float lift) {
        assertTrue(points.size() > 20, "a drawing cut finely enough to follow a hill: " + points.size());
        for (var point : points) {
            assertEquals(hill(point.x, point.z) + lift, point.y, 1e-2f, "on the hill at " + point);
        }
    }

    @Test
    void aRingAndItsWashLieOverTheHill() {
        var root = new Node("world");
        var ring = new GroundRing(assets, root, 1.6f, 96, 1.4f);

        ring.show(new Coord3D(125f, 100f, 0f), 20f, 0.3f, 0xFFFFFF, 1f, 0.2f, DrapedOverlaysTest::hill);

        var band = inTheWorld(root, (Geometry) ring.node().getChild("band"));
        assertOnTheHill(band, 0.3f);
        assertOnTheHill(inTheWorld(root, (Geometry) ring.node().getChild("wash")), 0.3f);
        for (int i = 2; i < band.size(); i += 2) {
            float across = (float) Math.hypot(band.get(i).x - band.get(i - 2).x, band.get(i).z - band.get(i - 2).z);
            assertTrue(across <= GroundRing.STEP + 1e-3f, "its points a step apart at most: " + across);
        }
    }

    @Test
    void aShotsLaneLiesUpTheHill() {
        var root = new Node("world");
        var rings = new RangeRings(assets, root, RangeLook.DEFAULT);

        rings.show(new SkillRange('Q', SkillRange.Shape.DOWN_A_LANE, 110f, 6f), new Coord3D(40f, 110f, 0f),
                new Coord3D(170f, 100f, 0f), true, 0f, DrapedOverlaysTest::hill);

        assertOnTheHill(inTheWorld(root, (Geometry) root.getChild("lane-band")), RangeLook.DEFAULT.height());
    }

    @Test
    void anAimsPictureLiesOverTheHillOnCellsOfAStep() {
        var root = new Node("world");
        var decal = new GroundDecal(assets, root);

        decal.show(new Coord3D(100f, 100f, 0f), 60f,
                new AimDecal("Common/Textures/dot.png", 1f, 1f, 40, 0xFFFFFF), 0, DrapedOverlaysTest::hill);

        var points = inTheWorld(root, (Geometry) decal.node().getChild(0));
        assertOnTheHill(points, GroundDecal.LIFT);
        assertTrue(points.get(1).x - points.get(0).x <= GroundRing.STEP,
                "a cone 120 across laid on cells a step wide, not eight: " + (points.get(1).x - points.get(0).x));
    }

    @Test
    void anOrdersArrowheadsStandOnTheHillUnderEach() {
        var root = new Node("world");
        var chevrons = new Chevrons(assets, root, OrderMark.DEFAULTS);
        var orders = new OrderMarkers();
        orders.add(125f, 100f, OrderMarkers.Kind.MOVE, 100f);

        chevrons.show(orders.markers(), 100f, DrapedOverlaysTest::hill);

        root.updateGeometricState();
        var mark = (Node) root.getChild(0);
        for (Spatial head : mark.getChildren()) {
            var at = head.getWorldTranslation();
            assertEquals(hill(at.x, at.z) + OrderMark.DEFAULTS.height(), at.y, 1e-2f, "on the hill at " + at);
        }
    }

    @Test
    void theDiscUnderASelectedThingLiesOverTheHill() {
        var disc = new GroundDisc(24);
        var mesh = new Mesh();
        var at = new Coord3D(120f, 100f, 0f);

        disc.lay(mesh, at, 4.2f, hill(120f, 100f), DrapedOverlaysTest::hill);

        var root = new Node("world");
        var geometry = new Geometry("disc", mesh);
        geometry.setLocalTranslation(at.x(), hill(at.x(), at.y()), at.y());
        root.attachChild(geometry);
        assertOnTheHill(inTheWorld(root, geometry), 0f);
    }
}
