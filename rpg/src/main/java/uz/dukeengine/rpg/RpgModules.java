package uz.dukeengine.rpg;

import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.rpg.hero.AttackBonus;
import uz.dukeengine.rpg.hero.AttackSpeed;
import uz.dukeengine.rpg.hero.CriticalStrike;
import uz.dukeengine.rpg.hero.LevelBonus;
import uz.dukeengine.rpg.skill.Summoned;

/**
 * What the RPG library adds to a game's module set that a save needs besides the game's own builders: the revivers of
 * the modules it puts on a thing while the match runs and that hold nothing but their figures. The ones that also hold
 * something of the game's — {@code LootDrop.reviver}, {@code KeyDrop.reviver}, {@code ItemErrand.reviver},
 * {@code Mission.Counted.reviver} — are given by the game, with what they are made again with.
 */
public final class RpgModules {

    private RpgModules() {
    }

    /** Register the revivers of the modules the RPG library adds to a thing while the match runs. */
    public static void registerRevivers(ModuleFactory factory) {
        factory.revives(AttackBonus.class, AttackBonus::revive)
                .revives(AttackSpeed.class, AttackSpeed::revive)
                .revives(CriticalStrike.class, CriticalStrike::revive)
                .revives(LevelBonus.class, LevelBonus::revive)
                .revives(Summoned.class, Summoned::revive);
    }
}
