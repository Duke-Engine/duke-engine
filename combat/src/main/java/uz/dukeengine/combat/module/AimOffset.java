package uz.dukeengine.combat.module;

import uz.dukeengine.core.math.Coord3D;

/**
 * A module of a target that throws its attackers' aim off — the reference's {@code getSneakyTargetingOffset}: an
 * Aurora bomber on its attack run is aimed at 20 units off for two seconds, so a direct-fire gun misses it and only a
 * blast wide enough still hurts it. A shot at a thing that has one lands at that point, not on the thing
 * ({@link WeaponUpdate#aimPoint}).
 */
public interface AimOffset {

    /** How far from the thing a shot at it is aimed now, on the ground and up; null for straight at it. */
    Coord3D aimOffset();
}
