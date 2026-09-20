package uz.dukeengine.dungeon.skill;

import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.ai.SightLine;

/**
 * Who a mending is for: whichever of the healer's own side is worst hurt, within its
 * reach and in plain sight of it.
 *
 * <p>Worst hurt as a share of his own health rather than in points, so a boss down a
 * hundred is not preferred over a skeleton at a third of itself. Only someone below
 * the skill's {@code HealBelowPercent} counts at all. The healer is never its own
 * patient: the one to kill first stays the one to kill first.
 *
 * <p>The brain choosing and the book casting both ask {@link #canMend}, so what one
 * picks the other accepts. A tie goes to the smaller object id, which makes the
 * choice the same on every machine.
 */
public final class Mending {

    private Mending() {
    }

    /** The one it should mend now, or {@code null} if nobody in reach needs it. */
    public static GameObject worstHurt(World world, GameObject healer, float reach,
            int belowPercent) {
        GameObject worst = null;
        for (var patient : world.objectsInRange(healer.getPosition(), reach,
                candidate -> canMend(healer, candidate, reach, belowPercent))) {
            if (worst == null || worse(patient, worst)) {
                worst = patient;
            }
        }
        return worst;
    }

    /** Whether {@code patient} is someone this healer may mend now. */
    public static boolean canMend(GameObject healer, GameObject patient, float reach,
            int belowPercent) {
        if (patient == null || patient == healer || patient.getBody() == null
                || patient.isEffectivelyDead()
                || patient.getPlayerIndex() != healer.getPlayerIndex()) {
            return false;
        }
        var body = patient.getBody();
        return body.getHealth() * 100f < body.getMaxHealth() * belowPercent
                && healer.getPosition().distance(patient.getPosition()) <= reach
                && SightLine.clear(healer, patient);
    }

    /** Worse hurt as a share of himself; the smaller id when they are the same. */
    private static boolean worse(GameObject a, GameObject b) {
        float aLeft = a.getBody().getHealth() * b.getBody().getMaxHealth();
        float bLeft = b.getBody().getHealth() * a.getBody().getMaxHealth();
        return aLeft < bLeft || aLeft == bLeft && a.getId().value() < b.getId().value();
    }
}
