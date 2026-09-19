package uz.duke.rts;

import uz.duke.core.GameConstants;
import uz.duke.core.thing.ThingTemplate;

/**
 * A thing a factory can make: what it costs and how long it takes. An RTS's concern,
 * and so the RTS library's capability rather than the engine's — a platformer's
 * templates have no price.
 */
public interface Buildable extends ThingTemplate {

    int buildCost();

    /** Seconds it takes to produce one, as a file writes it: {@code BuildTime = 7.5}. */
    float buildTime();

    /** Logic frames it takes to produce one: seconds in the file, frames in the logic, as SAGE counts them. */
    default int buildTimeFrames() {
        return Math.round(buildTime() * GameConstants.LOGICFRAMES_PER_SECOND);
    }

    /** What any template costs: its own price if it is buildable, nothing if not. */
    static int costOf(ThingTemplate template) {
        return template instanceof Buildable buildable ? buildable.buildCost() : 0;
    }

    /** How long any template takes: its own time if it is buildable, none if not. */
    static int framesOf(ThingTemplate template) {
        return template instanceof Buildable buildable ? buildable.buildTimeFrames() : 0;
    }
}
