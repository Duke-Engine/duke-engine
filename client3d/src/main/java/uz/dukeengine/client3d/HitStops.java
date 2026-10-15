package uz.dukeengine.client3d;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import uz.dukeengine.core.event.ObjectHurt;

/** The creatures a {@link HitStop} has caught, and until when: their animation slowed, then given back. */
final class HitStops {

    private final Map<Integer, Float> until = new LinkedHashMap<>();
    private final Map<Integer, Float> slowOf = new LinkedHashMap<>();

    /** A blow heard at {@code now}: its victim and its striker caught, where the look says it catches. */
    void heard(ObjectHurt hurt, HitStop look, float bigAt, float now) {
        if (look == null || !look.catches(hurt.amount(), hurt.critical(), bigAt)) {
            return;
        }
        catchOne(hurt.object() == null ? -1 : hurt.object().value(), look, now);
        catchOne(hurt.attacker() == null ? -1 : hurt.attacker().value(), look, now);
    }

    private void catchOne(int id, HitStop look, float now) {
        if (id < 0 || until.containsKey(id)) {
            return; // caught already: a second blow within the first's time does not lengthen it
        }
        until.put(id, now + look.seconds());
        slowOf.put(id, look.slow());
    }

    /** Each caught creature's speed set — its slow while caught, 1 once let go, and then forgotten. */
    void apply(float now, BiConsumer<Integer, Float> speed) {
        for (var it = until.entrySet().iterator(); it.hasNext(); ) {
            var caught = it.next();
            if (now >= caught.getValue()) {
                speed.accept(caught.getKey(), 1f);
                slowOf.remove(caught.getKey());
                it.remove();
            } else {
                speed.accept(caught.getKey(), slowOf.get(caught.getKey()));
            }
        }
    }

    void clear() {
        until.clear();
        slowOf.clear();
    }
}
