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
import uz.dukeengine.game.view.CommandButton;

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
    void aClickWhereItFitsIsThePressWithThatPlaceAndFacing() {
        aiming.arm(BARRACKS, false, 0f, null, heard::add);
        aiming.placement().cursor(400f, 300f, new Coord3D(12f, 34f, 0f));
        aiming.placement().press(400f, 300f, new Coord3D(12f, 34f, 0f));

        var press = aiming.putDown(true);

        assertNotNull(press);
        assertEquals("build:barracks", press.button().id());
        assertEquals(new Coord3D(12f, 34f, 0f), press.place());
        assertEquals(90f, press.facing());
        assertEquals(List.of(AimOutcome.USED), heard);
        assertFalse(aiming.isArmed());
    }

    @Test
    void aClickWhereItDoesNotFitSendsNothingAndKeepsAiming() {
        aiming.arm(BARRACKS, false, 0f, null, heard::add);
        aiming.placement().press(400f, 300f, new Coord3D(12f, 34f, 0f));

        assertNull(aiming.putDown(false));
        assertTrue(aiming.isArmed(), "still waiting for somewhere it fits");
        assertTrue(heard.isEmpty());
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
