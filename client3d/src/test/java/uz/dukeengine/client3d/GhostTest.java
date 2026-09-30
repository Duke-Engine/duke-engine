package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.AimMark;

/** A ghost drawn as the building it places, and the ground marked where it is refused ({@code InGameUI}). */
class GhostTest {

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);
    private static final String BIB = "Common/Textures/MissingTexture.png";
    private static final Visuals.GhostLook LOOK = new Visuals.GhostLook(0.45f, Color.RED, BIB);

    private static Geometry hull(Material material) {
        var hull = new Geometry("hull", new Box(5f, 5f, 5f));
        hull.setMaterial(material);
        return hull;
    }

    private static Material lit() {
        var material = new Material(ASSETS, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", ColorRGBA.White.clone());
        return material;
    }

    @Test
    void atAnOpacityOf045AndAYesItsOwnMaterialsShowFadedWithNoTintAndNoMarks() {
        var own = lit();
        var body = new Node("barracks");
        body.attachChild(hull(own));
        var ghost = new Ghost(ASSETS, new Node("markers"), body, LOOK);
        ghost.answer(true, List.of(), (x, y) -> 0f);

        var drawn = ((Geometry) body.getChild("hull")).getMaterial();
        assertNotSame(own, drawn, "its own copy: the things it stands for untouched");
        assertEquals(own.getMaterialDef(), drawn.getMaterialDef(), "its model's own material, lit");
        assertEquals(0.45f, ((ColorRGBA) drawn.getParamValue("Diffuse")).a, 1e-6f);
        assertEquals(ColorRGBA.Black, ghost.tint(), "no tint");
        assertEquals(0, ghost.marksShowing(), "no marks");
    }

    @Test
    void aNoWithOneRectangleTintsItRedAndLaysThePictureAndAYesTakesBothAway() {
        var body = new Node("barracks");
        body.attachChild(hull(lit()));
        var ghost = new Ghost(ASSETS, new Node("markers"), body, LOOK);
        ghost.answer(false, List.of(new AimMark(100f, 100f, 0f, 20f, 20f, 20f)), (x, y) -> 0f);
        assertEquals(ColorRGBA.Red, ghost.tint(), "red added to its light");
        assertEquals(1, ghost.marksShowing(), "the picture on the rectangle");

        ghost.answer(true, List.of(), (x, y) -> 0f);
        assertEquals(ColorRGBA.Black, ghost.tint());
        assertEquals(0, ghost.marksShowing(), "both gone");
    }

    @Test
    void aRectangleFacing90Reaching10Ahead5Behind4EachSideIsLaidTurned15By8() {
        var parent = new Node("markers");
        var decal = new GroundDecal(ASSETS, parent);
        Ghost.lay(decal, new AimMark(100f, 100f, 90f, 10f, 5f, 4f), BIB, (x, y) -> 0f);

        var node = (Node) parent.getChild("aim decal");
        assertEquals(100f, node.getLocalTranslation().x, 1e-4f);
        assertEquals(102.5f, node.getLocalTranslation().z, 1e-4f, "its middle 2.5 ahead of the centre");
        var bound = (BoundingBox) ((Geometry) node.getChild("aim decal")).getMesh().getBound();
        assertEquals(4f, bound.getXExtent(), 1e-3f, "8 across");
        assertEquals(7.5f, bound.getZExtent(), 1e-3f, "15 along its facing, turned 90 degrees");
    }

    @Test
    void aGameNamingNoLookKeepsTheFlatGreenAndRed() {
        var body = new Node("barracks");
        body.attachChild(hull(lit()));
        var ghost = new Ghost(ASSETS, new Node("markers"), body, null);
        ghost.answer(false, List.of(new AimMark(0f, 0f, 0f, 1f, 1f, 1f)), (x, y) -> 0f);
        var flat = ((Geometry) body.getChild("hull")).getMaterial();
        assertEquals(Ghost.REFUSED, flat.getParamValue("Color"));
        assertEquals(0, ghost.marksShowing());
    }
}
