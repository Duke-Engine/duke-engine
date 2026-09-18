package uz.duke.core.thing;

/** A thing that sees: what the fog and each side's sight read. */
public interface Sighted extends ThingTemplate {

    /** How far it sees, in world units. */
    float visionRange();

    /** How far any template sees: its own range if it has eyes, nothing if not. */
    static float of(ThingTemplate template) {
        return template instanceof Sighted sighted ? sighted.visionRange() : 0f;
    }
}
