package uz.duke.rts;

import uz.duke.core.thing.ThingTemplate;

/**
 * A thing a factory can make: what it costs and how long it takes. An RTS's concern,
 * and so the RTS library's capability rather than the engine's — a platformer's
 * templates have no price.
 */
public interface Buildable extends ThingTemplate {

    int buildCost();

    /** Logic frames it takes to produce one. */
    int buildTimeFrames();

    /** What any template costs: its own price if it is buildable, nothing if not. */
    static int costOf(ThingTemplate template) {
        return template instanceof Buildable buildable ? buildable.buildCost() : 0;
    }

    /** How long any template takes: its own time if it is buildable, none if not. */
    static int framesOf(ThingTemplate template) {
        return template instanceof Buildable buildable ? buildable.buildTimeFrames() : 0;
    }
}
