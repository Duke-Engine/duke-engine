package uz.duke.rts.module;

import uz.duke.rts.RtsTemplate;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.Footprint;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.Geometry;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.core.thing.World;
import uz.duke.rts.RtsSimulation;
import uz.duke.rts.message.GameMessage;

/** What having a physical size means for the RTS rules built on top of it. */
class SolidWorldTest {

    static final class TestLogic extends RtsSimulation {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
        }

        @Override
        protected void simulate() {
        }
    }

    private static final ThingTemplate SOLDIER = RtsTemplate.named("Soldier")
            .geometry(new Geometry.Cylinder(3f, 9f))
            .buildCost(100)
            .buildTimeFrames(2)
            .module(new ActiveBody.Data(80f))
            .module(new WeaponUpdate.Data(10f, 5f, 10)) // very short reach
            .build();

    private static final ThingTemplate BARRACKS = RtsTemplate.named("Barracks")
            .geometry(new Geometry.Box(20f, 16f, 14f))
            .module(new ActiveBody.Data(600f))
            .module(new ProductionUpdate.Data())
            .build();

    private static TestLogic logicWith(ThingTemplate... templates) {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        for (var template : templates) {
            thingFactory.addTemplate(template);
        }
        var logic = new TestLogic(thingFactory);
        logic.init();
        return logic;
    }

    private static GameObject spawn(TestLogic logic, ThingTemplate template, float x, float y, int player) {
        var object = logic.createObject(template);
        object.setPosition(new Coord3D(x, y, 0f));
        object.setPlayerIndex(player);
        return object;
    }

    @Test
    void aProducedUnitAppearsOutsideTheBuildingThatMadeIt() {
        var logic = logicWith(SOLDIER, BARRACKS);
        int usa = logic.getPlayerList().addPlayer("USA").getIndex();
        logic.getRtsPlayer(usa).deposit(1000);

        var barracks = spawn(logic, BARRACKS, 200f, 200f, usa);
        var production = barracks.findModule(ProductionUpdate.class);

        // Three in a row: the doorway is occupied by the time the second arrives.
        for (int unit = 0; unit < 3; unit++) {
            production.queue(SOLDIER);
            for (int frame = 0; frame < 5; frame++) {
                logic.update();
            }
        }

        var soldiers = logic.getObjects().stream()
                .filter(o -> o.getTemplate() == SOLDIER)
                .toList();
        assertTrue(soldiers.size() == 3, "all three should have been built, got " + soldiers.size());
        for (var soldier : soldiers) {
            assertFalse(Footprint.of(soldier).overlaps(Footprint.of(barracks)),
                    "a unit must not be built inside its own factory (at " + soldier.getPosition() + ")");
            for (var other : soldiers) {
                if (other != soldier) {
                    assertFalse(Footprint.of(soldier).overlaps(Footprint.of(other)),
                            "queued units must not pile up on each other");
                }
            }
        }
    }

    @Test
    void aShortRangedUnitCanHitTheWallItIsStandingAgainst() {
        var logic = logicWith(SOLDIER, BARRACKS);
        int usa = logic.getPlayerList().addPlayer("USA").getIndex();
        int foe = logic.getPlayerList().addPlayer("China").getIndex();
        logic.getPlayer(usa).setRelationshipTo(logic.getPlayer(foe), Relationship.ENEMIES);
        logic.getPlayer(foe).setRelationshipTo(logic.getPlayer(usa), Relationship.ENEMIES);

        var barracks = spawn(logic, BARRACKS, 200f, 200f, foe);
        // Standing just off the wall: 24 from the centre, but only 1 from the surface.
        var soldier = spawn(logic, SOLDIER, 224f, 200f, usa);

        assertTrue(World.reachBetween(soldier, barracks) < 5f,
                "wall-to-wall this is point blank, even though the centres are 24 apart");

        float healthBefore = barracks.getBody().getHealth();
        soldier.findModule(WeaponUpdate.class).attack(barracks.getId());
        for (int frame = 0; frame < 30; frame++) {
            logic.update();
        }

        assertTrue(barracks.getBody().getHealth() < healthBefore,
                "a unit against a building must be able to shoot it");
    }

    @Test
    void aBuildingIsAcquiredByItsWallNotItsCentre() {
        var logic = logicWith(SOLDIER, BARRACKS);
        int usa = logic.getPlayerList().addPlayer("USA").getIndex();
        int foe = logic.getPlayerList().addPlayer("China").getIndex();
        logic.getPlayer(usa).setRelationshipTo(logic.getPlayer(foe), Relationship.ENEMIES);
        logic.getPlayer(foe).setRelationshipTo(logic.getPlayer(usa), Relationship.ENEMIES);

        var barracks = spawn(logic, BARRACKS, 200f, 200f, foe);
        var soldier = spawn(logic, SOLDIER, 226f, 200f, usa);

        logic.update(); // auto-acquire runs inside the weapon's update

        assertTrue(barracks.getId().equals(soldier.findModule(WeaponUpdate.class).getTarget()),
                "the wall is 3 away, well inside the soldier's 5-unit reach");
    }
}
