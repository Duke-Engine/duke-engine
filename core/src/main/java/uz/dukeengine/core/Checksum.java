package uz.dukeengine.core;

import java.util.function.LongUnaryOperator;
import uz.dukeengine.core.thing.GameObject;

/**
 * A world's checksum as it is summed, ported in spirit from SAGE's {@code VERIFY_CRC}: each value mixed into the
 * running hash in turn, so the same values in the same order sum alike on every machine.
 */
final class Checksum {

    private long hash = 1125899906842597L; // a large prime seed

    void mix(int value) {
        hash = hash * 31 + value;
    }

    /** Both halves of a long, the low first. */
    void mixLong(long value) {
        mix((int) value);
        mix((int) (value >>> 32));
    }

    /** Whatever the value's own sum does with the running hash — a player's, say. */
    void mixWith(LongUnaryOperator sum) {
        hash = sum.applyAsLong(hash);
    }

    /**
     * Mix {@code object}'s identity, ownership, transform and health, using {@link Float#floatToIntBits} so float state
     * hashes identically everywhere — and what it carries beyond a plain thing's, which leaves the checksum of a world
     * with none, and every recording of one, as it was.
     */
    void mixThing(GameObject object) {
        mix(object.getId().value());
        mix(object.getPlayerIndex());
        var p = object.getPosition();
        mix(Float.floatToIntBits(p.x()));
        mix(Float.floatToIntBits(p.y()));
        mix(Float.floatToIntBits(p.z()));
        mix(Float.floatToIntBits(object.getOrientation()));
        mix(object.getBody() == null ? -1 : Float.floatToIntBits(object.getBody().getHealth()));
        if (object.getBody() != null && object.getBody().getDamageScale() != 1f) {
            mix(Float.floatToIntBits(object.getBody().getDamageScale())); // 1 sums as it always did
        }
        if (object.getOwnVisionRange() >= 0f) {
            mix(Float.floatToIntBits(object.getOwnVisionRange()));
        }
        if (object.getOwnFogRange() >= 0f) {
            mix(Float.floatToIntBits(object.getOwnFogRange())); // unset sums as it always did
        }
        if (object.getTargetableFrom() > 0) {
            mix(object.getTargetableFrom());
        }
        if (object.getProducer() != null) {
            mix(object.getProducer().value()); // made by nothing sums as it always did
        }
        if (object.getFloor() != 0) {
            mix(object.getFloor());
        }
        for (var override : object.getRelationOverrides().entrySet()) {
            mix(override.getKey()); // none sums as it always did
            mix(override.getValue().ordinal());
        }
        int statuses = object.statusBits();
        if (statuses != 0) {
            mix(statuses);
        }
    }

    long value() {
        return hash;
    }
}
