package uz.dukeengine.combat.module;

import java.util.List;
import uz.dukeengine.core.thing.GameObject;

/**
 * A module told each time its thing's {@link AuraUpdate} looks who is within its reach, on the simulation thread,
 * after the aura's own words and heal are given: what a game gives the things found there beyond them — a fountain's
 * mana, a pulse of health shown as a number over each head.
 */
public interface AuraListener {

    /** {@code aura} has looked, and found {@code inside}, in the order the world keeps its things. */
    void onPulse(AuraUpdate aura, List<GameObject> inside);
}
