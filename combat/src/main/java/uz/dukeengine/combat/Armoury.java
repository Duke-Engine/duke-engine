package uz.dukeengine.combat;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.combat.module.GuardRules;
import uz.dukeengine.combat.module.TargetRule;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponBonus;

/**
 * A world's arms: the weapons its slots link by name, what each thing is to a weapon, the weapon bonus table, how
 * often a weapon with no target looks, whose shots are shown though their shooter is hidden, and how a guard guards.
 * One object that any simulation whose things fight holds ({@link ArmedWorld}) and every weapon of it reads — what an RTS's
 * simulation held for itself, so a game of one hero has the same arms as a game of armies.
 *
 * <p>Data, like the templates: a new game in the same world keeps it. Set before the first frame, or from the
 * simulation thread, as everything the simulation reads is.
 */
public final class Armoury {

    private final Map<String, Weapon> weapons = new LinkedHashMap<>();
    private List<TargetRule> targetRules = List.of();
    private List<WeaponBonus> weaponBonuses = List.of();
    private int targetScanFrames = 1;
    private int idleTargetScanFrames;
    private List<Kind> shownWhenHidden = List.of();
    private boolean hiddenShotsToOwnerOnly;
    private GuardRules guardRules = GuardRules.DEFAULT;

    /** The armoury of {@code world}, or an empty one of its own for a world that keeps none. */
    public static Armoury of(World world) {
        return world instanceof ArmedWorld armed ? armed.armoury() : new Armoury();
    }

    /**
     * The weapons this game's {@link uz.dukeengine.combat.module.WeaponSlot}s link by name — one block a weapon,
     * however many units carry it. A later weapon of a name replaces an earlier.
     */
    public void addWeapons(Collection<Weapon> more) {
        for (var weapon : more) {
            if (weapon != null && weapon.name() != null) {
                weapons.put(weapon.name(), weapon);
            }
        }
    }

    /** The weapon of this name, or {@code null} where the game gave none. */
    public Weapon findWeapon(String name) {
        return name == null ? null : weapons.get(name);
    }

    /**
     * What each thing is, to a weapon: the game's lines, in order, the first that matches a thing deciding its
     * classes — see {@link TargetRule}. None, the default, gives no thing a class, which matters only to a weapon that
     * names classes: a weapon that names none fires at anything.
     */
    public void setTargetRules(List<TargetRule> rules) {
        this.targetRules = rules == null ? List.of() : List.copyOf(rules);
    }

    public List<TargetRule> getTargetRules() {
        return targetRules;
    }

    /**
     * The game's weapon bonus table: every line whose word a thing holds multiplies its weapons' damage, range, rate
     * of fire or blast — see {@link WeaponBonus}. Kept in the order of their words, sorted, so the sum is the same on
     * every machine.
     */
    public void setWeaponBonuses(Collection<WeaponBonus> lines) {
        this.weaponBonuses = lines.stream()
                .sorted(Comparator.comparing(WeaponBonus::word).thenComparing(WeaponBonus::kind))
                .toList();
    }

    /** What the table makes {@code kind} for {@code thing}, as its words stand now — see the overload below. */
    public float weaponBonus(GameObject thing, WeaponBonus.Kind kind) {
        return weaponBonus(thing, kind, List.of());
    }

    /**
     * What the table and a weapon's own lines make {@code kind} for {@code thing}, as its words stand now: 1, and for
     * every line whose word it holds, what that line adds over 1 — {@code 1 + Σ(multiplier − 1)}, as the reference's
     * {@code WeaponBonus::appendBonuses} sums the game's set and then the weapon's extra one. Added in a fixed order,
     * the table's lines by their words and then the weapon's, so it comes to the same bits on every machine.
     */
    public float weaponBonus(GameObject thing, WeaponBonus.Kind kind, List<WeaponBonus> own) {
        float sum = 1f;
        for (var line : weaponBonuses) {
            if (line.kind() == kind && thing.hasCondition(line.word())) {
                sum += line.multiplier() - 1f;
            }
        }
        for (var line : own) {
            if (line.kind() == kind && thing.hasCondition(line.word())) {
                sum += line.multiplier() - 1f;
            }
        }
        return sum;
    }

    /**
     * How often a weapon with no target looks for one, in frames, where its template does not say ({@code
     * WeaponUpdate.Data.targetScanFrames}) — the reference's {@code MoodAttackCheckRate}, 2 seconds where a template
     * leaves it out. Each thing on its own clock: a look moves its next on by the rate, its first by up to half the
     * rate more, drawn from the world's random numbers, so a crowd does not look all at once; and a thing that falls
     * idle looks soon after ({@link #setIdleTargetScanFrames}). 1, the default, is every frame.
     */
    public void setTargetScanFrames(int frames) {
        this.targetScanFrames = Math.max(1, frames);
    }

    public int getTargetScanFrames() {
        return targetScanFrames;
    }

    /**
     * How soon a thing that falls idle looks for a target, in frames — its order done or stopped, or what it fought
     * gone: its next look set to then, whatever its clock said — the reference's {@code resetNextMoodCheckTime}, now
     * and its {@code AIData}'s {@code ForceIdleFramesCount}. 0, the default, at once.
     */
    public void setIdleTargetScanFrames(int frames) {
        this.idleTargetScanFrames = Math.max(0, frames);
    }

    public int getIdleTargetScanFrames() {
        return idleTargetScanFrames;
    }

    /**
     * The kinds of thing whose shots are shown though the thing is hidden, filtered by where they are fired alone as
     * any other's — the reference's mines ({@code KINDOF_MINE}), which always show their blast. A weapon may say so of
     * itself ({@link Weapon#shownWhenHidden}). None, the default.
     */
    public void setShownWhenHidden(List<Kind> kinds) {
        this.shownWhenHidden = kinds == null ? List.of() : List.copyOf(kinds);
    }

    public List<Kind> getShownWhenHidden() {
        return shownWhenHidden;
    }

    /**
     * Whether a hidden thing's shots are kept from its allies too, shown to its own player alone — the reference's
     * {@code Weapon::fireWeaponTemplate}, a stealthed shooter's firing seen by the player who controls it and nobody
     * else. Off, the default: shown to whoever it is not hidden from.
     */
    public void setHiddenShotsToOwnerOnly(boolean ownerOnly) {
        this.hiddenShotsToOwnerOnly = ownerOnly;
    }

    public boolean isHiddenShotsToOwnerOnly() {
        return hiddenShotsToOwnerOnly;
    }

    /** How a guard guards and an attack-move chases — see {@link GuardRules}. */
    public void setGuardRules(GuardRules rules) {
        this.guardRules = rules == null ? GuardRules.DEFAULT : rules;
    }

    public GuardRules getGuardRules() {
        return guardRules;
    }
}
