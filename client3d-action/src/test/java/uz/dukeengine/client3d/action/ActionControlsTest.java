package uz.dukeengine.client3d.action;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.SkillRange;
import uz.dukeengine.action.ActionOrders;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.core.thing.ObjectId;

/** The action scheme: a held mouse, a camera it turns, skills aimed ahead, a strike ahead (E90-E93). */
class ActionControlsTest {

    private static final List<ObjectId> HERO = List.of(new ObjectId(1));

    @Test
    void theHeldMouseTurnsTheFollowRoundHimAndLeavesItsPitch() {
        var look = CameraFollow.of(null).orbiting(CameraFollow.ORBIT_SENSITIVITY, false, 0f);
        var camera = new TestWindow();
        var controls = new ActionControls().follow(look).holdMouse(true);
        controls.attach(camera);
        camera.moved = new float[] {100f, 30f};
        float pitch = camera.pitch();
        controls.frame(1f / 30f);
        assertEquals(-100f * CameraFollow.ORBIT_SENSITIVITY, camera.yaw(), 1e-5f, "a hundred pixels' turn");
        assertEquals(pitch, camera.pitch(), "band 0: its pitch its own");
        camera.lookAt(100f, 100f);
        new CameraFollow.Eye().step(look, camera, 100f, 100f, 0f, false, false, 1f, 1f);
        assertEquals(100f, camera.lookingAt()[0], 1e-3f, "still on him");
        assertEquals(100f * CameraFollow.ORBIT_SENSITIVITY,
                look.orbiting(CameraFollow.ORBIT_SENSITIVITY, true, 0f).turnOf(100f), 1e-6f, "inverted");
    }

    @Test
    void aSkillAimedAheadPointsAlongTheForwardAtADistanceTheMouseSets() {
        var spot = new ForwardAim(60f, SkillRange.Shape.AT_A_SPOT);
        assertEquals(40f, spot.distance(), 1e-4f, "two thirds of its reach at a spot");
        spot.nudge(-40f); // up: ten further
        assertEquals(50f, spot.distance(), 1e-4f);
        spot.nudge(-1000f);
        assertEquals(60f, spot.distance(), 1e-4f, "never past its reach");
        var at = spot.point(100f, 100f, 0f);
        assertEquals(100f, at[0], 1e-4f);
        assertEquals(40f, at[1], 1e-4f, "unturned, up the screen is -y: sixty along it");
        assertEquals(60f, new ForwardAim(60f, SkillRange.Shape.DOWN_A_LANE).distance(), 1e-4f, "a lane all of it");
    }

    @Test
    void theLeftButtonStrikesAheadSoftLockingTheEnemyInItsCone() {
        var scheme = MouseScheme.ACTION;
        var near10 = new MouseScheme.Candidate(7, 100f + 50f * (float) Math.cos(Math.toRadians(10)),
                100f + 50f * (float) Math.sin(Math.toRadians(10)));
        var off40 = new MouseScheme.Candidate(8, 100f + 40f * (float) Math.cos(Math.toRadians(40)),
                100f + 40f * (float) Math.sin(Math.toRadians(40)));

        var swing = new MouseScheme.Swing();
        assertEquals(List.of(new CombatOrder.AttackObject(0, HERO, new ObjectId(7))),
                swing.pressAhead(scheme, 0, HERO, 100f, 100f, 120f, 0f, List.of(near10, off40), 0),
                "the one ten degrees off is in the cone");

        var only40 = new MouseScheme.Swing().pressAhead(scheme, 0, HERO, 100f, 100f, 120f, 0f, List.of(off40), 0);
        var shot = (uz.dukeengine.combat.message.GameOrder) only40.getFirst();
        assertEquals(220f, shot.place().x(), 1e-3f, "none in the cone: straight ahead at his full reach");
        assertEquals(100f, shot.place().y(), 1e-3f);

        var melee = (uz.dukeengine.combat.message.GameOrder) new MouseScheme.Swing()
                .pressAhead(scheme, 0, HERO, 100f, 100f, 12f, 0f, List.of(near10), 0).getFirst();
        assertEquals(112f, melee.place().x(), 1e-3f, "a sword swings at the point its reach ahead");
    }
}
