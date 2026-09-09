package uz.duke.generals;

import java.util.List;
import uz.duke.rts.module.ExperienceModule;

/**
 * Veterancy the way Command &amp; Conquer: Generals does it — four ranks, each
 * worth a tenth more damage than the last, and a full heal on promotion.
 *
 * <p>This used to be the engine's only answer, written into an enum in the RTS
 * layer, which made every game built on it a game with four ranks whether it
 * wanted them or not. It is not a mechanism, it is a decision — so it lives here,
 * with the game that made it.
 *
 * <p>What is left in {@code rts} is the ladder: thresholds and what each is worth.
 * These constants are one table for that mechanism. A game wanting Warcraft's ten
 * hero levels, BFME's three, or none at all writes its own table and never touches
 * this class — which is the whole point, and what {@code GeneralsVeterancyTest}
 * exists to demonstrate.
 */
public final class GeneralsVeterancy {

    /** The rank names, in order. Index 0 is unranked. */
    public static final List<String> RANK_NAMES =
            List.of("REGULAR", "VETERAN", "ELITE", "HEROIC");

    /** Damage multipliers for VETERAN, ELITE and HEROIC. */
    public static final float VETERAN_DAMAGE = 1.1f;
    public static final float ELITE_DAMAGE = 1.2f;
    public static final float HEROIC_DAMAGE = 1.3f;

    private GeneralsVeterancy() {
    }

    /**
     * The Generals ladder for a unit: worth {@code experienceValue} to its killer,
     * and promoted at the three given thresholds.
     */
    public static ExperienceModule.Data ladder(int experienceValue,
            int veteranXp, int eliteXp, int heroicXp) {
        return new ExperienceModule.Data(experienceValue, List.of(
                new ExperienceModule.Rank(veteranXp, VETERAN_DAMAGE),
                new ExperienceModule.Rank(eliteXp, ELITE_DAMAGE),
                new ExperienceModule.Rank(heroicXp, HEROIC_DAMAGE)),
                true); // promotion fully heals, as in Generals
    }

    /** What to call a rung of this ladder — {@code REGULAR} through {@code HEROIC}. */
    public static String rankName(int level) {
        return level >= 0 && level < RANK_NAMES.size() ? RANK_NAMES.get(level) : "REGULAR";
    }

    /** The same ladder as INI, for a unit definition written as data. */
    public static String ini(int experienceValue, int veteranXp, int eliteXp, int heroicXp) {
        return """
                Behavior = ExperienceModule Tag
                  ExperienceValue = %d
                  ExperienceRequired = %d %d %d
                  LevelDamageBonus = %s %s %s
                  HealOnPromotion = Yes
                End
                """.formatted(experienceValue, veteranXp, eliteXp, heroicXp,
                VETERAN_DAMAGE, ELITE_DAMAGE, HEROIC_DAMAGE);
    }
}
