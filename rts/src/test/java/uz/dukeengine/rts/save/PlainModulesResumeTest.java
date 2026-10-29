package uz.dukeengine.rts.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.AuraUpdate;
import uz.dukeengine.combat.module.AutoHealUpdate;
import uz.dukeengine.combat.module.ExperienceModule;
import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.FlyUpdate;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ScriptedMove;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.save.ModuleStates;
import uz.dukeengine.core.save.UnsavedModuleException;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rts.RtsLogic;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.module.CapacityGate;
import uz.dukeengine.rts.module.CrushUpdate;
import uz.dukeengine.rts.module.Crushable;
import uz.dukeengine.rts.module.Dock;
import uz.dukeengine.rts.module.PowerModule;
import uz.dukeengine.rts.module.SpecialPowerModule;
import uz.dukeengine.rts.module.SupplyDepot;
import uz.dukeengine.rts.module.SupplyModule;
import uz.dukeengine.rts.module.ToppleUpdate;

/**
 * Every module of the plainer kind — a body, a flier, a story's move, a timer, a rank, an aura, a power, a pile — saved
 * mid-match, loaded, and played on frame for frame as the world it was saved from; and, where a module holds anything,
 * the world loaded with that module made afresh parting from it.
 */
class PlainModulesResumeTest {

    /** A world with one template of {@code modules}, called {@code Thing}, and two sides at war. */
    private static RtsLogic rules(ModuleData... modules) {
        var logic = new RtsLogic();
        var template = RtsTemplate.named("Thing").geometry(new Geometry.Cylinder(3f, 3f));
        for (var data : modules) {
            template.module(data);
        }
        logic.getThingFactory().addTemplate(template.build());
        logic.init();
        return logic;
    }

    private static void sides(RtsLogic logic) {
        var red = logic.getPlayerList().addPlayer("Red");
        var blue = logic.getPlayerList().addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
    }

    private static GameObject thing(RtsLogic logic, int side, float x, float y) {
        return logic.spawn(logic.getThingFactory().findTemplate("Thing"), new Coord3D(x, y, 0f), side);
    }

    private static GameObject first(RtsLogic logic) {
        return logic.findObject(new ObjectId(1));
    }

    @Test
    void aBodyKeepsItsScaleItsLeastHealthAndItsSoleHealer() {
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(new ActiveBody.Data(100f)), logic -> {
            sides(logic);
            var body = thing(logic, 1, 10f, 10f).getBody();
            body.setDamageScale(0.5f);
            body.setLeastHealth(20f);
            body.healFromOne(1f, new ObjectId(9), 60);
        }, (logic, frame) -> {
            var body = first(logic).getBody();
            body.damage(7f);
            body.healFromOne(2f, new ObjectId(frame % 2 == 0 ? 9 : 8), 60);
        }, 10, 30);
    }

    @Test
    void aFlierGoesOnToWhereItWasSentAtTheSpeedItHad() {
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(new ActiveBody.Data(100f),
                new FlyUpdate.Data(FlyUpdate.Kind.HOVERING, 40f, 0f, 20f, 20f, 90f, 30f, 10f)), logic -> {
            sides(logic);
            var flier = thing(logic, 1, 10f, 10f);
            flier.findModule(FlyUpdate.class).setSpeed(60f, 120f);
            flier.findModule(FlyUpdate.class).moveTo(new Coord3D(400f, 300f, 0f));
        }, (logic, frame) -> {
        }, 20, 60);
    }

    @Test
    void aStorysMoveIsMadeAgainWhereItHadGotToAndOneWhoseLandingIsHeardIsRefused() {
        var saved = SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(new ActiveBody.Data(100f)), logic -> {
            sides(logic);
            ScriptedMove.along(thing(logic, 1, 10f, 10f), new Coord3D(200f, 50f, 0f), 40, 15f);
        }, (logic, frame) -> {
        }, 12, 40);
        assertTrue(saved.contains("+ " + ScriptedMove.class.getName()), saved);

        var heard = rules(new ActiveBody.Data(100f));
        sides(heard);
        ScriptedMove.along(thing(heard, 1, 10f, 10f), new Coord3D(200f, 50f, 0f), 40, 15f).onLanded(() -> { });
        heard.update();
        var refused = assertThrows(UnsavedModuleException.class, () -> ModuleStates.save(first(heard),
                heard.getThingFactory().getModuleFactory()));
        assertEquals(ScriptedMove.class.getName(), refused.module());
    }

    @Test
    void aSelfHealerHoldsNothingAndGoesOnHealing() {
        SaveLoadCheck.resumes(() -> rules(new ActiveBody.Data(100f), new AutoHealUpdate.Data(3f)), logic -> {
            sides(logic);
            thing(logic, 1, 10f, 10f).getBody().damage(60f);
        }, (logic, frame) -> {
        }, 10, 30);
    }

    @Test
    void aRankKeepsItsExperience() {
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(new ActiveBody.Data(100f), new ExperienceModule.Data(10,
                List.of(100, 250), List.of(1.2f, 1.5f), false, List.of("VETERAN", "ELITE"), List.of(1.2f, 1.5f))),
                logic -> {
                    sides(logic);
                    thing(logic, 1, 10f, 10f).getBody().damage(30f);
                }, (logic, frame) -> first(logic).findModule(ExperienceModule.class).addExperience(9), 8, 40);
    }

    @Test
    void aTimedStatusKeepsTheFramesItHasLeft() {
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(new ActiveBody.Data(100f), new StatusUpdate.Data()),
                logic -> {
                    sides(logic);
                    thing(logic, 1, 10f, 10f).findModule(StatusUpdate.class).apply(ObjectStatus.DISABLED, 25);
                }, (logic, frame) -> {
                }, 5, 30);
    }

    @Test
    void anAuraKeepsWhomItHoldsAndWhenItLooksNext() {
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(new ActiveBody.Data(100f), new AuraUpdate.Data(50f, 10,
                List.of("INSPIRED"), 0.3f, List.of(), List.of(), List.of(), null)), logic -> {
                    sides(logic);
                    thing(logic, 1, 10f, 10f);
                    thing(logic, 1, 30f, 10f).getBody().damage(60f);
                }, (logic, frame) -> {
                    if (frame == 13) {
                        logic.findObject(new ObjectId(2)).setPosition(new Coord3D(300f, 10f, 0f));
                    }
                }, 6, 30);
    }

    @Test
    void aSpecialPowerKeepsItsRecharge() {
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(new ActiveBody.Data(500f),
                new SpecialPowerModule.Data(30, 20f, 40f)), logic -> {
                    sides(logic);
                    thing(logic, 1, 10f, 10f);
                    thing(logic, 2, 100f, 100f);
                }, (logic, frame) -> first(logic).findModule(SpecialPowerModule.class)
                        .fire(new Coord3D(100f, 100f, 0f)), 5, 40);
    }

    @Test
    void aPowerPlantKeepsItsBonusAndAGateHoldsNothing() {
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(new ActiveBody.Data(500f), new PowerModule.Data(10, 2),
                new CapacityGate.Data()), logic -> {
                    sides(logic);
                    thing(logic, 1, 10f, 10f).findModule(PowerModule.class).setBonus(5);
                }, (logic, frame) -> {
                }, 3, 5);
    }

    @Test
    void aCrusherKeepsWhereItStoodAndCrushesOnAsIfNeverSaved() {
        SaveLoadCheck.resumes(() -> {
            var logic = rules(new ActiveBody.Data(100f), new CrushUpdate.Data(2), new Crushable.Data(1));
            return logic;
        }, logic -> {
            sides(logic);
            thing(logic, 1, 10f, 10f);
            thing(logic, 2, 40f, 10f);
        }, (logic, frame) -> {
            var crusher = first(logic);
            crusher.setPosition(crusher.getPosition().add(new Coord3D(1.5f, 0f, 0f)));
        }, 14, 30);
    }

    @Test
    void aToppledThingFallsOnAndSinksFromWhereItHadGotTo() {
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(new ActiveBody.Data(100f),
                new ToppleUpdate.Data(1, 7f, 0.05f, 0.2f, 0.01f, 0.3f, 10f, 40)), logic -> {
                    sides(logic);
                    var tree = thing(logic, 0, 10f, 10f);
                    tree.findModule(ToppleUpdate.class).topple(null, new Coord3D(0f, 10f, 0f));
                }, (logic, frame) -> {
                }, 6, 120);
    }

    @Test
    void aPileKeepsWhatIsLeftAndADepotItsDock() {
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(new ActiveBody.Data(100f),
                new SupplyModule.Data(500, new Dock(2, null, null)), new SupplyDepot.Data(new Dock(1, null, null))),
                logic -> {
                    sides(logic);
                    thing(logic, 1, 10f, 10f).findModule(SupplyDepot.class).setDockClosed(true);
                }, (logic, frame) -> first(logic).findModule(SupplyModule.class).take(7), 5, 10);
    }
}
