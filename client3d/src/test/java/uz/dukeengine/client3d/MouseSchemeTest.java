package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/** The modern mouse: a lock on an enemy, a held swing on the ground, the right button for context alone (E78). */
class MouseSchemeTest {

    private static final List<ObjectId> HERO = List.of(new ObjectId(1));

    @Test
    void leftOnAnEnemyLocksOnAndOnTheFloorSwingsHeldUntilLetGo() {
        var scheme = MouseScheme.MODERN;
        var swing = new MouseScheme.Swing();
        assertEquals(List.of(new CombatOrder.AttackObject(0, HERO, new ObjectId(9))),
                swing.press(scheme, 0, HERO, 9, 30f, new Coord3D(5f, 5f, 0f), 0), "a skeleton: locked on");
        assertFalse(swing.isHeld());

        var far = swing.press(scheme, 0, HERO, 9, 80f, new Coord3D(5f, 5f, 0f), 0);
        assertEquals(List.of(new CombatOrder.AttackToward(0, HERO, new Coord3D(5f, 5f, 0f), true)), far,
                "past the lock's reach: a swing toward the ground");
        swing.release(0, HERO);

        var floor = new Coord3D(10f, 0f, 0f);
        assertEquals(List.of(new CombatOrder.AttackToward(0, HERO, floor, true)),
                swing.press(scheme, 0, HERO, -1, Float.MAX_VALUE, floor, 10));
        assertTrue(swing.drag(0, HERO, new Coord3D(11f, 0f, 0f), 20).isEmpty(), "moved too little");
        assertTrue(swing.drag(0, HERO, new Coord3D(20f, 0f, 0f), 11).isEmpty(), "too soon");
        assertEquals(1, swing.drag(0, HERO, new Coord3D(20f, 0f, 0f), 13).size(), "far enough, late enough");
        assertEquals(List.of(new CombatOrder.AttackToward(0, HERO, new Coord3D(20f, 0f, 0f), false)),
                swing.release(0, HERO), "let go");
    }

    @Test
    void theRightButtonOrdersOnlyWhatTheGameNamesUnlessItWalks() {
        assertTrue(MouseScheme.MODERN.rightOrders(true, false), "a tree's Chop");
        assertTrue(MouseScheme.MODERN.rightOrders(false, true), "a thing");
        assertFalse(MouseScheme.MODERN.rightOrders(false, false), "plain ground: nothing");
        assertTrue(new MouseScheme(true, 60f, true, true).rightOrders(false, false), "unless it walks");
        assertTrue(MouseScheme.CLASSIC.rightOrders(false, false), "classic, as ever");
    }
}
