package uz.duke.dungeon.ai;

import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.rts.module.WeaponUpdate;

/**
 * What a creature is doing this instant, in the four words the order buttons use.
 *
 * <p>The buttons beside the map were four things to press and nothing else, which
 * is half a control: every one of them is also a <em>state</em> the creature can
 * be in, and a player who can see which one is lit never has to wonder whether
 * the order he gave took. Nobody plays by clicking those buttons — the mouse and
 * the keys are faster and always were — so what earns their space on the bar is
 * what they can tell him.
 *
 * <p><b>One of the four, always.</b> A creature is never doing none of these and
 * never two: it is standing because it was told to, or fighting, or walking, or
 * simply guarding the ground it is on. The order they are asked in is the order
 * of what overrides what.
 */
public enum Doing {

    /** Walking somewhere. */
    WALKING,

    /**
     * Fighting something.
     *
     * <p>Which includes walking at something it was sent to fight. The order is
     * the attack; the walking is how it is being carried out, and a button that
     * said "walking" while he closed on a skeleton he had been pointed at would be
     * answering a question nobody asked.
     */
    FIGHTING,

    /**
     * Standing still, watching the ground it is on, and it will start a fight with
     * anything that comes near.
     *
     * <p>The ordinary idle state, and the one that had no name before: a creature
     * with nothing to do is not doing nothing.
     */
    GUARDING,

    /**
     * Told to stop, and it will start nothing.
     *
     * <p>The one state that is a <em>standing order</em> rather than an
     * observation. Everything else here is read off the creature; this is read off
     * what the player last said, which is why it outranks them — a creature that
     * has been told to stand still is standing still whatever else is true.
     */
    STANDING;

    /**
     * What this creature is doing, given whether its player has told it to stop.
     *
     * <p>Read off the modules rather than off any brain, so it is the same answer
     * for a hero, a skeleton and anything added later — the panel describes
     * whatever is selected, and most of what is selected has no brain of ours.
     */
    public static Doing of(GameObject unit, boolean stopped) {
        if (stopped) {
            return STANDING;
        }
        if (unit == null) {
            return GUARDING;
        }
        var weapon = unit.findModule(WeaponUpdate.class);
        if (weapon != null && weapon.isAttacking()) {
            return FIGHTING;
        }
        var legs = unit.findModule(MoveUpdate.class);
        return legs != null && legs.isMoving() ? WALKING : GUARDING;
    }

    /**
     * Which of the four buttons this lights, counting from the left.
     *
     * <p>The buttons are walk, attack, stop, guard — in that order, because that
     * is the order they are written in the file and the order a player reads them.
     */
    public int button() {
        return switch (this) {
            case WALKING -> 0;
            case FIGHTING -> 1;
            case STANDING -> 2;
            case GUARDING -> 3;
        };
    }
}
