package uz.dukeengine.rts.construction;

import uz.dukeengine.core.thing.GameObject;

/**
 * A module told that a building is finished, the frame {@code UNDER_CONSTRUCTION} comes off it — the reference's
 * {@code AIPlayer::onStructureProduced}, which marks a line of a build list done. A module on the builder hears
 * it, and so does one on the building itself (a supply centre that hands out a free harvester when it is
 * whole), then game code that watches every site ({@link uz.dukeengine.rts.RtsSimulation#onConstructed}).
 */
public interface ConstructionListener {

    void onConstructed(GameObject builder, GameObject building);
}
