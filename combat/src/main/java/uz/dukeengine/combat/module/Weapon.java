package uz.dukeengine.combat.module;

import java.util.List;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.thing.GameObject;

/**
 * A weapon, as a block of its own: what it deals, how far, how often and at what. Named, so the
 * {@link WeaponSlot}s of every unit that carries it link it by that name rather than each keeping a copy — in
 * the RTS this was measured in, 363 weapons are shared between 556 armed objects. A game hands its weapons to
 * the world with {@link uz.dukeengine.combat.Armoury#addWeapons}.
 *
 * <p>The fields are {@link WeaponUpdate.Data}'s, which is a one-weapon unit's weapon written in place; see
 * there for what each means. Left out, a field means what it means there.
 *
 * @param name what slots link it by, and what its shot sounds as ({@code fired.<name>})
 */
public record Weapon(String name, float damage, float attackRange, int reloadFrames, int reloadFramesMax,
        DamageType damageType, float splashRadius, boolean attackOnTheMove, List<String> targets, int clipSize,
        int clipReloadFrames, boolean autoReload, DeathType deathType, List<WeaponBonus> bonuses,
        List<Affects> affects, float secondaryDamage, float secondaryRadius, boolean shownWhenHidden,
        float minTargetPitch, float maxTargetPitch, float minimumAttackRange, int preAttackFrames,
        PreAttack preAttackType, boolean leechRange) {

    /** A weapon that fires the frame it may and keeps no reach, as every one did before one could wind up. */
    public Weapon(String name, float damage, float attackRange, int reloadFrames, int reloadFramesMax,
            DamageType damageType, float splashRadius, boolean attackOnTheMove, List<String> targets, int clipSize,
            int clipReloadFrames, boolean autoReload, DeathType deathType, List<WeaponBonus> bonuses,
            List<Affects> affects, float secondaryDamage, float secondaryRadius, boolean shownWhenHidden,
            float minTargetPitch, float maxTargetPitch, float minimumAttackRange) {
        this(name, damage, attackRange, reloadFrames, reloadFramesMax, damageType, splashRadius, attackOnTheMove,
                targets, clipSize, clipReloadFrames, autoReload, deathType, bonuses, affects, secondaryDamage,
                secondaryRadius, shownWhenHidden, minTargetPitch, maxTargetPitch, minimumAttackRange, 0,
                PreAttack.PER_SHOT, false);
    }

    /** When a weapon's wind-up comes — the reference's {@code PreAttackType}. */
    public enum PreAttack {
        /** Before every shot: a SCUD launcher's 500 ms. */
        PER_SHOT,
        /** Before the first shot at a target other than the one it last fired at: a knife's 833 ms. */
        PER_ATTACK,
        /** Before the first shot of a full clip — every shot, with no clip: the Scud Storm's 3000 ms. */
        PER_CLIP
    }

    /** A weapon with no least range, as every one was before one could name it. */
    public Weapon(String name, float damage, float attackRange, int reloadFrames, int reloadFramesMax,
            DamageType damageType, float splashRadius, boolean attackOnTheMove, List<String> targets, int clipSize,
            int clipReloadFrames, boolean autoReload, DeathType deathType, List<WeaponBonus> bonuses,
            List<Affects> affects, float secondaryDamage, float secondaryRadius, boolean shownWhenHidden,
            float minTargetPitch, float maxTargetPitch) {
        this(name, damage, attackRange, reloadFrames, reloadFramesMax, damageType, splashRadius, attackOnTheMove,
                targets, clipSize, clipReloadFrames, autoReload, deathType, bonuses, affects, secondaryDamage,
                secondaryRadius, shownWhenHidden, minTargetPitch, maxTargetPitch, 0f, 0, PreAttack.PER_SHOT, false);
    }

    /** The least dz, up or down, at which a pitch range is weighed at all: the reference's {@code ACCCEPTABLE_DZ}. */
    private static final float ACCEPTABLE_DZ = 10f;

    /** A weapon that fires at any pitch, as every one did before one could name a range. */
    public Weapon(String name, float damage, float attackRange, int reloadFrames, int reloadFramesMax,
            DamageType damageType, float splashRadius, boolean attackOnTheMove, List<String> targets, int clipSize,
            int clipReloadFrames, boolean autoReload, DeathType deathType, List<WeaponBonus> bonuses,
            List<Affects> affects, float secondaryDamage, float secondaryRadius, boolean shownWhenHidden) {
        this(name, damage, attackRange, reloadFrames, reloadFramesMax, damageType, splashRadius, attackOnTheMove,
                targets, clipSize, clipReloadFrames, autoReload, deathType, bonuses, affects, secondaryDamage,
                secondaryRadius, shownWhenHidden, -180f, 180f, 0f);
    }

    /** A weapon whose shots a hidden shooter keeps from those it is hidden from, as every one did before. */
    public Weapon(String name, float damage, float attackRange, int reloadFrames, int reloadFramesMax,
            DamageType damageType, float splashRadius, boolean attackOnTheMove, List<String> targets, int clipSize,
            int clipReloadFrames, boolean autoReload, DeathType deathType, List<WeaponBonus> bonuses,
            List<Affects> affects, float secondaryDamage, float secondaryRadius) {
        this(name, damage, attackRange, reloadFrames, reloadFramesMax, damageType, splashRadius, attackOnTheMove,
                targets, clipSize, clipReloadFrames, autoReload, deathType, bonuses, affects, secondaryDamage,
                secondaryRadius, false);
    }

    /**
     * Whether it is a contact weapon: its range less a quarter of a cell under a cell — the reference's {@code
     * WeaponTemplate::isContactWeapon}, whose range it undersizes so the goal is not teetering on the edge of firing
     * range. A unit closing with one runs into its target itself rather than to a spot a cell off.
     */
    public boolean isContact(float cell) {
        return attackRange - cell / 4f < cell;
    }

    /** What a block leaves out: plain damage, no splash, a shot taken on the move, at anything, no clip. */
    static final Weapon DEFAULTS = new Weapon(null, 0f, 0f, 0, 0, DamageType.NORMAL, 0f, true, List.of(), 0, 0, true,
            DeathType.NORMAL, List.of(), List.of(), 0f, 0f, false, -180f, 180f, 0f, 0, PreAttack.PER_SHOT, false);

    /**
     * How near it fires, as the reference measures it: its least range less a quarter of a cell, never below
     * nothing ({@code WeaponTemplate::getMinimumAttackRange}, which undersizes it so a shooter is not teetering on the
     * edge of it). 0 for a weapon with none.
     */
    public float leastRange(float cell) {
        return minimumAttackRange <= 0f ? 0f : Math.max(0f, minimumAttackRange - cell / 4f);
    }

    /**
     * Whether {@code victim} lies within the pitch it fires within, seen from {@code shooter}'s middle — the
     * reference's {@code Weapon::isWithinTargetPitch} and {@code GeometryInfo::calcPitches}: a contact weapon, one
     * that names no range, or a victim standing less than 10 above or below the shooter always does; else the pitches
     * from the shooter's middle to the victim's bottom and to its top must overlap the range.
     */
    public boolean withinPitch(GameObject shooter, GameObject victim, float cell) {
        if (minTargetPitch <= -180f && maxTargetPitch >= 180f || isContact(cell)) {
            return true;
        }
        var from = shooter.getPosition();
        var to = victim.getPosition();
        if (Math.abs(to.z() - from.z()) < ACCEPTABLE_DZ) {
            return true;
        }
        float middle = from.z() + shooter.getGeometry().sphereCentreHeight();
        float dx = to.x() - from.x();
        float dy = to.y() - from.y();
        double across = Math.sqrt(dx * dx + dy * dy);
        var shape = victim.getGeometry();
        float bottom = to.z() + shape.sphereCentreHeight() - shape.height() / 2f;
        double least = Math.toDegrees(StrictMath.atan2(bottom - middle, across));
        double most = Math.toDegrees(StrictMath.atan2(bottom + shape.height() - middle, across));
        return least <= maxTargetPitch && most >= minTargetPitch;
    }

    /**
     * Whom a weapon's blast hurts — the reference's {@code RadiusDamageAffects}. A blast names every one it hurts;
     * naming none is its enemies alone, as every blast was before a weapon could say.
     */
    public enum Affects {
        /** The firer's side and its allies. */
        ALLIES,
        /** Its enemies. */
        ENEMIES,
        /** Sides it is neither allied nor at war with. */
        NEUTRALS,
        /** The firer itself. */
        SELF,
        /** Not things of the firer's own kind, whatever their side. */
        NOT_SIMILAR,
        /** Not things in the air. */
        NOT_AIRBORNE
    }

    /**
     * @param bonuses         lines of its own — {@code WeaponBonus = PLAYER_UPGRADE DAMAGE 125%} inside a reference
     *                        weapon — added to the game's table for this weapon alone: a Technical's machine gun gets
     *                        the AP bullets its rocket launcher does not
     * @param affects         whom its blast hurts ({@link Affects}); none named is its enemies alone
     * @param secondaryDamage what its blast deals beyond {@code splashRadius} and within {@code secondaryRadius} —
     *                        the reference's second ring: a cluster mine's 50 within 3 and 100 within 5
     * @param secondaryRadius how far that second ring reaches; 0 for none
     * @param shownWhenHidden whether its shots are shown to whoever sees where they are fired though its shooter is
     *                        hidden — the reference's {@code PlayFXWhenStealthed}, a demo trap's detonation
     * @param minTargetPitch  the least pitch, in degrees, it fires at — the reference's {@code MinTargetPitch}: a
     *                        tank's gun -15. A victim none of whose height lies between this and {@code
     *                        maxTargetPitch}, seen from the shooter's middle, is none of its targets ({@link
     *                        #withinPitch}). -180, the default, is no least
     * @param maxTargetPitch  the most, in degrees: a tank's gun 15, a thrown bottle 57. 180, the default, is no most
     * @param minimumAttackRange how near, measured as its reach is, it may not fire — the reference's {@code
     *                        MinimumAttackRange}: a SCUD launcher's 200 of its 350. A unit that is nearer moves off
     *                        until it may fire; one that cannot move lets such a target go ({@link WeaponUpdate}).
     *                        0, the default, is none
     * @param preAttackFrames  how long it winds up before a shot, in frames — the reference's {@code PreAttackDelay}:
     *                        once it may fire, the shot comes that many frames later ({@link WeaponStatus#PRE_ATTACK}
     *                        meanwhile). 0, the default, is none
     * @param preAttackType    when it winds up ({@link PreAttack}); before every shot by default
     * @param leechRange       whether it keeps its reach for the rest of an attack once it has wound up or fired — the
     *                        reference's {@code LeechRangeWeapon}: a knife whose victim walks off is not missed
     */
    public Weapon {
        damageType = damageType == null ? DamageType.NORMAL : damageType;
        preAttackType = preAttackType == null ? PreAttack.PER_SHOT : preAttackType;
        affects = affects == null ? List.of() : List.copyOf(affects);
        deathType = deathType == null ? DeathType.NORMAL : deathType;
        bonuses = bonuses == null ? List.of() : bonuses.stream()
                .sorted(java.util.Comparator.comparing(WeaponBonus::word).thenComparing(WeaponBonus::kind))
                .toList();
    }

    /** A blast that hurts its enemies over one ring: every weapon from before a blast could say whom it hurts. */
    public Weapon(String name, float damage, float attackRange, int reloadFrames, int reloadFramesMax,
            DamageType damageType, float splashRadius, boolean attackOnTheMove, List<String> targets, int clipSize,
            int clipReloadFrames, boolean autoReload, DeathType deathType, List<WeaponBonus> bonuses) {
        this(name, damage, attackRange, reloadFrames, reloadFramesMax, damageType, splashRadius, attackOnTheMove,
                targets, clipSize, clipReloadFrames, autoReload, deathType, bonuses, List.of(), 0f, 0f);
    }

    /** A weapon with no bonus lines of its own: every weapon from before a weapon could carry them. */
    public Weapon(String name, float damage, float attackRange, int reloadFrames, int reloadFramesMax,
            DamageType damageType, float splashRadius, boolean attackOnTheMove, List<String> targets, int clipSize,
            int clipReloadFrames, boolean autoReload, DeathType deathType) {
        this(name, damage, attackRange, reloadFrames, reloadFramesMax, damageType, splashRadius, attackOnTheMove,
                targets, clipSize, clipReloadFrames, autoReload, deathType, List.of());
    }

    /** A weapon whose kills are plain deaths: every weapon from before a weapon said what death it deals. */
    public Weapon(String name, float damage, float attackRange, int reloadFrames, int reloadFramesMax,
            DamageType damageType, float splashRadius, boolean attackOnTheMove, List<String> targets, int clipSize,
            int clipReloadFrames, boolean autoReload) {
        this(name, damage, attackRange, reloadFrames, reloadFramesMax, damageType, splashRadius, attackOnTheMove,
                targets, clipSize, clipReloadFrames, autoReload, DeathType.NORMAL);
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    /** The one weapon a {@link WeaponUpdate} block writes in place, named as that block names it. */
    static Weapon of(WeaponUpdate.Data data) {
        return new Weapon(data.name(), data.damage(), data.attackRange(), data.reloadFrames(), data.reloadFramesMax(),
                data.damageType(), data.splashRadius(), data.attackOnTheMove(), data.targets(), data.clipSize(),
                data.clipReloadFrames(), data.autoReload(), data.deathType());
    }
}
