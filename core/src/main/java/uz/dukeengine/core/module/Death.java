package uz.dukeengine.core.module;

import uz.dukeengine.core.thing.ObjectId;

/**
 * How a death came: the death the killing blow dealt, and whose blow it was — the two of SAGE's {@code
 * DamageInfo} its die modules read. Carried by a blow ({@link BodyModule#damage(float, DamageType, Death)}),
 * kept by the body it kills, and handed to its {@link DieModule}s.
 *
 * @param killer            what dealt it, or {@code null} for a death by no one — a script, a fall, a timer running out
 * @param killerPlayerIndex whose side dealt it, as it stood when the blow landed — a shell's shooter may be gone by
 *                          the time the shell lands — or -1 for nobody, or not yet known
 */
public record Death(DeathType type, ObjectId killer, int killerPlayerIndex) {

    /** A plain death that nobody dealt: what a thing that died without being told how died of. */
    public static final Death NORMAL = new Death(DeathType.NORMAL, null, -1);

    public Death {
        type = type == null ? DeathType.NORMAL : type;
        killerPlayerIndex = killer == null ? -1 : killerPlayerIndex;
    }

    /** A blow whose side is not said: looked up from the killer when the death is reaped, where it still stands. */
    public Death(DeathType type, ObjectId killer) {
        this(type, killer, -1);
    }
}
