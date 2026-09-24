package uz.dukeengine.rts.event;

import uz.dukeengine.core.event.WorldEvent;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/**
 * An upgrade was finished — researched at a building, or bought at once.
 *
 * @param player     the side it is for
 * @param upgrade    its name, which is also the word it leaves on what it reaches
 * @param researcher what researched it, or {@code null} for one bought at once
 * @param sideWide   whether it is the whole side's, or {@code researcher}'s alone
 * @param where      where the researcher stands, or {@code null}
 */
public record UpgradeCompleted(int frame, int player, String upgrade, ObjectId researcher, boolean sideWide,
        Coord3D where) implements WorldEvent {

    @Override
    public Coord3D where() {
        return where;
    }
}
