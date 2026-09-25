package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/** A bridge laid from bank to bank as its model's pieces, stretched to end where the line does. */
class LinePiecesTest {

    private static final Visuals.UnitVisual.LineLook LOOK =
            new Visuals.UnitVisual.LineLook("BRIDGE_LEFT", "BRIDGE_SPAN", "BRIDGE_RIGHT", 1f, 1f);

    /** Its left, its span and its right, end to end along x: 73, 65 and 73 long, 20 wide. */
    private static Node bridge() {
        var model = new Node("bridge");
        model.attachChild(piece("BRIDGE_LEFT", 0f, 73f));
        model.attachChild(piece("BRIDGE_SPAN", 73f, 138f));
        model.attachChild(piece("BRIDGE_RIGHT", 138f, 211f));
        return model;
    }

    private static Geometry piece(String name, float from, float to) {
        var geometry = new Geometry(name, new Box((to - from) / 2f, 2f, 10f));
        geometry.setLocalTranslation((from + to) / 2f, 0f, 0f);
        return geometry;
    }

    private static BoundingBox bound(Node laid) {
        laid.updateModelBound();
        laid.updateGeometricState();
        return (BoundingBox) laid.getWorldBound();
    }

    @Test
    void twoSpansFitThreeHundredAndNoneFitAHundredAndFifty() {
        assertEquals(new LinePieces.Layout(2, 300f / 276f), LinePieces.layout(73f, 65f, 73f, 300f));
        assertEquals(new LinePieces.Layout(0, 150f / 146f), LinePieces.layout(73f, 65f, 73f, 150f),
                "the right meets the left");
    }

    @Test
    void theLeftTwoSpansAndTheRightEndAtTheFarEnd() {
        var laid = LinePieces.lay(bridge(), LOOK, new Coord3D(0f, 0f, 0f), new Coord3D(300f, 0f, 0f));

        assertEquals(4, laid.getQuantity(), "left, two spans, right");
        var box = bound(laid);
        assertEquals(0f, box.getCenter().x - box.getXExtent(), 1e-3f, "from the first end");
        assertEquals(300f, box.getCenter().x + box.getXExtent(), 1e-3f, "to the second, exactly");
        var right = (Node) laid.getChild(3);
        assertEquals(300f / 276f, right.getLocalScale().x, 1e-5f, "every piece stretched by 300/276");
    }

    @Test
    void aHundredAndFiftyApartTheRightMeetsTheLeft() {
        var laid = LinePieces.lay(bridge(), LOOK, new Coord3D(0f, 0f, 0f), new Coord3D(150f, 0f, 0f));

        assertEquals(2, laid.getQuantity());
        assertEquals(150f / 146f, ((Node) laid.getChild(0)).getLocalScale().x, 1e-5f);
    }

    @Test
    void endsAtDifferentHeightsRiseEvenly() {
        var laid = LinePieces.lay(bridge(), LOOK, new Coord3D(0f, 0f, 0f), new Coord3D(300f, 0f, 20f));
        laid.updateGeometricState();
        float length = (float) Math.sqrt(300f * 300f + 20f * 20f);

        var far = laid.localToWorld(new Vector3f(length, 0f, 0f), null);
        assertEquals(300f, far.x, 1e-2f);
        assertEquals(20f, far.y, 1e-2f, "the far end at the second point's height");
        var second = (Node) laid.getChild(1);
        var secondStart = laid.localToWorld(new Vector3f(second.getLocalTranslation().x
                + 73f * second.getLocalScale().x, 0f, 0f), null);
        assertEquals(secondStart.x / 300f * 20f, secondStart.y, 1e-2f, "and every piece between on the even rise");
    }

    @Test
    void aScaleAcrossNarrowsItWithoutShorteningIt() {
        var narrow = new Visuals.UnitVisual.LineLook("BRIDGE_LEFT", "BRIDGE_SPAN", "BRIDGE_RIGHT", 0.68f, 1f);
        var laid = LinePieces.lay(bridge(), narrow, new Coord3D(0f, 0f, 0f), new Coord3D(300f, 0f, 0f));

        var box = bound(laid);
        assertEquals(6.8f, box.getZExtent(), 1e-3f, "20 wide times 0.68");
        assertEquals(150f, box.getXExtent(), 1e-3f, "and as long as ever");
    }

    @Test
    void aModelWithoutItsPiecesIsDrawnOnceStretched() {
        var plain = new Node("ramp");
        plain.attachChild(piece("RAMP", 0f, 100f));

        var laid = LinePieces.lay(plain, LOOK, new Coord3D(0f, 0f, 0f), new Coord3D(250f, 0f, 0f));

        assertEquals(1, laid.getQuantity());
        var box = bound(laid);
        assertEquals(250f, box.getCenter().x + box.getXExtent(), 1e-3f, "stretched end to end");
    }
}
