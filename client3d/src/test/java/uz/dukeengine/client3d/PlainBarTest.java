package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.view.UnitView;

/** The health bar as an RTS draws it: an outline and a fill, sized by the thing and shown over the picked ones. */
class PlainBarTest {

    private static final UnitBarLook PLAIN = UnitBarLook.plain(UnitBarLook.Plain.REFERENCE);

    private static UnitBars bars() {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var bars = new UnitBars(assets, font, font);
        bars.look(PLAIN);
        return bars;
    }

    private static Camera camera() {
        var camera = new Camera(1600, 900);
        camera.setFrustumPerspective(45f, 1600f / 900f, 1f, 2000f);
        camera.setLocation(new Vector3f(0f, 310f, 200f));
        camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        camera.update();
        return camera;
    }

    private static UnitView thing(int id, float x, float health, int statuses) {
        return new UnitView(id, "Crusader", 1, x, 0f, 0f, health, 400f, false, true, false, false, -1, 0f, 0f, 0f,
                false, statuses);
    }

    private static UnitBars.Standing standing(UnitView view, boolean picked, UnitBars.Badge badge) {
        return new UnitBars.Standing(view, 20f, 0f, true, badge, 25f, picked);
    }

    private static Geometry piece(Node bar, String name) {
        return (Geometry) bar.getChild(name);
    }

    private static boolean shown(Spatial spatial) {
        return spatial.getCullHint() != Spatial.CullHint.Always;
    }

    @Test
    void shownOverTheSelectedAndThePointedAtOnlyAndTheThirdsMarkIsDrawnAll() {
        var bars = bars();
        var mark = new UnitBars.Badge("Common/Textures/dot.png", 0.25f, 1f);

        bars.update(camera(), List.of(standing(thing(1, -40f, 400f, 0), true, null),
                standing(thing(2, 0f, 400f, 0), true, null),
                standing(thing(3, 40f, 400f, 0), false, mark)), UnitBarReading.read(""), 310f, BarColours.REFERENCE);

        var up = bars.plainBars();
        assertEquals(3, up.size());
        assertTrue(shown(piece(up.get(0), "fill")), "the selected one has a bar");
        assertTrue(shown(piece(up.get(1), "fill")), "and the one under the pointer");
        assertEquals(false, shown(piece(up.get(2), "fill")), "the third none");
        assertEquals(false, shown(piece(up.get(2), "outline0")));
        assertTrue(shown(piece(up.get(2), "badge")), "but its mark is drawn");
    }

    @Test
    void aThingOfRadiiFifteenAndTenUnderAnEye310UpIs374WideAnd3TallItsHalfFillInsideAOnePixelOutline() {
        assertEquals(37.4f, UnitBars.plainWidth(UnitBarLook.Plain.REFERENCE, 15f + 10f, 310f), 0.05f);
        assertEquals(29.9f, UnitBars.plainWidth(UnitBarLook.Plain.REFERENCE, 7f + 7f, 310f), 0.05f,
                "a soldier of radius 7: 40 at 232");
        assertEquals(194.6f, UnitBars.plainWidth(UnitBarLook.Plain.REFERENCE, 60f + 70f, 310f), 0.05f,
                "a headquarters of 60 and 70");

        var bars = bars();
        bars.update(camera(), List.of(standing(thing(1, 0f, 200f, 0), true, null)), UnitBarReading.read(""), 310f,
                BarColours.REFERENCE);

        var bar = bars.plainBars().getFirst();
        float width = UnitBars.plainWidth(UnitBarLook.Plain.REFERENCE, 25f, 310f);
        assertEquals(width, piece(bar, "outline0").getLocalScale().x, 1e-4f, "the outline as wide as the bar");
        assertEquals(1f, piece(bar, "outline0").getLocalScale().y, 1e-4f, "one pixel");
        float top = piece(bar, "outline0").getLocalTranslation().y + 1f;
        float bottom = piece(bar, "outline1").getLocalTranslation().y;
        assertEquals(3f, top - bottom, 1e-4f, "3 tall");
        assertEquals((width - 2f) / 2f, piece(bar, "fill").getLocalScale().x, 1e-4f, "half of the inside");
        assertEquals(1f, piece(bar, "fill").getLocalScale().y, 1e-4f, "one pixel tall");
        assertEquals(piece(bar, "outline0").getLocalTranslation().x + 1f, piece(bar, "fill").getLocalTranslation().x,
                1e-4f, "inside the outline");
    }

    @Test
    void aColourFunctionAnsweringRedForAHalfShareFillsTheBarRed() {
        var bars = bars();
        BarColours red = thing -> thing.healthFraction() == 0.5f ? new BarColours.Colours(0xFF0000, 0x800000) : null;

        bars.update(camera(), List.of(standing(thing(1, 0f, 200f, 0), true, null)), UnitBarReading.read(""), 310f, red);

        assertEquals(ColorRGBA.Red, piece(bars.plainBars().getFirst(), "fill").getMaterial().getParamValue("Color"));
    }

    @Test
    void theReferencesColoursAtFullHalfAndAFifthAndGoingUp() {
        assertEquals(new BarColours.Colours(0x00FF00, 0x007F00), BarColours.REFERENCE.of(thing(1, 0f, 400f, 0)));
        assertEquals(new BarColours.Colours(0xFFFF00, 0x7F7F00), BarColours.REFERENCE.of(thing(1, 0f, 200f, 0)));
        assertEquals(new BarColours.Colours(0xFF3300, 0x7F3300), BarColours.REFERENCE.of(thing(1, 0f, 80f, 0)));
        int goingUp = 1 << ObjectStatus.UNDER_CONSTRUCTION.ordinal();
        assertEquals(new BarColours.Colours(0x007FFF, 0x004080), BarColours.REFERENCE.of(thing(1, 0f, 200f, goingUp)),
                "blue to cyan while it is built");
    }

    /**
     * A bar floats over the feet where they stand: up a hill 30 high, a model 12 tall carries its bar at 42. It was
     * read as the world height itself, so going up a hill the bar sank under the hero and going down it floated high.
     */
    @Test
    void aBarFloatsOverTheFeetWhereverTheyStand() {
        assertEquals(42f, DukeRtsApp.barTop(30f, 12f, null, null), 1e-4f);
        assertEquals(12f, DukeRtsApp.barTop(0f, 12f, null, null), 1e-4f, "on the flat, as it always was");
    }
}
