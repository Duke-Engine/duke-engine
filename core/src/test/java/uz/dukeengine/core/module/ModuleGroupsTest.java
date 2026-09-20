package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;

class ModuleGroupsTest {

    @Test
    void aModulesGroupsAreReadByReflectionAtRuntime() {
        // A wizard and the IDE plugin read these off the class, so they have to
        // survive into the class file and the running VM.
        assertEquals(List.of(ModuleGroups.MOVEMENT), ModuleGroups.of(MoveUpdate.class));
        assertEquals(List.of(ModuleGroups.BODY), ModuleGroups.of(ActiveBody.class));
    }

    @Test
    void severalGroupsComeBackInTheOrderTheModuleNamesThem() {
        assertEquals(List.of(ModuleGroups.EFFECT, ModuleGroups.COMBAT), ModuleGroups.of(Blast.class));
    }

    @Test
    void aSubclassIsFiledWhereItsParentIsUntilItSaysOtherwise() {
        assertEquals(List.of(ModuleGroups.EFFECT, ModuleGroups.COMBAT), ModuleGroups.of(BiggerBlast.class));
        assertEquals(List.of(ModuleGroups.BODY), ModuleGroups.of(MendingBlast.class));
    }

    @Test
    void aModuleThatNamesNoGroupIsInNone() {
        assertEquals(List.of(), ModuleGroups.of(Unfiled.class));
    }

    @ModuleGroup({ModuleGroups.EFFECT, ModuleGroups.COMBAT})
    private static class Blast extends Module {
        Blast(GameObject owner) {
            super(owner);
        }
    }

    private static final class BiggerBlast extends Blast {
        BiggerBlast(GameObject owner) {
            super(owner);
        }
    }

    @ModuleGroup(ModuleGroups.BODY)
    private static final class MendingBlast extends Blast {
        MendingBlast(GameObject owner) {
            super(owner);
        }
    }

    private static final class Unfiled extends Module {
        Unfiled(GameObject owner) {
            super(owner);
        }
    }
}
