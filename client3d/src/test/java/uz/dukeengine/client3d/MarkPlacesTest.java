package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.view.UnitView;

/**
 * Several marks by a health bar, each placed as the reference places its own ({@code Drawable::drawIconUI}): a rank's
 * chevron beside the bar, from its middle row, and the enthusiastic mark a quarter of its own height under it.
 */
class MarkPlacesTest {

    private static final String PICTURE = "Common/Textures/dot.png";
    private static final Visuals.MarkPlace CHEVRON =
            new Visuals.MarkPlace(1.1f, 1f, false, Visuals.MarkRow.MIDDLE, 0f, 1f);
    private static final Visuals.MarkPlace ENTHUSIASTIC =
            new Visuals.MarkPlace(0.25f, 0f, true, Visuals.MarkRow.UNDER, 0.25f, 0f);

    private static final Visuals.UnitVisual SOLDIER = Visuals.create().unit("Ranger", look -> look
            .mark(Set.of("VETERAN"), List.of(PICTURE), 100, false, 1f, CHEVRON, "rank", false)
            .mark(Set.of("ENTHUSIASTIC"), List.of(PICTURE), 100, false, 0.5f, ENTHUSIASTIC, "morale", true))
            .of("Ranger");

    @Test
    void aThingHoldingTwoMarksWordsShowsBothEachWhereItIsPlaced() {
        assertEquals(List.of(0, 1), SOLDIER.marksFor(Set.of("VETERAN", "ENTHUSIASTIC")), "one of each group");
        assertEquals(List.of(1), SOLDIER.marksFor(Set.of("ENTHUSIASTIC")));

        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var bars = new UnitBars(assets, font, font);
        bars.look(UnitBarLook.plain(UnitBarLook.Plain.REFERENCE));
        var camera = new Camera(1600, 900);
        camera.setFrustumPerspective(45f, 1600f / 900f, 1f, 2000f);
        camera.setLocation(new Vector3f(0f, 310f, 200f));
        camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        camera.update();
        var view = new UnitView(1, "Ranger", 1, 0f, 0f, 0f, 200f, 400f, false, true, false, false, -1);
        var badges = SOLDIER.marksFor(Set.of("VETERAN", "ENTHUSIASTIC")).stream().map(SOLDIER.marks::get)
                .map(mark -> new UnitBars.Badge(mark.frameAt(0f), mark.scale(), mark.place())).toList();

        bars.update(camera, List.of(new UnitBars.Standing(view, 20f, 0f, true, badges, 25f, true)),
                UnitBarReading.read(""), 310f, BarColours.REFERENCE);

        var bar = bars.plainBars().getFirst();
        var top = (Geometry) bar.getChild("outline0");
        var bottom = (Geometry) bar.getChild("outline1");
        float left = top.getLocalTranslation().x;
        float width = top.getLocalScale().x;
        float middle = (bottom.getLocalTranslation().y + top.getLocalTranslation().y + 1f) / 2f;

        var chevron = (Geometry) bar.getChild("badge");
        assertEquals(left + 1.1f * width + 1f, chevron.getLocalTranslation().x, 1e-3f,
                "its left edge 1.1 bars and a pixel from the bar's left end");
        assertEquals(middle - 1f, chevron.getLocalTranslation().y + chevron.getLocalScale().y, 1e-3f,
                "its top a pixel below the bar's middle row");

        var cross = (Geometry) bar.getChild("badge1");
        float tall = cross.getLocalScale().y;
        assertEquals(left + 0.25f * width, cross.getLocalTranslation().x + cross.getLocalScale().x / 2f, 1e-3f,
                "its middle a quarter along");
        assertEquals(bottom.getLocalTranslation().y - tall / 4f, cross.getLocalTranslation().y + tall, 1e-3f,
                "a quarter of its own height under the bar");
        assertTrue(cross.getLocalScale().x < chevron.getLocalScale().x, "at its own scale");
    }

    /**
     * The reference's enthusiastic mark, 32 pixels square drawn at 0.5 on a soldier, with a quarter of its own height
     * named as its gap ({@code Drawable::drawEnthusiastic}): its top 4 pixels below the plain bar's bottom edge, its
     * middle a quarter along the bar; with none named, its top on the edge.
     */
    @Test
    void aThirtyTwoPixelMarkAtAHalfStandsFourPixelsUnderThePlainBar() {
        float drawn = 32f * 0.5f;
        var corner = UnitBars.markCorner(ENTHUSIASTIC, drawn, drawn, 100f, 40f, 200f, 203f);
        assertEquals(200f - 4f, corner[1] + drawn, 1e-4f, "its top 4 pixels below the bar's bottom edge");
        assertEquals(100f + 40f / 4f, corner[0] + drawn / 2f, 1e-4f, "its middle a quarter along");
        var unnamed = UnitBars.markCorner(Visuals.MarkPlace.under(0.25f), drawn, drawn, 100f, 40f, 200f, 203f);
        assertEquals(200f, unnamed[1] + drawn, 1e-4f, "no gap unless named");
    }

    @Test
    void aStripStartsOnAPictureOfItsOwnWhereItSaysSo() {
        var strip = List.of("e0", "e1", "e2", "e3");
        var look = Visuals.create().unit("Ranger", l -> l
                .mark(Set.of("ENTHUSIASTIC"), strip, 100, false, 1f, ENTHUSIASTIC, "", true)
                .mark(Set.of("SUBLIMINAL"), strip, 100, false, 1f, ENTHUSIASTIC, "s", false)).of("Ranger");
        var random = new Random(7);
        var started = new java.util.HashSet<String>();
        for (int time = 0; time < 40; time++) {
            var mark = look.marks.getFirst();
            started.add(mark.frameAt(mark.startSeconds(random)));
        }
        assertTrue(started.size() > 1, "on a picture drawn at random: " + started);
        assertEquals(0f, look.marks.get(1).startSeconds(random), "one that does not say so, on its first");
    }
}
