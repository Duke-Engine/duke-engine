package uz.duke.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.ThingTemplate;

/**
 * Experience, and a ladder whose shape is the game's to choose.
 *
 * <p>These were written against four rungs named after Generals' ranks, with the
 * bonuses each carried compiled into an enum. That is one game's ladder. The
 * mechanism is a list of thresholds and what each is worth, so the same tests now
 * ask about ladders of two rungs, ten rungs, and none — including the one Generals
 * happens to use, which lives with Generals.
 */
class ExperienceModuleTest {

    private static ExperienceModule tracker(ExperienceModule.Data data) {
        var owner = new GameObject(new ObjectId(1), ThingTemplate.named("Unit").build());
        var module = new ExperienceModule(owner, data);
        owner.addModule(module);
        return module;
    }

    private static ExperienceModule.Data ladder(int worth, int... thresholds) {
        return ExperienceModule.Data.ofThresholds(worth, thresholds);
    }

    @Test
    void ranksUpAsExperienceCrossesThresholds() {
        var xp = tracker(ladder(50, 100, 300, 600));
        assertEquals(0, xp.getLevel(), "unranked to begin with");

        xp.addExperience(100);
        assertEquals(1, xp.getLevel());

        xp.addExperience(200); // total 300
        assertEquals(2, xp.getLevel());

        xp.addExperience(300); // total 600
        assertEquals(3, xp.getLevel(), "the top of this ladder");
    }

    /** A ladder is as long as the game says. Four rungs is not a rule. */
    @Test
    void aLadderCanBeAnyLength() {
        var twoRungs = tracker(ladder(0, 10, 20));
        twoRungs.addExperience(25);
        assertEquals(2, twoRungs.getLevel());
        assertEquals(2, twoRungs.getRankCount());

        var tenRungs = tracker(ladder(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        tenRungs.addExperience(7);
        assertEquals(7, tenRungs.getLevel(), "a hero climbing ten levels is the same module");
    }

    /** Each rung is worth what the data says, not what an enum decided. */
    @Test
    void eachRungCarriesItsOwnBonus() {
        var xp = tracker(new ExperienceModule.Data(0, List.of(
                new ExperienceModule.Rank(10, 2f),
                new ExperienceModule.Rank(20, 5f)), false));

        assertEquals(1f, xp.damageMultiplier(), 1e-6f, "unranked changes nothing");
        xp.addExperience(10);
        assertEquals(2f, xp.damageMultiplier(), 1e-6f);
        xp.addExperience(10);
        assertEquals(5f, xp.damageMultiplier(), 1e-6f);
    }

    /** A ladder of unreachable rungs counts experience and promotes nobody. */
    @Test
    void zeroThresholdsMeanCountButNeverPromote() {
        var xp = tracker(ladder(0, 0, 0, 0));

        xp.addExperience(10_000);

        assertEquals(10_000, xp.getExperience(), "still counted");
        assertEquals(0, xp.getLevel(), "and never promoted");
        assertEquals(1f, xp.damageMultiplier(), 1e-6f);
    }

    /**
     * Healing on promotion is a rule a game asks for. Generals heals; a game where
     * levelling mid-fight should not rescue you does not.
     */
    @Test
    void healingOnPromotionIsAskedForRatherThanAssumed() {
        var owner = new GameObject(new ObjectId(1), ThingTemplate.named("Unit").build());
        owner.addModule(new ActiveBody(owner, new ActiveBody.Data(100f)));
        owner.getBody().damage(70f);

        var plain = new ExperienceModule(owner, ladder(0, 10));
        owner.addModule(plain);
        plain.addExperience(10);
        assertEquals(30f, owner.getBody().getHealth(), 1e-4f, "no heal unless asked");

        var healing = new ExperienceModule(owner, new ExperienceModule.Data(0,
                List.of(new ExperienceModule.Rank(10, 1f)), true));
        healing.addExperience(10);
        assertEquals(100f, owner.getBody().getHealth(), 1e-4f, "asked for, and given");
    }

    @Test
    void experienceValueIsWhatKillersEarn() {
        assertEquals(75, tracker(ladder(75, 100, 300, 600)).getExperienceValue());
    }

    @Test
    void negativeExperienceIgnored() {
        var xp = tracker(ladder(0, 100, 300, 600));
        xp.addExperience(-50);
        assertEquals(0, xp.getExperience());
        assertEquals(0, xp.getLevel());
    }

    private static ExperienceModule.Data read(String block) {
        return new uz.duke.core.data.Binder().bind(uz.duke.core.data.DukeText.parse(block, "xp.duke").getFirst(),
                ExperienceModule.Data.class);
    }

    @Test
    void isReadFromItsBlock() {
        var data = read("""
                ExperienceModule
                  ExperienceValue = 25
                  ExperienceRequired = [100, 200, 400]
                  LevelDamageBonus = [1.1, 1.2, 1.3]
                  HealOnPromotion = Yes
                End
                """);

        assertEquals(25, data.experienceValue());
        assertEquals(3, data.ranks().size());
        assertEquals(100, data.ranks().get(0).experience());
        assertEquals(400, data.ranks().get(2).experience());
        assertEquals(1.3f, data.ranks().get(2).damageMultiplier(), 1e-6f);
        assertTrue(data.healOnPromotion());
    }

    /** A ladder may name its costs and say nothing about bonuses. */
    @Test
    void aLadderWithoutBonusesStillParses() {
        var data = read("""
                ExperienceModule
                  ExperienceValue = 10
                  ExperienceRequired = [5, 10]
                End
                """);

        assertEquals(2, data.ranks().size());
        assertEquals(1f, data.ranks().get(1).damageMultiplier(), 1e-6f);
        assertFalse(data.healOnPromotion());
    }
}
