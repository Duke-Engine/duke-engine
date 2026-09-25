package uz.dukeengine.core.thing;

/** A thing that sees: what the fog and each side's sight read. */
public interface Sighted extends ThingTemplate {

    /** How far it sees, in world units: what it looks for targets by. */
    float visionRange();

    /**
     * How far it clears the fog for its side, apart from how far it looks for targets — the reference's {@code
     * ShroudClearingRange}, a Ranger looking 100 and clearing 400; negative for its sight, 0 for none.
     */
    default float fogRange() {
        return -1f;
    }

    /**
     * Within how far every player sees round it once it is finished — the reference's {@code ShroudRevealToAllRange},
     * a superweapon everyone knows the place of; 0 for none.
     */
    default float seenByAllWithin() {
        return 0f;
    }

    /** How far any template sees: its own range if it has eyes, nothing if not. */
    static float of(ThingTemplate template) {
        return template instanceof Sighted sighted ? sighted.visionRange() : 0f;
    }

    /** How far any template clears the fog: its own fog range, negative for its sight. */
    static float fogRangeOf(ThingTemplate template) {
        return template instanceof Sighted sighted ? sighted.fogRange() : -1f;
    }

    /** Within how far any template is seen by all once finished; 0 for none. */
    static float seenByAllOf(ThingTemplate template) {
        return template instanceof Sighted sighted ? sighted.seenByAllWithin() : 0f;
    }
}
