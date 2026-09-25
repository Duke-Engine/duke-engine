package uz.dukeengine.core.module;

/**
 * A module that may pass its thing off to some players as none of their targets: shown to them, but not acquired by
 * their side's weapons nor taken as the target of their orders unless the order is forced — the reference's bomb truck
 * disguised as a unit of theirs ({@code WeaponSet::getAbleToAttackSpecificObject}). Any module of a thing saying so
 * passes it off. Asked on the simulation thread, so the answer is a pure function of the simulation's state; a thing is
 * never passed off to its own side.
 */
public interface Disguise {

    /** Whether its thing passes, to {@code player} now, for none of his side's targets. */
    boolean fools(int player);
}
