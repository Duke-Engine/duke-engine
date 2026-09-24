package uz.dukeengine.client3d;

import java.util.function.Consumer;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.view.CommandButton;

/**
 * A command button waiting for its place or its thing — armed from the client's own bar, or by the game's code for a
 * button on its own canvas — and what a click, a drag, a right click or Escape make of it. One at a time: arming
 * another gives the first up. Whoever armed it is told how it ended.
 *
 * <p>Held apart from the window so it can be checked without one; the window draws the ghost where
 * {@link #placement()} says and sends the {@link Press} this hands back.
 */
final class Aiming {

    /** A press to send: the button, and its place and facing or the thing it was aimed at. */
    record Press(CommandButton button, Coord3D place, float facing, int target) {
    }

    private CommandButton armed;
    private PlacementDrag placement;
    private Consumer<AimOutcome> told;
    private float radius;
    private String pointer;
    private boolean fromTheBar;

    /**
     * Arm {@code button}: whatever was armed is given up first.
     *
     * @param fromTheBar whether the client's own bar armed it — given up when the bar stops offering it
     * @param radius     a circle drawn round the cursor on the ground, in world units; 0 for none
     * @param pointer    the pointer shown while it is armed, a situation the game named; null for the usual
     * @param told       told how it ended, or null
     */
    void arm(CommandButton button, boolean fromTheBar, float radius, String pointer, Consumer<AimOutcome> told) {
        giveUp();
        this.armed = button;
        this.fromTheBar = fromTheBar;
        this.radius = Math.max(0f, radius);
        this.pointer = pointer;
        this.told = told;
        this.placement = button.aim() == CommandButton.Aim.GROUND ? new PlacementDrag(button.facing()) : null;
    }

    boolean isArmed() {
        return armed != null;
    }

    /** The button armed, or null. */
    CommandButton button() {
        return armed;
    }

    /** Where a button that aims at the ground would put its thing and which way it faces; null for any other. */
    PlacementDrag placement() {
        return placement;
    }

    float radius() {
        return radius;
    }

    String pointer() {
        return pointer;
    }

    boolean fromTheBar() {
        return fromTheBar;
    }

    /** Given up — nothing sent — and whoever armed it told so. */
    void giveUp() {
        if (armed != null) {
            end(AimOutcome.GIVEN_UP);
        }
    }

    /** A thing clicked, for a button that aims at one: the press to send, and the aim used. */
    Press target(int unitId) {
        var button = armed;
        end(AimOutcome.USED);
        return new Press(button, null, 0f, unitId);
    }

    /**
     * The press let go, for a button that aims at the ground: the press to send where the place fits — facing the way
     * it was dragged, or the button's way — or null where it does not, the aim still armed for somewhere that does.
     */
    Press putDown(boolean fits) {
        var placed = placement.release();
        if (placed == null || !fits) {
            return null;
        }
        var button = armed;
        end(AimOutcome.USED);
        return new Press(button, placed.place(), placed.facing(), -1);
    }

    private void end(AimOutcome outcome) {
        var ear = told;
        armed = null;
        placement = null;
        told = null;
        radius = 0f;
        pointer = null;
        fromTheBar = false;
        if (ear != null) {
            ear.accept(outcome);
        }
    }
}
