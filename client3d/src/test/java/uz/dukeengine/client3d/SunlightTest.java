package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Where the light comes from.
 *
 * <p>Two questions worth holding still, and they are not the same question. One is
 * that turning three hard-coded numbers into a record did not move the light: a
 * change meant to let a game <em>choose</em> its lighting must not quietly relight
 * every scene on its way past. The other is the thing the choice is for — that the
 * pitch is what decides whether a floor and a wall are shaded differently at all,
 * which is the whole of why a player said the map looked flat.
 */
class SunlightTest {

    /** The vector the client had written into it before a game could say. */
    private static final Vector3f AS_IT_WAS =
            new Vector3f(-0.4f, -1f, -0.5f).normalizeLocal();

    /**
     * The default lights a scene exactly as the constants did.
     *
     * <p>To within a hundredth, because the angles are written to two places and
     * the vector was not written as angles at all. What matters is that nobody
     * opening the game after this change sees a different picture until somebody
     * edits the file on purpose.
     */
    @Test
    void theDefaultIsTheLightTheClientAlwaysHad() {
        var was = Sunlight.DEFAULT.direction();

        assertEquals(AS_IT_WAS.x, was.x, 0.01f);
        assertEquals(AS_IT_WAS.y, was.y, 0.01f);
        assertEquals(AS_IT_WAS.z, was.z, 0.01f);
        assertEquals(1f, Sunlight.DEFAULT.sunColour().r, 0.01f, "the sun was white");
        assertEquals(0.9f, Sunlight.DEFAULT.sunColour().b, 0.01f, "and a little warm");
        assertEquals(0.45f, Sunlight.DEFAULT.ambientColour().r, 0.01f,
                "and the shade was a cold half-light");
        assertEquals(0.5f, Sunlight.DEFAULT.ambientColour().b, 0.01f);
    }

    /**
     * A sun overhead shades every wall alike, and a sun off to one side does not.
     *
     * <p>The reason any of this is a setting, and it is <em>not</em> the difference
     * between a wall and the ground — straight overhead that difference is at its
     * largest, with the floor blown out and every wall black. What goes is the
     * difference <b>between one wall and another</b>: a face's share of the light
     * comes from the sun's horizontal part, which at 90 degrees is nothing at all,
     * so a wall running north and a wall running east are the same shade of dark
     * and nothing in the picture has any form. That is what "flat" means here.
     *
     * <p>What the spread comes to is the cosine of the pitch: 0 overhead, 0.54 at
     * the 57 this used to stand at, 0.77 at the 40 it stands at now.
     */
    @Test
    void aSunOverheadShadesEveryWallAlikeAndOneOffToTheSideDoesNot() {
        assertEquals(0f, reliefAmongWalls(new Sunlight(90f, 0f, 1f, 0f, 0xFFFFFF, 0xFFFFFF)),
                0.01f, "straight overhead, every upright face is the same dark");
        assertTrue(reliefAmongWalls(Sunlight.DEFAULT) > 0.4f,
                "and off to one side a wall has a lit side and a shaded one");
    }

    /**
     * The shader's own arithmetic — {@code max(dot(normal, -sunDirection), 0)} —
     * asked of one surface.
     */
    private static float lit(Sunlight sun, Vector3f normal) {
        return Math.max(normal.dot(sun.direction().negate()), 0f);
    }

    /**
     * How far apart the best-lit upright face and the worst-lit one are.
     *
     * <p>Over every way a wall can face, which is the whole of what makes a
     * building read as one rather than as a floor plan with grey stripes on it.
     */
    private static float reliefAmongWalls(Sunlight sun) {
        float brightest = 0f;
        float darkest = 1f;
        for (int degrees = 0; degrees < 360; degrees += 15) {
            float radians = (float) Math.toRadians(degrees);
            float share = lit(sun, new Vector3f((float) Math.sin(radians), 0f,
                    (float) Math.cos(radians)));
            brightest = Math.max(brightest, share);
            darkest = Math.min(darkest, share);
        }
        return brightest - darkest;
    }

    /** A pitch nobody could see by is pulled back to one they can. */
    @Test
    void aSunBelowTheHorizonIsBroughtBackAboveIt() {
        assertTrue(new Sunlight(-30f, 0f, 1f, 1f, 0xFFFFFF, 0xFFFFFF).direction().y < 0f,
                "light travelling upward lights nothing at all");
    }

    /** Strength and ambient are separate knobs, and turning one leaves the other. */
    @Test
    void strengthAndAmbientAreTurnedSeparately() {
        var dim = new Sunlight(45f, 0f, 0.5f, 0.25f, 0xFFFFFF, 0xFFFFFF);

        assertEquals(0.5f, dim.sunColour().r, 0.001f);
        assertEquals(0.25f, dim.ambientColour().r, 0.001f);
        assertEquals(Sunlight.DEFAULT.direction(),
                new Sunlight(Sunlight.DEFAULT.pitch(), Sunlight.DEFAULT.yaw(), 0.1f, 0.1f,
                        0x000000, 0x000000).direction(),
                "and neither of them moves the sun");
    }
}
