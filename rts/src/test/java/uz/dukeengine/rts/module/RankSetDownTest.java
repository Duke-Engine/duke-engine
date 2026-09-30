package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.combat.module.ExperienceModule;

/**
 * A thing's rank set down as well as up — the reference's {@code ExperienceTracker::setExperienceAndLevel}: a veteran
 * vehicle whose crew a sniper killed goes back to no rank, and a bike's rider and the bike swap theirs.
 */
class RankSetDownTest {

    private static GameObject veteranCapable() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Humvee").module(new ActiveBody.Data(100f))
                .module(new ExperienceModule.Data(20, List.of(10, 20, 40), List.of(1.1f, 1.2f, 1.3f), true,
                        List.of("VETERAN", "ELITE", "HEROIC"), List.of(1.2f, 1.3f, 1.5f)))
                .build());
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        var humvee = logic.createObject(factory.findTemplate("Humvee"));
        humvee.setPosition(Coord3D.ZERO);
        return humvee;
    }

    @Test
    void aVeteranSetToNothingHasNoRankNoWordAndNoRanksHealthAndCountsOnFromNothing() {
        var humvee = veteranCapable();
        var rank = humvee.findModule(ExperienceModule.class);
        rank.addExperience(10);
        assertEquals(1, rank.getLevel());
        assertEquals(120f, humvee.getBody().getMaxHealth(), 1e-3f);
        humvee.getBody().setHealth(60f);

        rank.setExperience(0);
        assertEquals(0, rank.getLevel(), "no rank");
        assertFalse(humvee.getConditions().contains("VETERAN"), "nor its word");
        assertEquals(100f, humvee.getBody().getMaxHealth(), 1e-3f, "the most health of no rank");
        assertEquals(50f, humvee.getBody().getHealth(), 1e-3f, "its share kept, and no heal");
        assertEquals(1f, rank.damageMultiplier());

        rank.addExperience(5);
        assertEquals(5, rank.getExperience(), "a kill's experience counts on from 0");
    }

    @Test
    void anEliteSetToAVeteransExperienceIsAVeteran() {
        var humvee = veteranCapable();
        var rank = humvee.findModule(ExperienceModule.class);
        rank.addExperience(25);
        assertEquals(2, rank.getLevel());

        rank.setExperience(12);
        assertEquals(1, rank.getLevel());
        assertTrue(humvee.getConditions().contains("VETERAN"));
        assertFalse(humvee.getConditions().contains("ELITE"));
        assertEquals(120f, humvee.getBody().getMaxHealth(), 1e-3f);
    }
}
