package uz.dukeengine.client3d.touch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the fingers mean, fed by hand: no window, no GPU. */
class TouchGesturesTest {

    private final List<String> heard = new ArrayList<>();
    private final TouchGestures hand = new TouchGestures(new TouchGestures.Listener() {
        @Override
        public void tap(float x, float y) {
            heard.add("tap " + x + "," + y);
        }

        @Override
        public void panBegin(float x, float y) {
            heard.add("panBegin " + x + "," + y);
        }

        @Override
        public void pan(float x, float y) {
            heard.add("pan " + x + "," + y);
        }

        @Override
        public void panEnd() {
            heard.add("panEnd");
        }

        @Override
        public void boxBegin(float x, float y) {
            heard.add("boxBegin " + x + "," + y);
        }

        @Override
        public void boxEnd(float x, float y) {
            heard.add("boxEnd " + x + "," + y);
        }

        @Override
        public void pinch(float factor) {
            heard.add("pinch " + factor);
        }

        @Override
        public void twist(float radians) {
            heard.add("twist " + radians);
        }
    });

    @Test
    void aFingerLiftedInPlaceTaps() {
        hand.down(1, 100, 100, 0f);
        hand.move(1, 105, 103); // within the slop
        hand.up(1, 105, 103, 0.1f);

        assertEquals(List.of("tap 105.0,103.0"), heard);
        assertFalse(hand.touching());
    }

    @Test
    void aFingerDraggedPastTheSlopPansAndDoesNotTap() {
        hand.down(1, 100, 100, 0f);
        hand.move(1, 150, 100);
        hand.move(1, 160, 100);
        hand.up(1, 160, 100, 0.2f);

        assertEquals(List.of("panBegin 100.0,100.0", "pan 150.0,100.0", "pan 160.0,100.0", "panEnd"), heard);
    }

    @Test
    void aFingerHeldThenDraggedMarksABoxAndDoesNotPan() {
        hand.down(1, 100, 100, 0f);
        hand.update(0.2f);
        assertTrue(heard.isEmpty(), "not held long enough");
        hand.update(TouchGestures.LONG_PRESS);
        hand.move(1, 200, 180);
        hand.up(1, 220, 190, 1f);

        assertEquals(List.of("boxBegin 100.0,100.0", "boxEnd 220.0,190.0"), heard);
    }

    @Test
    void twoFingersApartPinchOutAndTogetherPinchIn() {
        hand.down(1, 100, 100, 0f);
        hand.down(2, 200, 100, 0.01f);
        hand.move(2, 300, 100); // distance 100 -> 200
        hand.move(2, 200, 100); // and back

        assertEquals(List.of("pinch 2.0", "twist 0.0", "pinch 0.5", "twist 0.0"), heard);
    }

    @Test
    void twoFingersTwistingTurnByTheAngleBetweenThem() {
        hand.down(1, 100, 100, 0f);
        hand.down(2, 200, 100, 0.01f);
        hand.move(2, 100, 200); // the line between them turns a quarter, counterclockwise
        hand.up(2, 100, 200, 0.5f);
        hand.up(1, 100, 100, 0.6f);

        assertEquals(2, heard.size());
        assertTrue(heard.get(1).startsWith("twist 1.5707"), heard.get(1));
    }

    @Test
    void aSecondFingerEndsAPanAndLiftingEitherLeavesNoTap() {
        hand.down(1, 100, 100, 0f);
        hand.move(1, 160, 100);
        hand.down(2, 300, 100, 0.1f);
        hand.up(2, 300, 100, 0.2f);
        hand.up(1, 160, 100, 0.3f);

        assertEquals(List.of("panBegin 100.0,100.0", "pan 160.0,100.0", "panEnd"), heard);
        assertFalse(hand.touching());
    }
}
