package uz.dukeengine.client3d.touch;

import static com.jme3.input.event.TouchEvent.Type.DOWN;
import static com.jme3.input.event.TouchEvent.Type.MOVE;
import static com.jme3.input.event.TouchEvent.Type.UP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.ControlScheme;
import uz.dukeengine.client3d.SkillRange;
import uz.dukeengine.core.thing.ObjectId;

/** The scheme's calls for each finger; the window is a recorder, 1000 x 600, and the hero stands at (30, 40). */
class TouchHeroTest {

    /** The strike button's centre (928, 72); the skills stand left of it, 120 px apart. */
    private static final float STRIKE_X = 928f;
    private static final float Q_X = 808f;
    private static final float BUTTON_Y = 72f;

    private final List<String> calls = new ArrayList<>();
    private float yaw;
    private boolean playing = true;
    /** How dense the screen is against the design: 2 emulates a phone on the desktop. */
    private float uiScale = 1f;
    /** Whether the client arms a pressed skill and leaves it for the scheme to aim, as it does for an aimed one. */
    private boolean skillIsAimed;
    private TouchHero scheme;

    private ControlScheme.Context window() {
        InvocationHandler recorder = (proxy, method, args) -> switch (method.getName()) {
            case "input", "gui", "font", "game" -> null;
            case "playing" -> playing;
            case "uiScale" -> uiScale;
            case "yaw" -> yaw;
            case "screenSize" -> new float[] {1000f, 600f};
            case "selected" -> List.of(new ObjectId(1));
            case "drawnAt" -> new float[] {30f, 40f};
            case "pressSkill" -> {
                calls.add("pressSkill " + List.of(args));
                if (skillIsAimed) {
                    scheme.armed((char) args[0], true, null);
                }
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

    private TouchHero hero() {
        scheme = new TouchHero()
                .walk((game, angle) -> calls.add("walk " + angle))
                .strike((game, forward, held) -> calls.add("strike " + Math.round(Math.toDegrees(forward)) + " "
                        + held))
                .skills('q', 'w');
        scheme.attach(window());
        return scheme;
    }

    @Test
    void theLeftHalfIsAFloatingStickThatWalksAndStops() {
        var scheme = hero();
        scheme.touch(DOWN, 1, 200, 300);
        scheme.frame(0f);
        assertTrue(calls.isEmpty(), "a touch alone is only the centre");

        scheme.touch(MOVE, 1, 200, 400); // up the screen
        scheme.frame(0f);
        scheme.frame(0f);
        assertEquals(List.of("walk 192"), calls, "told once, not every frame");

        scheme.touch(UP, 1, 200, 400);
        scheme.frame(0f);
        assertEquals(List.of("walk 192", "walk -1"), calls);
    }

    @Test
    void aMenuOpeningStopsTheHero() {
        var scheme = hero();
        scheme.touch(DOWN, 1, 200, 300);
        scheme.touch(MOVE, 1, 300, 300);
        scheme.frame(0f);
        playing = false;
        scheme.frame(0f);

        assertEquals(List.of("walk 0", "walk -1"), calls);
    }

    @Test
    void theRightHalfTurnsTheCameraWhileTheStickWalks() {
        var scheme = hero();
        scheme.touch(DOWN, 1, 200, 300);
        scheme.touch(MOVE, 1, 200, 400);
        scheme.touch(DOWN, 2, 600, 300);
        scheme.touch(MOVE, 2, 700, 300); // 100 px right
        scheme.touch(DOWN, 3, 100, 100); // a second finger on the left half: nothing, the stick has one
        scheme.touch(MOVE, 3, 100, 200);
        scheme.frame(0f);

        assertEquals(List.of("turnBy [" + -100 * TouchHero.TURN_PER_PIXEL + "]", "walk 192"), calls);
    }

    @Test
    void onADenseScreenTheSameLengthOfFingerTurnsAsFar() {
        uiScale = 2f;
        var scheme = hero();
        scheme.touch(DOWN, 2, 600, 300);
        scheme.touch(MOVE, 2, 800, 300); // 200 px of a screen twice as dense: 100 at the design's density

        assertTrue(calls.contains("turnBy [" + -100 * TouchHero.TURN_PER_PIXEL + "]"), calls.toString());
    }

    @Test
    void holdingHitStrikesAlongTheForwardAndTurnsFollow() {
        var scheme = hero();
        scheme.touch(DOWN, 1, STRIKE_X, BUTTON_Y);
        assertEquals(List.of("strike -90 true"), calls, "the camera at yaw 0 looks up the map: -y");

        scheme.frame(0f);
        assertEquals(1, calls.size(), "nothing new while the view is still");

        yaw = (float) (Math.PI / 2);
        scheme.frame(0f);
        assertEquals("strike 180 true", calls.get(1), "the swing follows the turned camera");

        scheme.touch(UP, 1, STRIKE_X, BUTTON_Y);
        assertEquals("strike 180 false", calls.get(2));
        scheme.frame(0f);
        assertEquals(3, calls.size());
    }

    @Test
    void aFingerOnAButtonMakesNeitherAStickNorATurn() {
        var scheme = hero();
        scheme.touch(DOWN, 1, STRIKE_X, BUTTON_Y);
        scheme.touch(MOVE, 1, STRIKE_X + 50, BUTTON_Y + 50);
        scheme.frame(0f);

        assertEquals(List.of("strike -90 true"), calls);
    }

    @Test
    void anUnaimedSkillIsJustPressed() {
        var scheme = hero();
        scheme.touch(DOWN, 1, Q_X, BUTTON_Y);
        scheme.touch(UP, 1, Q_X, BUTTON_Y);

        assertEquals(List.of("pressSkill [Q]"), calls);
    }

    @Test
    void anAimedSkillIsAimedAheadAndCastWhenTheFingerLifts() {
        skillIsAimed = true;
        var scheme = hero();
        scheme.touch(DOWN, 1, Q_X, BUTTON_Y);
        assertTrue(scheme.aiming());
        scheme.touch(DOWN, 2, 600, 300); // the other thumb turns the view meanwhile
        scheme.touch(UP, 2, 600, 300);
        scheme.touch(UP, 1, Q_X, BUTTON_Y);

        assertEquals(List.of("pressSkill [Q]", "castArmed"), calls);
    }

    @Test
    void theAimIsFromTheHeroAlongTheCameraForward() {
        var scheme = hero();
        scheme.armed('Q', true, new SkillRange('Q', SkillRange.Shape.DOWN_A_LANE, 12f, 0f));

        var at = scheme.aimPoint();
        assertEquals(30f, at[0], 1e-3);
        assertEquals(28f, at[1], 1e-3, "all of a lane's reach, up the map from (30, 40)");
    }

    @Test
    void onAScreenTwiceAsDenseTheButtonsAreTwiceAsBigAndTwiceAsFarFromTheEdge() {
        uiScale = 2f;
        var scheme = hero();
        // Strike's centre is (1000 - 48 - 96, 48 + 96) = (856, 144); 90 px off is still on a 192 px button.
        scheme.touch(DOWN, 1, 856f + 90f, 144f + 90f);
        assertEquals(List.of("strike -90 true"), calls);
    }

    @Test
    void theSameTouchOnTheDesignsDensityIsNoButton() {
        var scheme = hero();
        scheme.touch(DOWN, 1, 856f + 90f, 144f + 90f); // right half, away from the 96 px button at (928, 72)
        scheme.frame(0f);
        assertTrue(calls.isEmpty(), "a look finger, nothing struck");
    }

    @Test
    void theStickReachesTwiceAsFarOnAScreenTwiceAsDense() {
        uiScale = 2f;
        var scheme = hero();
        scheme.touch(DOWN, 1, 200, 300);
        scheme.touch(MOVE, 1, 200 + VirtualStick.RADIUS, 300); // a design radius: only half way on this screen
        scheme.frame(0f);
        // half the scaled radius: (0.5 - 0.2) / 0.8 is told as a direction, and the knob has not reached the ring
        assertEquals(List.of("walk 0"), calls);
    }
}
