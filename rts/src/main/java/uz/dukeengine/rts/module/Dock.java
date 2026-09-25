package uz.dukeengine.rts.module;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.Bones;
import uz.dukeengine.core.thing.GameObject;

/**
 * How a pile or a depot takes its harvesters, where it names one — the reference's {@code DockUpdate}: one at a time.
 * The others that reach it wait by it, each at a place of its own, and each frame nobody is in, the lowest place whose
 * harvester has come to it is let in; the one let in counts its {@code FramesBeforeActs} from then, and is out — the
 * next let in — the frame after its last act, its {@code FramesAfterActs} stood outside. At most {@code waitingPlaces}
 * wait by it, 0 for any number: a harvester that finds no place looks for another as though that one were empty, and
 * one kept waiting 900 frames, the reference's 30 seconds, gives up and looks again. With none named, any number act
 * at once, as always.
 *
 * <p>Where they wait and act, where it names a place: {@code waitAt}, the place its harvesters wait at, and {@code
 * actAt}, the place the one let in goes to and loads or banks at — the reference's DockWaiting and DockAction bones.
 * Each is left out for where the harvester stands beside it; one let in with no place to act at acts where it waited.
 */
public record Dock(int waitingPlaces, Place waitAt, Place actAt) {
    public Dock {
        waitingPlaces = Math.max(0, waitingPlaces);
    }

    /** Harvesters that wait and act where they stand beside it. */
    public Dock(int waitingPlaces) {
        this(waitingPlaces, null, null);
    }

    /**
     * A point of the building's own frame: the bone of its model named {@code bone}, or — with none named, or none
     * such — {@code x} ahead of its middle and {@code y} to its side, the middle itself for 0 and 0.
     */
    public record Place(String bone, float x, float y) {

        /** Where it is in the world, on {@code building} as it stands and faces now. */
        Coord3D in(GameObject building) {
            var boned = bone == null ? null : Bones.inWorld(building, bone);
            if (boned != null) {
                return boned;
            }
            double cos = StrictMath.cos(building.getOrientation());
            double sin = StrictMath.sin(building.getOrientation());
            var at = building.getPosition();
            return new Coord3D((float) (at.x() + x * cos - y * sin), (float) (at.y() + x * sin + y * cos), at.z());
        }
    }
}
