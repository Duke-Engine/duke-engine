package uz.duke.generals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.ini.Ini;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.rts.module.ExperienceModule;

/**
 * The proof that Generals is just a game.
 *
 * <p>Its veterancy — four ranks, ten percent more damage each, a full heal on
 * promotion — was written into the RTS layer, which quietly made it every game's
 * veterancy. Here it is rebuilt on the general mechanism, from outside the engine,
 * touching nothing in {@code rts}. If that works, then the rule was never a
 * mechanism, and anything else a game wants is equally expressible.
 *
 * <p>These assertions are deliberately the old ones: the same ranks at the same
 * thresholds with the same multipliers, so this doubles as evidence that nothing
 * about Generals' behaviour was lost in generalising it.
 */
class GeneralsVeterancyTest {

    private static GameObject soldier() {
        var owner = new GameObject(new ObjectId(1), ThingTemplate.named("Soldier").build());
        owner.addModule(new ActiveBody(owner, new ActiveBody.Data(100f)));
        return owner;
    }

    private static ExperienceModule veterancyOn(GameObject owner) {
        var module = new ExperienceModule(owner, GeneralsVeterancy.ladder(50, 100, 300, 600));
        owner.addModule(module);
        return module;
    }

    @Test
    void aUnitClimbsTheFourGeneralsRanks() {
        var xp = veterancyOn(soldier());
        assertEquals(0, xp.getLevel());
        assertEquals("REGULAR", GeneralsVeterancy.rankName(xp.getLevel()));

        xp.addExperience(100);
        assertEquals("VETERAN", GeneralsVeterancy.rankName(xp.getLevel()));

        xp.addExperience(200); // 300
        assertEquals("ELITE", GeneralsVeterancy.rankName(xp.getLevel()));

        xp.addExperience(300); // 600
        assertEquals("HEROIC", GeneralsVeterancy.rankName(xp.getLevel()));
        assertEquals(3, xp.getRankCount(), "four ranks counting REGULAR, three to climb");
    }

    /** The multipliers the enum used to hold, now carried by the data. */
    @Test
    void eachRankCarriesTheDamageBonusItAlwaysDid() {
        var xp = veterancyOn(soldier());

        assertEquals(1.0f, xp.damageMultiplier(), 1e-6f);
        xp.addExperience(100);
        assertEquals(1.1f, xp.damageMultiplier(), 1e-6f);
        xp.addExperience(200);
        assertEquals(1.2f, xp.damageMultiplier(), 1e-6f);
        xp.addExperience(300);
        assertEquals(1.3f, xp.damageMultiplier(), 1e-6f);
    }

    @Test
    void promotionStillFullyHealsAsItDoesInGenerals() {
        var soldier = soldier();
        var xp = veterancyOn(soldier);
        soldier.getBody().damage(70f);
        assertEquals(30f, soldier.getBody().getHealth(), 1e-4f);

        xp.addExperience(100);

        assertEquals(100f, soldier.getBody().getHealth(), 1e-4f);
    }

    /** And the same ladder authored as INI, which is how a unit file would say it. */
    @Test
    void theLadderCanBeWrittenAsUnitData() {
        var text = GeneralsVeterancy.ini(50, 100, 300, 600);
        // Strip the module header the engine's template loader would consume.
        var body = text.substring(text.indexOf('\n') + 1);
        var data = (ExperienceModule.Data) ExperienceModule.parseData(
                Ini.of(body, Ini.registry()));

        assertEquals(50, data.experienceValue());
        assertEquals(3, data.ranks().size());
        assertEquals(100, data.ranks().get(0).experience());
        assertEquals(1.1f, data.ranks().get(0).damageMultiplier(), 1e-6f);
        assertEquals(1.3f, data.ranks().get(2).damageMultiplier(), 1e-6f);
        assertTrue(data.healOnPromotion());
    }

    /**
     * The other half of the point: a different game, on the same mechanism, with
     * nothing of Generals about it.
     */
    @Test
    void anotherGamesLadderIsTheSameMechanism() {
        var owner = soldier();
        // Ten hero levels, no heal, a steeper bonus — nothing like Generals.
        var ranks = new java.util.ArrayList<ExperienceModule.Rank>();
        for (int level = 1; level <= 10; level++) {
            ranks.add(new ExperienceModule.Rank(level * 100, 1f + level * 0.25f));
        }
        var hero = new ExperienceModule(owner, new ExperienceModule.Data(0, ranks, false));
        owner.addModule(hero);
        owner.getBody().damage(50f);

        hero.addExperience(1000);

        assertEquals(10, hero.getLevel(), "ten levels, on the module that used to allow four");
        assertEquals(3.5f, hero.damageMultiplier(), 1e-6f);
        assertEquals(50f, owner.getBody().getHealth(), 1e-4f, "and no free heal");
    }
}
