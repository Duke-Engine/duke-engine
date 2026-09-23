package uz.dukeengine.core.module;

import uz.dukeengine.core.thing.ObjectId;

/**
 * How a death came: the death the killing blow dealt, and whose blow it was — the two of SAGE's {@code
 * DamageInfo} its die modules read. Carried by a blow ({@link BodyModule#damage(float, DamageType, Death)}),
 * kept by the body it kills, and handed to its {@link DieModule}s.
 *
 * @param killer what dealt it, or {@code null} for a death by no one — a script, a fall, a timer running out
 */
public record Death(DeathType type, ObjectId killer) {

    /** A plain death that nobody dealt: what a thing that died without being told how died of. */
    public static final Death NORMAL = new Death(DeathType.NORMAL, null);

    public Death {
        type = type == null ? DeathType.NORMAL : type;
    }
}
