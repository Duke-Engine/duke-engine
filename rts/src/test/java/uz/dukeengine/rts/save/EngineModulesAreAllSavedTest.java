package uz.dukeengine.rts.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.CombatModules;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.rts.module.RtsModules;

/** A save refuses a module that is neither Saveable nor Stateless, so the engine's own sets must hold none. */
class EngineModulesAreAllSavedTest {

    @Test
    void everyModuleTheEngineRegistersIsSaveableOrStateless() {
        assertEquals(List.of(), ModuleFactory.withDefaults().unsaved(), "core's");
        var combat = new ModuleFactory();
        CombatModules.register(combat);
        assertEquals(List.of(), combat.unsaved(), "combat's");
        assertEquals(List.of(), RtsModules.withDefaults().unsaved(), "the RTS library's, with all of those");
    }

    @Test
    void theRtsSetNamesEveryModuleItsFactoryBuilds() {
        var factory = RtsModules.withDefaults();
        assertTrue(factory.vocabulary().values().containsAll(RtsModules.MODULES), "every listed module is registered");
    }
}
