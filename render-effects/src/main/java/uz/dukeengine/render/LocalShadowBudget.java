package uz.dukeengine.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Which of a scene's local lights have shadows this frame. There are only so many slots; each frame the lights that matter
 * most are asked for, but a light holds its slot until something clearly beats it ({@link #HOLD}), and gains or loses its
 * shadow over {@link #FADE} seconds rather than at once, so a shadow never pops when the camera moves and two lights of
 * equal worth do not trade places every frame. A light leaving keeps its slot until it has faded out: a slot is never
 * handed on while its old shadow is still visible, and no more than the slots' number of lights ever has one.
 *
 * <p>Plain logic, no graphics: {@link LocalShadowFilter} reads the result.
 */
final class LocalShadowBudget {

    /** How much worth a light with a shadow already is given over one without, when they are weighed. */
    static final float HOLD = 1.3f;
    /** Seconds from no shadow to all of it, and back. */
    static final float FADE = 0.3f;

    /** One slot: whom it belongs to and how much of their shadow is shown, 0 to 1. */
    static final class Slot {
        Object light;
        float weight;
    }

    private final Slot[] slots;
    private final float fade;

    LocalShadowBudget(int slots, float fade) {
        this.slots = new Slot[slots];
        for (int i = 0; i < slots; i++) {
            this.slots[i] = new Slot();
        }
        this.fade = fade;
    }

    Slot[] slots() {
        return slots;
    }

    /**
     * How much a light matters to a viewer at {@code distance}: its brightness, as far as it reaches, and fading with the
     * square of how far off it is, as light does. Zero for what cannot be seen lit.
     */
    static float worth(float brightness, float radius, float distance) {
        if (brightness <= 0f || radius <= 0f) {
            return 0f;
        }
        return brightness * radius * radius / (distance * distance + radius * radius);
    }

    /**
     * One frame on. {@code worth} holds each light that could have a shadow now and what it is worth ({@link #worth});
     * a light not in it, or worth nothing, is not wanted.
     */
    void update(Map<?, Float> worth, float tpf) {
        var wanted = new ArrayList<Object>();
        worth.entrySet().stream().filter(entry -> entry.getValue() > 0f)
                .sorted(Comparator.comparingDouble((Map.Entry<?, Float> entry) -> -weighed(entry.getKey(), entry.getValue())))
                .limit(slots.length).forEach(entry -> wanted.add(entry.getKey()));
        float step = tpf / fade;
        for (var slot : slots) {
            if (slot.light == null) {
                continue;
            }
            if (wanted.remove(slot.light)) {
                slot.weight = Math.min(1f, slot.weight + step);
            } else {
                slot.weight = Math.max(0f, slot.weight - step);
                if (slot.weight == 0f) {
                    slot.light = null;
                }
            }
        }
        // Those left over wait for a free slot, which a fading one gives up in time.
        for (var light : wanted) {
            for (var slot : slots) {
                if (slot.light == null) {
                    slot.light = light;
                    slot.weight = Math.min(1f, step);
                    break;
                }
            }
        }
    }

    private float weighed(Object light, float worth) {
        for (var slot : slots) {
            if (slot.light == light && slot.weight > 0f) {
                return worth * HOLD;
            }
        }
        return worth;
    }

    /** The lights that have a shadow shown now. */
    List<Object> shown() {
        var shown = new ArrayList<Object>();
        for (var slot : slots) {
            if (slot.light != null && slot.weight > 0f) {
                shown.add(slot.light);
            }
        }
        return shown;
    }
}
