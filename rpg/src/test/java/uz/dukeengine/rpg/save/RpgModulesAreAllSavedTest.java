package uz.dukeengine.rpg.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.hero.AttackBonus;
import uz.dukeengine.rpg.hero.AttackSpeed;
import uz.dukeengine.rpg.hero.CriticalStrike;
import uz.dukeengine.rpg.hero.LevelBonus;
import uz.dukeengine.rpg.skill.Summoned;

/** A save refuses a module that is neither Saveable nor Stateless, so the RPG library's must hold none. */
class RpgModulesAreAllSavedTest {

    @Test
    void everyModuleTheLibraryIsRegisteredWithIsSaveableOrStateless() {
        var factory = RpgSaveCheck.world(Map.of(), Seal.NONE).getThingFactory().getModuleFactory();
        assertEquals(List.of(), factory.unsaved());
    }

    @Test
    void everyModuleTheLibraryAddsWhileAMatchRunsHasARevivalThatNeedsNothingOfTheGame() {
        var factory = RpgSaveCheck.world(Map.of(), Seal.NONE).getThingFactory().getModuleFactory();
        for (var added : List.of(AttackBonus.class, AttackSpeed.class, CriticalStrike.class, LevelBonus.class,
                Summoned.class)) {
            assertNotNull(factory.reviverFor(added.getName()), added.getName());
        }
    }
}
