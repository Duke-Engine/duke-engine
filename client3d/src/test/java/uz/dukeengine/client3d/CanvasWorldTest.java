package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.core.view.UnitView;

/**
 * Where a thing and its bar are on the screen, and the player's control groups, for a game's own drawing — the
 * reference's built percent, group numbers and pips ({@code Drawable::drawConstructPercent}, {@code drawUIText}).
 */
class CanvasWorldTest {

    private static Camera camera(float z) {
        var camera = new Camera(1280, 720);
        camera.setFrustumPerspective(45f, 1280f / 720f, 1f, 2000f);
        camera.setLocation(new Vector3f(0f, 310f, z));
        camera.lookAt(new Vector3f(0f, 0f, z - 200f), Vector3f.UNIT_Y);
        camera.update();
        return camera;
    }

    @Test
    void theGroundUnderThePointerIsToldWhereThePointerIsAndAPointBehindTheEyeIsNot() {
        var camera = camera(200f);
        var near = camera.getWorldCoordinates(new Vector2f(640f, 720f - 360f), 0f);
        var far = camera.getWorldCoordinates(new Vector2f(640f, 720f - 360f), 1f);
        var ground = near.add(far.subtract(near).mult(near.y / (near.y - far.y)));

        var told = CanvasFrame.onScreen(camera, ground.x, ground.z, 0f);
        assertEquals(640f, told.x(), 1f);
        assertEquals(360f, told.y(), 1f);
        assertNull(CanvasFrame.onScreen(camera, 0f, 1000f, 310f), "behind the eye: none");
    }

    @Test
    void aBarPlacedAt400By300ThirtyByThreeIsToldSoInTheCanvassPixels() {
        assertEquals(new Canvas.Box(400f, 300f, 30f, 3f), CanvasFrame.fromTheBottom(new float[] {400f, 417f, 30f, 3f},
                720), "its top left in the canvas's pixels");
        assertNull(CanvasFrame.fromTheBottom(null, 720));
    }

    private static UnitBars bars() {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var bars = new UnitBars(assets, font, font);
        bars.look(UnitBarLook.plain(UnitBarLook.Plain.REFERENCE));
        return bars;
    }

    private static UnitBars.Standing standing(int id, float health, float most, boolean picked) {
        var view = new UnitView(id, "Crusader", 1, 0f, 0f, 0f, health, most, false, true, false, false, -1);
        return new UnitBars.Standing(view, 20f, 0f, true, List.<UnitBars.Badge>of(), 25f, picked);
    }

    @Test
    void everyBarPlacedIsToldShownOrNotAndAThingOfNoHealthHasNone() {
        var bars = bars();
        bars.update(camera(200f), List.of(standing(1, 400f, 400f, false), standing(2, 0f, 0f, true)),
                UnitBarReading.read(""), 310f, BarColours.REFERENCE);
        assertNotNull(bars.barOf(1), "not shown, not picked: told all the same");
        assertEquals(UnitBarLook.Plain.REFERENCE.height(), bars.barOf(1)[3]);
        assertNull(bars.barOf(2), "a thing of no health: none");

        var before = bars.barOf(1).clone();
        bars.update(camera(220f), List.of(standing(1, 400f, 400f, false)), UnitBarReading.read(""), 310f,
                BarColours.REFERENCE);
        assertNotEquals(before[1], bars.barOf(1)[1], "the view scrolled: told where it now is, that frame");
    }

    @Test
    void theGameIsToldWhichGroupEachThingIsInWheneverThatChanges() {
        var controls = new Controls(KeyMap.empty().groupsOnDigits().oneGroupEach(true));
        var game = DukeGame.create("groups");
        var link = new SelectionLink();
        var world = new ControlsTestWorld();
        world.add(3);
        world.add(4);
        world.selection().addAll(List.of(3, 4));
        controls.press(KeyMap.Key.ctrl(com.jme3.input.KeyInput.KEY_2), 0f, world);
        link.tellGroups(controls.groups(), game);
        assertEquals(Map.of(2, List.of(3, 4)), game.getControlGroups());

        world.selection().clear();
        world.selection().add(4);
        controls.press(KeyMap.Key.ctrl(com.jme3.input.KeyInput.KEY_5), 1f, world);
        link.tellGroups(controls.groups(), game);
        assertEquals(Map.of(2, List.of(3), 5, List.of(4)), game.getControlGroups());

        controls.died(3);
        link.tellGroups(controls.groups(), game);
        assertEquals(List.of(), game.getControlGroups().getOrDefault(2, List.of()), "group 2 holds nobody");
    }

    /** Just enough of a scene for groups: the player's things, and what is selected. */
    private static final class ControlsTestWorld implements Controls.Scene {
        private final List<UnitView> units = new java.util.ArrayList<>();
        private final java.util.Set<Integer> selection = new java.util.LinkedHashSet<>();

        void add(int id) {
            units.add(new UnitView(id, "Tank", 1, 0f, 0f, 0f, 100f, 100f, false, true, false, false, -1));
        }

        @Override
        public List<UnitView> units() {
            return units;
        }

        @Override
        public int localPlayer() {
            return 1;
        }

        @Override
        public java.util.Set<Integer> selection() {
            return selection;
        }

        @Override
        public boolean onScreen(UnitView unit) {
            return true;
        }

        @Override
        public boolean hasKind(UnitView unit, String kind) {
            return false;
        }

        @Override
        public float sizeOf(UnitView unit) {
            return 5f;
        }

        @Override
        public CameraFocus camera() {
            return new CameraFocus();
        }

        @Override
        public void stop(List<Integer> units) {
        }

        @Override
        public void move(Map<Integer, uz.dukeengine.core.math.Coord3D> destinations) {
        }
    }
}
