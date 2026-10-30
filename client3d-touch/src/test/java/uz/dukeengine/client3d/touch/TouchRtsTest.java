package uz.dukeengine.client3d.touch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.ControlScheme;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.view.UnitView;

/** The scheme's calls on the window for each gesture; the window is a recorder over a flat ground. */
class TouchRtsTest {

    private final List<String> calls = new ArrayList<>();
    private boolean unitUnderFinger;
    private boolean somethingSelected;
    private float lookX = 50f;
    private float lookY = 50f;

    private ControlScheme.Context window() {
        InvocationHandler recorder = (proxy, method, args) -> switch (method.getName()) {
            case "input" -> null;
            case "time" -> 0f;
            case "pointedUnit" -> unitUnderFinger ? aUnit() : null;
            case "selected" -> somethingSelected ? List.of(new ObjectId(1)) : List.of();
            // ten pixels to a world unit, the camera's look the origin
            case "groundAt" -> new float[] {(float) args[0] / 10f + lookX, (float) args[1] / 10f + lookY, 0f};
            case "lookingAt" -> new float[] {lookX, lookY};
            case "lookAt" -> {
                lookX = (float) args[0];
                lookY = (float) args[1];
                yield null;
            }
            default -> {
                calls.add(method.getName() + (args == null ? "" : " " + List.of(args)));
                yield null;
            }
        };
        return (ControlScheme.Context) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {ControlScheme.Context.class}, recorder);
    }

    /** Any unit: the scheme only asks whether there is one. */
    private static UnitView aUnit() throws ReflectiveOperationException {
        var make = UnitView.class.getDeclaredConstructors()[0];
        var types = make.getParameterTypes();
        var args = new Object[types.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = types[i] == boolean.class ? (Object) false : types[i] == int.class ? (Object) 0
                    : types[i] == float.class ? (Object) 0f : types[i] == long.class ? (Object) 0L : null;
        }
        return (UnitView) make.newInstance(args);
    }

    private TouchRts scheme() {
        var scheme = new TouchRts();
        scheme.attach(window());
        scheme.pressedWorld();
        return scheme;
    }

    @Test
    void aTapOnAUnitSelectsAndOnGroundWithASelectionOrders() {
        var scheme = scheme();
        unitUnderFinger = true;
        somethingSelected = true;
        scheme.gestures().down(1, 10, 10, 0f);
        scheme.gestures().up(1, 10, 10, 0.1f);
        assertEquals(List.of("selectPointed [false]"), calls);

        calls.clear();
        unitUnderFinger = false;
        scheme.gestures().down(1, 10, 10, 1f);
        scheme.gestures().up(1, 10, 10, 1.1f);
        assertEquals(List.of("orderPointed"), calls);

        calls.clear();
        somethingSelected = false;
        scheme.gestures().down(1, 10, 10, 2f);
        scheme.gestures().up(1, 10, 10, 2.1f);
        assertEquals(List.of("selectPointed [false]"), calls, "nothing selected: the ground clears, no order");
    }

    @Test
    void aDragPansSoTheGroundStaysUnderTheFinger() {
        var scheme = scheme();
        scheme.gestures().down(1, 100, 100, 0f);
        scheme.gestures().move(1, 200, 100); // the finger moved 100 px right: 10 world units of ground

        assertEquals(40f, lookX, 1e-3, "the camera went left by what the ground slid under the finger");
        assertEquals(50f, lookY, 1e-3);
    }

    @Test
    void aHeldFingerBeginsABoxAndLiftingEndsIt() {
        var scheme = scheme();
        scheme.gestures().down(1, 100, 100, 0f);
        scheme.gestures().update(TouchGestures.LONG_PRESS);
        scheme.gestures().up(1, 180, 160, 1f);

        assertEquals(List.of("beginBox", "endBox [false]"), calls);
    }

    @Test
    void pinchZoomsAndTwistTurnsTheCamera() {
        var scheme = scheme();
        scheme.gestures().down(1, 100, 100, 0f);
        scheme.gestures().down(2, 200, 100, 0f);
        scheme.gestures().move(2, 300, 100);
        scheme.gestures().move(2, 100, 200);

        assertEquals("zoomBy [0.5]", calls.get(0), "fingers apart: nearer");
        assertTrue(calls.stream().anyMatch(c -> c.startsWith("turnBy [-")), "counterclockwise: the eye turns back");
    }

    @Test
    void aTouchThatNeverReachedTheWorldDoesNothing() {
        var scheme = new TouchRts();
        scheme.attach(window());
        scheme.gestures().down(1, 10, 10, 0f);
        scheme.gestures().up(1, 10, 10, 0.1f);

        assertTrue(calls.isEmpty(), "a tap on the HUD is the client's");
        assertFalse(scheme.left(true), "no input here: the client's own mouse");
    }
}
