package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.input.dummy.DummyKeyInput;
import com.jme3.input.dummy.DummyMouseInput;
import com.jme3.input.event.InputEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The game's first look at the input, through jME's own input manager: what the game takes goes no further, and
 * what it types arrives as the characters typed.
 */
class CanvasInputTest {

    private static final int HEIGHT = 600;

    /** A hand on the mouse and the keys: what it is given is raised when the input manager reads the devices. */
    private static final class Hand {
        final List<InputEvent> pending = new ArrayList<>();
        RawInputListener listener;
        final DummyMouseInput mouse = new DummyMouseInput() {
            @Override
            public void setInputListener(RawInputListener given) {
                listener = given;
            }

            @Override
            public void update() {
                super.update();
                for (var event : pending) {
                    switch (event) {
                        case MouseButtonEvent button -> listener.onMouseButtonEvent(button);
                        case KeyInputEvent key -> listener.onKeyEvent(key);
                        default -> throw new IllegalArgumentException(String.valueOf(event));
                    }
                }
                pending.clear();
            }
        };
        final DummyKeyInput keys = new DummyKeyInput();

        InputManager manager() {
            mouse.initialize();
            keys.initialize();
            return new InputManager(mouse, keys, null, null);
        }
    }

    /** The game's panel along the top of the screen: it takes the clicks inside it, and nothing else. */
    private static final CanvasInput PANEL = event -> event instanceof CanvasInput.Button button && button.y() < 50;

    @Test
    void aClickInARectangleTheGameClaimsDoesNotSelectTheUnitUnderIt() {
        var hand = new Hand();
        var input = hand.manager();
        var selections = new ArrayList<Boolean>();
        input.addMapping("Select", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        input.addListener((ActionListener) (name, pressed, tpf) -> selections.add(pressed), "Select");
        input.addRawInputListener(new CanvasInputs(PANEL, () -> HEIGHT));

        // The input manager counts up from the bottom: 580 is twenty pixels from the top.
        hand.pending.add(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, 100, 580));
        hand.pending.add(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, 100, 580));
        input.update(1f / 60f);
        assertEquals(List.of(), selections, "the panel took it; the world never heard of it");

        hand.pending.add(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, 100, 300));
        hand.pending.add(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, 100, 300));
        input.update(1f / 60f);
        assertEquals(List.of(true, false), selections, "a click outside it selects as it always did");
    }

    @Test
    void typedCyrillicArrivesAsTheSameCodePoints() {
        var hand = new Hand();
        var input = hand.manager();
        var typed = new StringBuilder();
        input.addRawInputListener(new CanvasInputs(event -> {
            if (event instanceof CanvasInput.Typed character) {
                typed.appendCodePoint(character.codePoint());
                return true;
            }
            return false;
        }, () -> HEIGHT));

        // As the window sends a character: a key with no code carrying it, down and up.
        for (char letter : "Привет".toCharArray()) {
            hand.pending.add(new KeyInputEvent(KeyInput.KEY_UNKNOWN, letter, true, false));
            hand.pending.add(new KeyInputEvent(KeyInput.KEY_UNKNOWN, letter, false, false));
        }
        input.update(1f / 60f);

        assertEquals("Привет", typed.toString());
        assertEquals(List.of(0x41F, 0x440, 0x438, 0x432, 0x435, 0x442), typed.codePoints().boxed().toList());
    }

    @Test
    void aKeyArrivesWithItsCodeAndWhetherItIsARepeat() {
        var heard = new ArrayList<CanvasInput.Event>();
        var inputs = new CanvasInputs(event -> heard.add(event), () -> HEIGHT);

        inputs.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', true, false));
        inputs.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', true, true));
        inputs.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', false, false));

        assertEquals(List.of(new CanvasInput.Key(KeyInput.KEY_BACK, true, false),
                new CanvasInput.Key(KeyInput.KEY_BACK, true, true),
                new CanvasInput.Key(KeyInput.KEY_BACK, false, false)), heard);
    }

    @Test
    void twoPressesCloseTogetherAreADoubleClickAndAThirdIsAFreshFirst() {
        var heard = new ArrayList<CanvasInput.Button>();
        var inputs = new CanvasInputs(event -> event instanceof CanvasInput.Button button && heard.add(button),
                () -> HEIGHT);

        inputs.onMouseButtonEvent(at(MouseInput.BUTTON_LEFT, 100, 100, 0L));
        inputs.onMouseButtonEvent(at(MouseInput.BUTTON_LEFT, 102, 101, 300_000_000L));
        inputs.onMouseButtonEvent(at(MouseInput.BUTTON_LEFT, 102, 101, 400_000_000L));
        inputs.onMouseButtonEvent(at(MouseInput.BUTTON_LEFT, 102, 101, 2_000_000_000L));

        assertFalse(heard.get(0).doubleClick());
        assertTrue(heard.get(1).doubleClick(), "within half a second and four pixels");
        assertFalse(heard.get(2).doubleClick(), "a third press is a first");
        assertFalse(heard.get(3).doubleClick(), "and one a long while later is too");
        assertEquals(HEIGHT - 101, heard.get(1).y(), "counted from the top, as the canvas draws");
    }

    private static MouseButtonEvent at(int button, int x, int y, long nanos) {
        var event = new MouseButtonEvent(button, true, x, y);
        event.setTime(nanos);
        return event;
    }
}
