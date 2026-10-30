package uz.dukeengine.client3d.touch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The stick's maths, fed by hand. */
class VirtualStickTest {

    private final VirtualStick stick = new VirtualStick();

    @Test
    void idleSaysNothingAndTheDeadZoneIsQuiet() {
        assertEquals(VirtualStick.NONE, stick.angle(0f));
        stick.down(300, 300);
        stick.move(310, 300); // 10 px of a 120 px radius: inside the dead zone
        assertTrue(stick.active());
        assertEquals(0f, stick.strength());
        assertEquals(VirtualStick.NONE, stick.angle(0f));
    }

    @Test
    void strengthGrowsFromTheDeadZoneToTheRadiusAndNoFurther() {
        stick.down(0, 0);
        stick.move(VirtualStick.RADIUS * 0.6f, 0); // 0.6 of the radius: (0.6 - 0.2) / 0.8 = 0.5
        assertEquals(0.5f, stick.strength(), 1e-4);
        stick.move(VirtualStick.RADIUS * 3f, 0);
        assertEquals(1f, stick.strength(), 1e-4);
        assertEquals(VirtualStick.RADIUS, stick.knobX(), 1e-3, "the knob stays on the ring");
        assertEquals(0f, stick.knobY(), 1e-3);
    }

    @Test
    void directionFollowsTheDragAndTheCameraTurn() {
        stick.down(0, 0);
        stick.move(100, 0); // right on the screen, the camera looking up the map: along +x
        assertEquals(0, stick.angle(0f));
        stick.move(0, 100); // up the screen: away from the eye, 64 256ths clockwise from +x
        assertEquals(192, stick.angle(0f));
        stick.move(100, 0);
        assertEquals(192, stick.angle((float) (Math.PI / 2)), "a quarter turn of the camera: right is another way");
    }

    @Test
    void liftingStopsIt() {
        stick.down(0, 0);
        stick.move(100, 0);
        stick.up();
        assertFalse(stick.active());
        assertEquals(VirtualStick.NONE, stick.angle(0f));
        stick.move(100, 0); // a stray move after the lift changes nothing
        assertEquals(0f, stick.strength());
    }
}
