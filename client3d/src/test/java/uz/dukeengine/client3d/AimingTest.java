package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.view.CommandButton;

/** A button on the game's own canvas aims as the client's bar aims: armed, put down, given up. */
class AimingTest {

    private static final CommandButton BARRACKS =
            new CommandButton("build:barracks", null, "Barracks", "B", true, CommandButton.Aim.GROUND, "barracks", 90f);
    private static final CommandButton POWER_PLANT =
            new CommandButton("build:power", null, "Power plant", "P", true, CommandButton.Aim.GROUND, "power", 0f);

    private final Aiming aiming = new Aiming();
    private final List<AimOutcome> heard = new ArrayList<>();

    @Test
    void armingAPlacingButtonPutsItsGhostAtTheCursor() {
        aiming.arm(BARRACKS, false, 0f, null, heard::add);

        aiming.placement().cursor(400f, 300f, new Coord3D(12f, 34f, 0f));

        assertTrue(aiming.isArmed());
        assertEquals(new Coord3D(12f, 34f, 0f), aiming.placement().where(), "the ghost stands where the cursor is");
        assertEquals(90f, aiming.placement().facing(), "facing the button's way until it is turned");
        assertTrue(heard.isEmpty(), "nothing has ended yet");
    }

    @Test
    void givenUpByTheGameItSendsNothingShowsNoCircleOrPointerAndItsArmerHearsGivenUpOnce() {
        aiming.arm(BARRACKS, false, 40f, "Aim", heard::add);

        aiming.giveUp();
        aiming.giveUp();

        assertFalse(aiming.isArmed());
        assertEquals(0f, aiming.radius(), "no circle");
        assertNull(aiming.pointer(), "the usual pointer");
        assertEquals(List.of(AimOutcome.GIVEN_UP), heard, "told once");
    }

    @Test
    void usedByTheGameAtAPlaceItIsThePressThereFacingItsWayAndItsArmerHearsUsed() {
        aiming.arm(BARRACKS, false, 40f, "Aim", heard::add);

        var press = aiming.useAt(new Coord3D(300f, 400f, 0f));

        assertEquals(new Aiming.Press(BARRACKS, new Coord3D(300f, 400f, 0f), 90f, -1), press);
        assertEquals(List.of(AimOutcome.USED), heard);
        assertFalse(aiming.isArmed());
        assertNull(aiming.useAt(new Coord3D(300f, 400f, 0f)), "used once");
    }

    @Test
    void withNothingArmedNeitherDoesAnything() {
        assertNull(aiming.useAt(new Coord3D(300f, 400f, 0f)));
        aiming.giveUp();
        assertTrue(heard.isEmpty());
    }

    @Test
    void aClickWhereItFitsIsThePressWithThatPlaceAndFacing() {
        aiming.arm(BARRACKS, false, 0f, null, heard::add);
        aiming.placement().cursor(400f, 300f, new Coord3D(12f, 34f, 0f));
        aiming.placement().press(400f, 300f, new Coord3D(12f, 34f, 0f));

        var refused = new java.util.ArrayList<Aiming.Press>();
        var press = aiming.putDown(true, refused::add);

        assertNotNull(press);
        assertEquals("build:barracks", press.button().id());
        assertEquals(new Coord3D(12f, 34f, 0f), press.place());
        assertEquals(90f, press.facing());
        assertEquals(List.of(AimOutcome.USED), heard);
        assertFalse(aiming.isArmed());
        assertTrue(refused.isEmpty(), "taken, and told as ever: never refused");
    }

    @Test
    void aClickWhereItDoesNotFitSendsNothingAndKeepsAiming() {
        aiming.arm(BARRACKS, false, 0f, null, heard::add);
        aiming.placement().press(400f, 300f, new Coord3D(12f, 34f, 0f));

        var refused = new java.util.ArrayList<Aiming.Press>();
        assertNull(aiming.putDown(false, refused::add));
        assertTrue(aiming.isArmed(), "still waiting for somewhere it fits");
        assertTrue(heard.isEmpty());
        assertEquals(1, refused.size(), "the refusal told");
        assertEquals("build:barracks", refused.getFirst().button().id());
        assertEquals(new Coord3D(12f, 34f, 0f), refused.getFirst().place(), "and where");
    }

    @Test
    void aRightClickGivesItUpWithoutAPress() {
        aiming.arm(BARRACKS, false, 0f, null, heard::add);

        aiming.giveUp();

        assertFalse(aiming.isArmed());
        assertNull(aiming.placement(), "no ghost left to put down");
        assertEquals(List.of(AimOutcome.GIVEN_UP), heard);
    }

    @Test
    void armingAnotherGivesTheFirstUp() {
        var first = new ArrayList<AimOutcome>();
        aiming.arm(BARRACKS, false, 0f, null, first::add);

        aiming.arm(POWER_PLANT, false, 0f, null, heard::add);

        assertEquals(List.of(AimOutcome.GIVEN_UP), first);
        assertEquals("build:power", aiming.button().id());
        assertTrue(heard.isEmpty());
    }

    @Test
    void aThingAimedAtIsThePressesTarget() {
        var attack = new CommandButton("power:strike", null, "Strike", "S", true, CommandButton.Aim.UNIT, null);
        aiming.arm(attack, false, 150f, "target", heard::add);

        assertNull(aiming.placement());
        assertEquals(150f, aiming.radius());
        assertEquals("target", aiming.pointer());

        var press = aiming.target(7);

        assertEquals(7, press.target());
        assertNull(press.place());
        assertEquals(List.of(AimOutcome.USED), heard);
    }
}
