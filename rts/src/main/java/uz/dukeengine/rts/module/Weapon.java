package uz.dukeengine.rts.module;

import java.util.List;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.DeathType;

/**
 * A weapon, as a block of its own: what it deals, how far, how often and at what. Named, so the
 * {@link WeaponSlot}s of every unit that carries it link it by that name rather than each keeping a copy — in
 * the RTS this was measured in, 363 weapons are shared between 556 armed objects. A game hands its weapons to
 * the world with {@link uz.dukeengine.rts.RtsSimulation#addWeapons}.
 *
 * <p>The fields are {@link WeaponUpdate.Data}'s, which is a one-weapon unit's weapon written in place; see
 * there for what each means. Left out, a field means what it means there.
 *
 * @param name what slots link it by, and what its shot sounds as ({@code fired.<name>})
 */
public record Weapon(String name, float damage, float attackRange, int reloadFrames, int reloadFramesMax,
        DamageType damageType, float splashRadius, boolean attackOnTheMove, List<String> targets, int clipSize,
        int clipReloadFrames, boolean autoReload, DeathType deathType, List<WeaponBonus> bonuses,
        List<Affects> affects, float secondaryDamage, float secondaryRadius) {

    /** What a block leaves out: plain damage, no splash, a shot taken on the move, at anything, no clip. */
    static final Weapon DEFAULTS = new Weapon(null, 0f, 0f, 0, 0, DamageType.NORMAL, 0f, true, List.of(), 0, 0, true,
            DeathType.NORMAL, List.of(), List.of(), 0f, 0f);

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
     */
    public Weapon {
        damageType = damageType == null ? DamageType.NORMAL : damageType;
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
