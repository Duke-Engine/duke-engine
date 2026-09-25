package uz.dukeengine.rts;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;

/**
 * How the units a {@code MoveTo} names are sent to its one point: where each goes, and by what way. {@link GroupMove}
 * is the reference's; a game gives its own with {@link RtsSimulation#setGroupLayout}. Asked on the simulation thread as
 * the order is applied, the same on every machine, so it decides by nothing but the world.
 */
@FunctionalInterface
public interface GroupLayout {

    /**
     * Send {@code units} — the order's own, each with a locomotor — to {@code point}; {@code click} says whether the
     * order is the player's own click rather than the game's code.
     */
    void send(RtsSimulation world, List<GameObject> units, Coord3D point, boolean click);
}
