package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.view.RallyView;

/** A rally point shown while its building is selected: the flag on it, and the line to it with its nodes. */
class RallyMarksTest {

    private static final WorldMoments.Floor FLAT = (x, z) -> 0f;
    private static final Vector3f EYE = new Vector3f(300f, 400f, -200f);
    private static final RallyLook LOOK = new RallyLook("models/scmrally.glb", "SCMRally", -0.785f,
            "models/scmnode.glb", 1.5f, 0x4080FF, null);
    private static final ColorRGBA RED = new ColorRGBA(1f, 0f, 0f, 1f);

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);

    /** A pole with a cloth marked for its owner's colour. */
    private static Spatial load(String path) {
        var model = new Node(path);
        var cloth = new Geometry("HOUSECOLOR01", new Box(1f, 1f, 0.1f));
        var material = new Material(ASSETS, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", ColorRGBA.White.clone());
        cloth.setMaterial(material);
        model.attachChild(cloth);
        return model;
    }

    private static RallyView factory(int id, Coord3D rally) {
        var door = new Coord3D(190f, 170f, 0f);
        var natural = new Coord3D(253f, 170f, 0f);
        return new RallyView(id, rally, List.of(door, natural, rally), List.of(natural));
    }

    private static Vector3f end(Geometry leg) {
        var positions = leg.getMesh().getFloatBuffer(VertexBuffer.Type.Position);
        return new Vector3f((positions.get(6) + positions.get(9)) / 2f, (positions.get(7) + positions.get(10)) / 2f,
                (positions.get(8) + positions.get(11)) / 2f);
    }

    @Test
    void oneFactorySelectedShowsItsFlagInThePlayersColourAndALineFromTheDoorThroughTheNaturalRallyPoint() {
        var marks = new RallyMarks(ASSETS, new Node("rallies"), RallyMarksTest::load);
        var rally = factory(7, new Coord3D(450f, 200f, 0f));
        var shown = List.of(rally);

        marks.show(LOOK, shown, RallyMarks.flagOf(shown, 1), RED, "HOUSECOLOR", FLAT, EYE);

        var flag = marks.shownFlag();
        assertEquals(new Vector3f(450f, 0f, 200f), flag.getLocalTranslation(), "the flag on the rally point");
        var cloth = (Geometry) ((Node) flag).getChild("HOUSECOLOR01");
        assertEquals(RED, cloth.getMaterial().getParamValue("Diffuse"), "in his colour");
        assertEquals(2, marks.shownLegs().size(), "door to natural rally point, and on to the rally point");
        assertEquals(new Vector3f(253f, 0f, 170f), end(marks.shownLegs().get(0)));
        assertEquals(new Vector3f(450f, 0f, 200f), end(marks.shownLegs().get(1)));
        assertEquals(1, marks.shownNodes().size(), "a node at the natural rally point");
        assertEquals(new Vector3f(253f, 0f, 170f), marks.shownNodes().getFirst().getLocalTranslation());
    }

    @Test
    void twoSelectedShowTwoLinesAndNoFlagAndNothingSelectedShowsNeither() {
        var marks = new RallyMarks(ASSETS, new Node("rallies"), RallyMarksTest::load);
        var two = List.of(factory(7, new Coord3D(450f, 200f, 0f)), factory(8, new Coord3D(400f, 300f, 0f)));

        marks.show(LOOK, two, RallyMarks.flagOf(two, 2), RED, "HOUSECOLOR", FLAT, EYE);
        assertEquals(4, marks.shownLegs().size(), "two lines");
        assertNull(marks.shownFlag(), "no flag");

        marks.show(LOOK, List.of(), RallyMarks.flagOf(List.of(), 0), RED, "HOUSECOLOR", FLAT, EYE);
        assertEquals(0, marks.shownLegs().size());
        assertEquals(0, marks.shownNodes().size());
        assertNull(marks.shownFlag());
    }

    @Test
    void aRallyPointMovedIsFollowedTheNextFrame() {
        var marks = new RallyMarks(ASSETS, new Node("rallies"), RallyMarksTest::load);
        var before = List.of(factory(7, new Coord3D(450f, 200f, 0f)));
        marks.show(LOOK, before, RallyMarks.flagOf(before, 1), RED, "HOUSECOLOR", FLAT, EYE);

        var after = List.of(factory(7, new Coord3D(380f, 260f, 0f)));
        marks.show(LOOK, after, RallyMarks.flagOf(after, 1), RED, "HOUSECOLOR", FLAT, EYE);

        assertEquals(new Vector3f(380f, 0f, 260f), marks.shownFlag().getLocalTranslation());
        assertEquals(new Vector3f(380f, 0f, 260f), end(marks.shownLegs().getLast()));
    }

    @Test
    void aGameThatNamesNoLookIsShownTheReferencesLineAndNoFlag() {
        var marks = new RallyMarks(ASSETS, new Node("rallies"), path -> {
            throw new AssertionError("no model is named: " + path);
        });
        var one = List.of(factory(7, new Coord3D(450f, 200f, 0f)));

        marks.show(RallyLook.DEFAULT, one, RallyMarks.flagOf(one, 1), RED, null, FLAT, EYE);

        assertEquals(2, marks.shownLegs().size());
        var colour = (ColorRGBA) marks.shownLegs().getFirst().getMaterial().getParamValue("Color");
        assertEquals(0.25f, colour.r, 1f / 255f, "the reference's (0.25, 0.5, 1.0), to a byte");
        assertEquals(0.5f, colour.g, 1f / 255f);
        assertEquals(1f, colour.b, 1f / 255f);
        assertNull(marks.shownFlag());
    }
}
