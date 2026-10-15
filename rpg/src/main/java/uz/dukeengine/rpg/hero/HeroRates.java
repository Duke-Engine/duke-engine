package uz.dukeengine.rpg.hero;

import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.skill.SkillBook;

/**
 * What a hero's body does each second, as the fight works it out: read off the very modules that act on it — the
 * weapon's rate in its hands, his recovery, his book's pool and passives, worn things lent included, and the auras on
 * him — so a sheet printing these can never disagree with the blows.
 *
 * @param blowsPerSecond   how often his weapon strikes: its wait divided by everything that hastens it
 * @param healthPerSecond  health that comes back on its own, his recovery's and a passive's
 * @param manaPerSecond    mana that comes back on its own, his pool's and an aura's
 * @param lifestealPercent the share of every blow he drinks back: his own, a worn thing's and an aura's
 */
public record HeroRates(float blowsPerSecond, float healthPerSecond, float manaPerSecond, int lifestealPercent) {

    /** What {@code body} does each second now. */
    public static HeroRates of(GameObject body) {
        var weapon = body.findModule(WeaponUpdate.class);
        var recovery = body.findModule(Recovery.class);
        var book = body.findModule(SkillBook.class);
        float health = (recovery == null ? 0f : recovery.getRate() / 10f) + (book == null ? 0f : book.healthPerSecond());
        return new HeroRates(weapon == null ? 0f : weapon.blowsPerSecond(), health,
                book == null ? 0f : book.manaPerSecond(), SkillBook.lifestealPercent(body));
    }
}
