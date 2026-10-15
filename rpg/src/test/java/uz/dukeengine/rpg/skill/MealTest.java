package uz.dukeengine.rpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;

/** A meal's health over time is a HEAL buff the book pays out, as a drink is a MANA_AURA its trickle reads (E68). */
class MealTest {

    @Test
    void aHealBuffOfAHundredTenthsForTwoSecondsGivesTwentyPointsAndNoMore() {
        var world = new RpgWorld(Map.of(), Seal.NONE)
                .template("Hero", new ActiveBody.Data(100f), new SkillBook.Data());
        var hero = world.put("Hero", world.heroes, 0f, 0f);
        world.run(1);
        hero.getBody().damage(50f);
        var book = hero.findModule(SkillBook.class);

        int from = world.getFrame() + 1;
        book.give(SkillEffect.HEAL, 99, 100, from + 59); // the meal's sixty frames
        assertEquals(10f, book.healthPerSecond(), 1e-6f, "ten points a second, counted");
        world.run(60);
        assertEquals(70f, hero.getBody().getHealth(), 1e-3f, "twenty points fuller two seconds later");
        world.run(60);
        assertEquals(70f, hero.getBody().getHealth(), 1e-3f, "and no fuller after");
        assertEquals(0f, book.healthPerSecond(), 1e-6f);
    }
}
