package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.input.KeyInput;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.view.UnitView;

/**
 * The client's own controls on the keys a game gave them: the reference's layout, written by a test as a game
 * would write it, and pressed.
 */
class ControlsTest {

    private static final int ME = 1;
    private static final int THEM = 2;

    /** A world of units, what is selected, what is on screen, and what was ordered. */
    private static final class World implements Controls.Scene {
        final List<UnitView> units = new ArrayList<>();
        final Set<Integer> selection = new HashSet<>();
        final Set<Integer> onScreen = new HashSet<>();
        final Map<Integer, Set<String>> kinds = new LinkedHashMap<>();
        final CameraFocus camera = new CameraFocus();
        final List<List<Integer>> stopped = new ArrayList<>();
        final List<Map<Integer, Coord3D>> moved = new ArrayList<>();

        UnitView unit(int id, String template, int player, float x, float y, boolean seen, String... kinds) {
            var unit = new UnitView(id, template, player, x, y, 0f, 100f, 100f, false, true, false, false, -1);
            units.add(unit);
            if (seen) {
                onScreen.add(id);
            }
            this.kinds.put(id, Set.of(kinds));
            return unit;
        }

        void dies(int id) {
            units.removeIf(unit -> unit.id() == id);
        }

        @Override
        public List<UnitView> units() {
            return units;
        }

        @Override
        public int localPlayer() {
            return ME;
        }

        @Override
        public Set<Integer> selection() {
            return selection;
        }

        @Override
        public boolean onScreen(UnitView unit) {
            return onScreen.contains(unit.id());
        }

        @Override
        public boolean hasKind(UnitView unit, String kind) {
            return kinds.getOrDefault(unit.id(), Set.of()).contains(kind);
        }

        @Override
        public float sizeOf(UnitView unit) {
            return 5f;
        }

        @Override
        public CameraFocus camera() {
            return camera;
        }

        @Override
        public void stop(List<Integer> units) {
            stopped.add(units);
        }

        @Override
        public void move(Map<Integer, Coord3D> destinations) {
            moved.add(destinations);
        }
    }

    private static KeyMap.Key key(int code) {
        return KeyMap.Key.of(code);
    }

    @Test
    void sStopsTheSelectionAndMovesNoCameraWhenNothingPansOnIt() {
        var keys = KeyMap.empty()
                .bind(KeyMap.Control.PAN_UP, key(KeyInput.KEY_UP))
                .bind(KeyMap.Control.PAN_DOWN, key(KeyInput.KEY_DOWN))
                .bind(KeyMap.Control.STOP, key(KeyInput.KEY_S));
        var controls = new Controls(keys);
        var world = new World();
        world.unit(1, "Tank", ME, 10f, 10f, true);
        world.unit(2, "Tank", THEM, 20f, 10f, true);
        world.selection.addAll(List.of(1, 2));
        world.camera.lookAt(50f, 50f);

        var press = controls.press(key(KeyInput.KEY_S), 0f, world);

        assertEquals(KeyMap.Control.STOP, press.control());
        assertEquals(List.of(List.of(1)), world.stopped, "his own stop; theirs is only being looked at");
        assertFalse(controls.isHeld(KeyMap.Control.PAN_DOWN), "S pans nothing");
        assertEquals(50f, world.camera.targetZ(), 0f);
    }

    /**
     * The latest alert: without the game, where a thing of his was last hurt; once the game keeps its own, where the
     * game put it, a thing of his hurt after that moving it no more.
     */
    @Test
    void theLatestAlertLooksWhereTheGameSaysOnceItKeepsItsOwn() {
        var space = key(KeyInput.KEY_SPACE);
        var controls = new Controls(KeyMap.empty().bind(KeyMap.Control.LATEST_ALERT, space));
        var world = new World();

        controls.alert(10f, 20f); // his, hurt
        controls.press(space, 0f, world);
        assertEquals(10f, world.camera.targetX(), 0f, "where he was hurt, as ever");
        assertEquals(20f, world.camera.targetZ(), 0f);

        controls.gamesAlert(new uz.dukeengine.core.math.Coord2D(300f, 400f));
        controls.alert(10f, 20f); // hurt again
        controls.press(space, 1f, world);
        assertEquals(300f, world.camera.targetX(), 0f, "where the game put it");
        assertEquals(400f, world.camera.targetZ(), 0f, "a hurt thing moving it no more");
    }

    @Test
    void ctrlOneThenOneBringsTheGroupBack() {
        var controls = new Controls(KeyMap.empty().groupsOnDigits());
        var world = new World();
        world.unit(1, "Tank", ME, 10f, 10f, true);
        world.unit(2, "Tank", ME, 20f, 10f, true);
        world.unit(3, "Ranger", ME, 30f, 10f, true);
        world.selection.addAll(List.of(1, 2));

        controls.press(KeyMap.Key.ctrl(KeyInput.KEY_1), 0f, world);
        world.selection.clear();
        world.selection.add(3); // the player has moved on to something else
        controls.press(key(KeyInput.KEY_1), 5f, world);

        assertEquals(Set.of(1, 2), world.selection);
    }

    @Test
    void aSecondPressSoonAfterPutsTheCameraOnTheGroup() {
        var controls = new Controls(KeyMap.empty().groupsOnDigits());
        var world = new World();
        world.unit(1, "Tank", ME, 10f, 10f, true);
        world.unit(2, "Tank", ME, 300f, 400f, true);
        world.selection.addAll(List.of(1, 2));
        controls.press(KeyMap.Key.ctrl(KeyInput.KEY_4), 0f, world);

        controls.press(key(KeyInput.KEY_4), 10f, world);
        assertEquals(0f, world.camera.targetX(), 0f, "once: selected, not looked at");
        controls.press(key(KeyInput.KEY_4), 10.3f, world);
        assertEquals(300f, world.camera.targetX(), 0f, "twice: on the last of them");
        assertEquals(400f, world.camera.targetZ(), 0f);
    }

    @Test
    void aUnitThatDiesIsGoneFromItsGroup() {
        var controls = new Controls(KeyMap.empty().groupsOnDigits());
        var world = new World();
        world.unit(1, "Tank", ME, 10f, 10f, true);
        world.unit(2, "Tank", ME, 20f, 10f, true);
        world.selection.addAll(List.of(1, 2));
        controls.press(KeyMap.Key.ctrl(KeyInput.KEY_2), 0f, world);

        world.dies(1);
        controls.died(1);
        world.selection.clear();
        controls.press(key(KeyInput.KEY_2), 5f, world);

        assertEquals(List.of(2), controls.group(2));
        assertEquals(Set.of(2), world.selection);
    }

    @Test
    void shiftAddsTheGroupAndAltOnlyLooksAtIt() {
        var controls = new Controls(KeyMap.empty().groupsOnDigits());
        var world = new World();
        world.unit(1, "Tank", ME, 10f, 10f, true);
        world.unit(2, "Ranger", ME, 90f, 70f, true);
        world.selection.add(2);
        controls.press(KeyMap.Key.ctrl(KeyInput.KEY_3), 0f, world);
        world.selection.clear();
        world.selection.add(1);

        controls.press(KeyMap.Key.shift(KeyInput.KEY_3), 5f, world);
        assertEquals(Set.of(1, 2), world.selection, "added to what was selected");

        world.selection.clear();
        controls.press(KeyMap.Key.alt(KeyInput.KEY_3), 9f, world);
        assertTrue(world.selection.isEmpty(), "Alt selects nothing");
        assertEquals(90f, world.camera.targetX(), 0f);
    }

    @Test
    void ctrlF2ThenF2BringsTheCameraBack() {
        var controls = new Controls(KeyMap.empty().bookmarksOnFunctionKeys(8));
        var world = new World();
        world.camera.lookAt(120f, 80f);
        world.camera.turnBy(0.5f);
        world.camera.zoomBy(0.5f);
        var kept = world.camera.view();

        controls.press(KeyMap.Key.ctrl(KeyInput.KEY_F2), 0f, world);
        world.camera.lookAt(600f, 20f);
        world.camera.turnBy(1f);
        world.camera.resetView();
        controls.press(key(KeyInput.KEY_F2), 3f, world);

        assertEquals(kept, world.camera.view(), "where it looked, how it was turned, how far back it stood");
    }

    @Test
    void ePicksTheSameTypeOnScreenAndAgainEverywhere() {
        var controls = new Controls(KeyMap.empty().bind(KeyMap.Control.SELECT_SAME_TYPE, key(KeyInput.KEY_E)));
        var world = new World();
        world.unit(1, "Ranger", ME, 10f, 10f, true);
        world.unit(2, "Ranger", ME, 20f, 10f, true);
        world.unit(3, "Ranger", ME, 900f, 900f, false);
        world.unit(4, "Tank", ME, 15f, 10f, true);
        world.unit(5, "Ranger", THEM, 12f, 10f, true);
        world.selection.add(1);

        controls.press(key(KeyInput.KEY_E), 0f, world);
        assertEquals(Set.of(1, 2), world.selection, "every Ranger of his on the screen");

        controls.press(key(KeyInput.KEY_E), 0.4f, world);
        assertEquals(Set.of(1, 2, 3), world.selection, "and, pressed again soon after, every one on the map");
    }

    @Test
    void selectAllLeavesOutWhatTheGameSaysAndTheKindedOneTakesOnlyItsKind() {
        var controls = new Controls(KeyMap.empty()
                .bind(KeyMap.Control.SELECT_ALL, key(KeyInput.KEY_Q))
                .bind(KeyMap.Control.SELECT_ALL_OF_KIND, key(KeyInput.KEY_W))
                .kindToSelect("AIRCRAFT")
                .leftOutOfSelectAll(List.of("DOZER", "HARVESTER")));
        var world = new World();
        world.unit(1, "Tank", ME, 10f, 10f, true);
        world.unit(2, "Dozer", ME, 20f, 10f, true, "DOZER");
        world.unit(3, "Comanche", ME, 30f, 10f, false, "AIRCRAFT");
        world.unit(4, "Tank", THEM, 40f, 10f, true);

        controls.press(key(KeyInput.KEY_Q), 0f, world);
        assertEquals(Set.of(1, 3), world.selection);

        controls.press(key(KeyInput.KEY_W), 1f, world);
        assertEquals(Set.of(3), world.selection);
    }

    @Test
    void heldControlsLastAsLongAsTheirKeys() {
        var controls = new Controls(KeyMap.standard());
        var world = new World();

        controls.press(key(KeyInput.KEY_W), 0f, world);
        assertTrue(controls.isHeld(KeyMap.Control.PAN_UP));
        controls.press(KeyMap.Key.shift(KeyInput.KEY_D), 0f, world);
        assertTrue(controls.isHeld(KeyMap.Control.PAN_RIGHT), "a pan held with Shift down still pans");
        controls.release(KeyInput.KEY_W);
        assertFalse(controls.isHeld(KeyMap.Control.PAN_UP));
    }

    @Test
    void scatteringSendsEachAwayFromTheMiddle() {
        var controls = new Controls(KeyMap.empty().bind(KeyMap.Control.SCATTER, key(KeyInput.KEY_X)));
        var world = new World();
        world.unit(1, "Ranger", ME, 0f, 0f, true);
        world.unit(2, "Ranger", ME, 10f, 0f, true);
        world.selection.addAll(List.of(1, 2));

        controls.press(key(KeyInput.KEY_X), 0f, world);

        var sent = world.moved.getFirst();
        assertEquals(-20f, sent.get(1).x(), 0.01f, "four times its size, away from the middle");
        assertEquals(30f, sent.get(2).x(), 0.01f);
    }

    @Test
    void theDigitsBuildUntilAGroupIsPutOnThem() {
        assertFalse(KeyMap.standard().takesDigits());
        assertTrue(KeyMap.standard().groupsOnDigits().takesDigits());
        assertNull(new Controls(KeyMap.standard()).press(key(KeyInput.KEY_1), 0f, new World()).control());
    }
}
