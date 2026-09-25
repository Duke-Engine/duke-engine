package uz.dukeengine.rts.module;

/**
 * How a pile or a depot takes its harvesters, where it names one — the reference's {@code DockUpdate}: one at a time.
 * The others that reach it wait by it, each at a place of its own, and each frame nobody is in, the lowest place whose
 * harvester has come to it is let in; the one let in counts its {@code FramesBeforeActs} from then, and is out — the
 * next let in — the frame after its last act, its {@code FramesAfterActs} stood outside. At most {@code waitingPlaces}
 * wait by it, 0 for any number: a harvester that finds no place looks for another as though that one were empty, and
 * one kept waiting 900 frames, the reference's 30 seconds, gives up and looks again. With none named, any number act
 * at once, as always.
 */
public record Dock(int waitingPlaces) {
    public Dock {
        waitingPlaces = Math.max(0, waitingPlaces);
    }
}
