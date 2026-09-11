package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import org.junit.jupiter.api.Test;

/**
 * Where the little camera stands, which is the whole of what makes one block
 * serve every creature in the game.
 *
 * <p>Everything here is measured off the creature rather than written down for
 * it, so this is the arithmetic that decides whether an unmeasured monster gets a
 * portrait of its face or a portrait of its knees. It is also the part a chair
 * catches late and badly: a camera a third of a body too low reads as "the art is
 * wrong", not as "the number is".
 */
class PortraitCameraTest {

    /** A creature whose box runs from {@code bottom} to {@code top}. */
    private static Spatial boxed(float bottom, float top) {
        float height = top - bottom;
        var shape = new Geometry("body", new Box(0.5f, height / 2f, 0.5f));
        shape.setLocalTranslation(0f, bottom + height / 2f, 0f);
        var holder = new Node("holder");
        holder.attachChild(shape);
        holder.updateGeometricState();
        return holder;
    }

    /** An ordinary creature: it stands on the floor, so its box is its height. */
    @Test
    void somethingStandingOnItsFeetIsAsTallAsItsBox() {
        assertEquals(2.275f, HeroPortrait.standingHeight(boxed(0f, 2.275f)), 0.001f);
    }

    /**
     * Something with a tail below the floor is measured by what is above it.
     *
     * <p>The dungeon's two drifting creatures are modelled with something trailing
     * under the ground they hover over: the Stalker's box runs from -0.88 to 2.17,
     * so it is 3.05 tall and stands 2.17. Measured by the box, every fraction is
     * computed against a third more creature than there is, and the camera looks a
     * third of a body too low — on exactly the two nobody would think to check,
     * because every other creature in the game is fine.
     */
    @Test
    void somethingTrailingBelowTheFloorIsMeasuredByWhatStandsAboveIt() {
        var stalker = boxed(-0.879f, 2.166f);

        assertEquals(2.166f, HeroPortrait.standingHeight(stalker), 0.001f,
                "measured by its box it would be 3.045 tall");
    }

    /** And a creature drawn entirely below the origin still gives a usable number. */
    @Test
    void somethingEntirelyBelowTheOriginIsStillMeasured() {
        assertTrue(HeroPortrait.standingHeight(boxed(-3f, -1f)) > 0f,
                "a height of zero would put the camera on top of it");
    }

    /** Nothing to measure: the fractions then mean world units, and nothing breaks. */
    @Test
    void somethingWithNoShapeAtAllIsOneUnitTall() {
        assertEquals(1f, HeroPortrait.standingHeight(new Node("empty")), 0.001f);
    }

    /**
     * The frame holds what {@code show} says, whatever the creature's height.
     *
     * <p>The promise the whole arrangement rests on. A skeleton and a hero of
     * different heights both come out framed the same way, so the numbers in one
     * block are true of every creature rather than of the one somebody tuned them
     * against.
     */
    @Test
    void theFrameHoldsTheSameShareOfACreatureWhateverItsHeight() {
        var camera = new PortraitLook.Camera(0.74f, 0.5f, 0f, 0f, 34f);

        for (float height : new float[] {1f, 2.166f, 2.275f, 9f}) {
            float distance = camera.distanceFor(height);
            // What a lens of this angle sees at that distance, by the same
            // trigonometry read the other way round.
            float visible = 2f * distance
                    * com.jme3.math.FastMath.tan(com.jme3.math.FastMath.DEG_TO_RAD * 34f / 2f);
            assertEquals(0.5f * height, visible, 0.001f * height,
                    "a creature " + height + " tall was not framed on half of itself");
        }
    }

    /**
     * Widening the lens does not zoom out.
     *
     * <p>Which is the reason the framing is said outright rather than left to fall
     * out of a distance and a lens. Written that way round, every creature's two
     * numbers have to be re-tuned together, and the one thing anybody reaches for
     * — a slightly wider lens, to bend the face less — silently makes the head
     * smaller.
     */
    @Test
    void wideningTheLensDoesNotZoomOut() {
        var narrow = new PortraitLook.Camera(0.74f, 0.5f, 0f, 0f, 20f);
        var wide = new PortraitLook.Camera(0.74f, 0.5f, 0f, 0f, 60f);

        assertTrue(wide.distanceFor(2.275f) < narrow.distanceFor(2.275f),
                "a wider lens has to come closer to hold the same amount of him");
    }

    /**
     * The frame stops just past the top of the creature's head.
     *
     * <p>The number a chair caught and no arithmetic did. This kit's characters
     * are drawn with a head nearly half their height — the head joint is at 0.55
     * of the model and the top of the hair at 1.0 — so the first camera, looking
     * at 0.86 and holding 0.46 of him, framed 0.63 to 1.09: the middle of the
     * forehead upward, and the face below the bottom edge.
     */
    @Test
    void theDefaultFrameEndsJustPastTheTopOfHisHead() {
        var camera = PortraitLook.Camera.DEFAULT;

        float top = camera.head() + camera.show() / 2f;
        float bottom = camera.head() - camera.show() / 2f;

        assertTrue(top >= 0.95f && top <= 1.1f,
                "the frame ends at " + top + " of him rather than at the top of his head");
        assertTrue(bottom >= 0.4f && bottom <= 0.6f,
                "the frame starts at " + bottom + " of him: head and shoulders, not a bust"
                        + " and not a whole body");
    }

    /** A lens of zero degrees is not a lens, and must not be a division by zero. */
    @Test
    void anImpossibleLensIsStillANumber() {
        var broken = new PortraitLook.Camera(0.74f, 0.5f, 0f, 0f, 0f);

        float distance = broken.distanceFor(2f);

        assertTrue(Float.isFinite(distance) && distance > 0f,
                "a zero-degree lens gave " + distance);
    }

    /** The default look is a whole look, whatever a game leaves out of it. */
    @Test
    void aLookWithNothingInItIsStillALook() {
        var empty = new PortraitLook(null, null, -5f, 0f);

        assertEquals(PortraitLook.Camera.DEFAULT, empty.camera());
        assertEquals(PortraitLook.Clips.NONE, empty.clips());
        assertEquals(0f, empty.hurtBelowPercent(), 0.001f);
        assertEquals(1f, empty.hurtSpeed(), 0.001f, "a speed of zero is a frozen portrait");
    }

    /** And the bounding box a test hands in is the one jME would build. */
    @Test
    void theShapeUsedHereIsMeasuredTheWayJmeMeasuresIt() {
        var stalker = boxed(-0.879f, 2.166f);

        assertTrue(stalker.getWorldBound() instanceof BoundingBox box
                && box.getCenter().distance(new Vector3f(0f, 0.6435f, 0f)) < 0.01f,
                "the fixture is not building the box this is meant to be about");
    }
}
