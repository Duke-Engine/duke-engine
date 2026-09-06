package uz.duke.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.ThingTemplate;

class ExperienceModuleTest {

    private static ExperienceModule newTracker(int value, int vet, int elite, int heroic) {
        var owner = new GameObject(new ObjectId(1), ThingTemplate.named("Unit").build());
        return new ExperienceModule(owner, new ExperienceModule.Data(value, vet, elite, heroic));
    }

    @Test
    void ranksUpAsExperienceCrossesThresholds() {
        var xp = newTracker(50, 100, 300, 600);
        assertEquals(VeterancyLevel.REGULAR, xp.getLevel());

        xp.addExperience(100);
        assertEquals(VeterancyLevel.VETERAN, xp.getLevel());

        xp.addExperience(200); // total 300
        assertEquals(VeterancyLevel.ELITE, xp.getLevel());

        xp.addExperience(300); // total 600
        assertEquals(VeterancyLevel.HEROIC, xp.getLevel());
        assertEquals(1.3f, xp.getDamageMultiplier(), 1e-6f);
    }

    @Test
    void experienceValueIsWhatKillersEarn() {
        var xp = newTracker(75, 100, 300, 600);
        assertEquals(75, xp.getExperienceValue());
    }

    @Test
    void negativeExperienceIgnored() {
        var xp = newTracker(0, 100, 300, 600);
        xp.addExperience(-50);
        assertEquals(0, xp.getExperience());
        assertEquals(VeterancyLevel.REGULAR, xp.getLevel());
    }

    @Test
    void parsesFromIni() {
        var ini = uz.duke.core.ini.Ini.of("""
                ExperienceValue = 25
                ExperienceRequired = 100 200 400
                End
                """, uz.duke.core.ini.Ini.registry());
        var data = (ExperienceModule.Data) ExperienceModule.parseData(ini);
        assertEquals(25, data.experienceValue());
        assertEquals(100, data.veteranXp());
        assertEquals(200, data.eliteXp());
        assertEquals(400, data.heroicXp());
    }
}
