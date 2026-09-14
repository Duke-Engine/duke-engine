package uz.duke.dungeon.level;

import uz.duke.core.module.MoveUpdate;
import uz.duke.rts.module.WeaponUpdate;

/**
 * A hero built at his first level: his creature block with his attributes already in it.
 *
 * <p>Applied while the engine builds his body, legs and weapon rather than a frame later,
 * so a hero is the same hero wherever he is spawned — a run, a stage, or a test that never
 * started either. Everything past the first level is {@link HeroProgress}'s.
 *
 * <p>A creature whose block names no attributes is handed back exactly as the file wrote
 * it, the same instance, so no monster's numbers pass through any arithmetic at all.
 */
public final class HeroBuild {

    private HeroBuild() {
    }

    public static GrowableBody.Data body(GrowableBody.Data data, HeroAttributes hero,
            AttributeRules rules) {
        if (data == null || hero.primary() == null) {
            return data;
        }
        return new GrowableBody.Data(
                firstLevel(new HeroBase(data.maxHealth(), 0f, 0f, 0f), hero, rules).maxHealth());
    }

    public static MoveUpdate.Data legs(MoveUpdate.Data data, HeroAttributes hero,
            AttributeRules rules) {
        if (data == null || hero.primary() == null) {
            return data;
        }
        var built = firstLevel(new HeroBase(0f, data.speedPerSecond(),
                data.turnRateDegreesPerSecond(), 0f), hero, rules);
        return new MoveUpdate.Data(built.speed(), data.turnRateDegreesPerSecond());
    }

    public static WeaponUpdate.Data weapon(WeaponUpdate.Data data, HeroAttributes hero,
            AttributeRules rules) {
        if (data == null || hero.primary() == null) {
            return data;
        }
        var built = firstLevel(new HeroBase(0f, 0f, 0f, data.damage()), hero, rules);
        return new WeaponUpdate.Data(built.attack(), data.attackRange(), data.reloadFrames(),
                data.damageType(), data.splashRadius(), data.attackOnTheMove());
    }

    /**
     * The same call {@code HeroProgress} makes to learn what the body was built as, so
     * the two agree to the bit rather than to a tolerance.
     */
    static HeroFigures firstLevel(HeroBase base, HeroAttributes hero, AttributeRules rules) {
        return HeroFigures.of(base, 0, hero, rules, Levelling.FIRST_LEVEL,
                HeroFigures.Found.NOTHING);
    }
}
