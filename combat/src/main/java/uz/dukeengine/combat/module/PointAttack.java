package uz.dukeengine.combat.module;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;

/**
 * An attack toward a point rather than at a target, handed to a weapon ({@link WeaponUpdate#attackToward}) and asked
 * by it each frame until it is over or the weapon lets it go ({@link WeaponUpdate#holdFire}). The weapon does the
 * attacking — turned to the point, wound up as it winds, then loosed along the line by a launcher that carries to a
 * place, or else a blow at what {@link #struck} names, or at nothing; this says where, what a blow strikes, and whether
 * it goes on.
 */
public interface PointAttack {

    /**
     * Where to attack this frame, or null: the attack is over. {@code windingUp} while a blow toward the point is
     * winding up.
     */
    Coord3D point(boolean windingUp);

    /** The thing a blow along {@code facing}, reaching {@code reach}, strikes; null for none, a blow at nothing. */
    GameObject struck(WeaponUpdate weapon, float reach, float facing);

    /**
     * Told a blow toward the point went off.
     *
     * @return whether the attack goes on
     */
    boolean attacked();
}
