package uz.dukeengine.rts.module;

import uz.dukeengine.core.thing.ThingTemplate;

/**
 * A module that says how many frames making a thing takes now — the reference's {@code ThingTemplate::calcTimeToBuild},
 * worked out every frame from its side's power: a barracks' 10 seconds are 20 while the side is short of it.
 *
 * <p>Asked every frame the work moves, in module order, of a factory for the unit at the head of its queue and of a
 * site for itself; the first that answers is taken. A site gains 1/length of its work and of the health it rises by
 * each frame its builder works it, the length of that frame; a factory's job is done once the frames it has run reach
 * the length of that frame. Research keeps its own length, as the reference's ({@code UpgradeTemplate::calcTimeToBuild}
 * leaves power out). None answering, the template's build time, as always.
 */
public interface BuildLength {

    /** How many frames making {@code thing} takes now; 0 or less for no say. */
    int framesToBuild(ThingTemplate thing);

    /** What the first module of {@code owner}'s that answers says {@code thing} takes, or {@code otherwise}. */
    static int of(uz.dukeengine.core.thing.GameObject owner, ThingTemplate thing, int otherwise) {
        for (var module : owner.getModules()) {
            if (module instanceof BuildLength length) {
                int frames = length.framesToBuild(thing);
                if (frames > 0) {
                    return frames;
                }
            }
        }
        return otherwise;
    }
}
