package uz.dukeengine.client3d;

import java.util.HashMap;
import java.util.Map;

/** How a blow, a level and an arrival are felt, held for {@link Visuals}: numbers, flashes, shake, moments, casts. */
final class FeelLooks {

    HitNumbers hitNumbers = HitNumbers.DEFAULTS;
    FloatingTexts.Look floatingText = FloatingTexts.Look.REFERENCE;
    Visuals.HitFlashLook hitFlash = Visuals.HitFlashLook.NONE;
    Flinch flinch = Flinch.DEFAULTS;
    HitStop hitStop;
    float shakeScale = 1f;
    float strikeWithin = 30f;
    final Map<String, Visuals.MomentLook> moments = new HashMap<>();
    final Map<String, Visuals.CastAnim> castAnims = new HashMap<>();

    /** The look of a moment, or of the nearest name above it: {@code ordered.supply} falls back to {@code ordered}. */
    Visuals.MomentLook moment(String name) {
        for (var key = name; key != null; key = key.lastIndexOf('.') < 0 ? null
                : key.substring(0, key.lastIndexOf('.'))) {
            var look = moments.get(key);
            if (look != null) {
                return look;
            }
        }
        return null;
    }
}
