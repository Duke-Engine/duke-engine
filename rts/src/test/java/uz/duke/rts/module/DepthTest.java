package uz.duke.rts.module;

import uz.duke.rts.RtsTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.rts.RtsSimulation;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.DamageType;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.rts.message.GameMessage;

/** Exercises the deeper system behaviours: splash, promotion heal, rally, turn rate. */
class DepthTest {

    static final class TestLogic extends RtsSimulation {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
            switch (command) {
                case GameMessage.AttackObject a -> a.units().forEach(id ->
                        findObject(id).findModule(WeaponUpdate.class).attack(a.target()));
                case GameMessage.MoveTo m -> m.units().forEach(id ->
                        findObject(id).findModule(MoveUpdate.class).moveTo(m.destination()));
                case GameMessage.StopMoving s -> {
                }
                case GameMessage.QueueProduction ignored -> {
                }
                case GameMessage.SetRallyPoint ignored -> {
                }
            }
        }

        @Override
        protected void simulate() {
        }
    }

    private static TestLogic logicWith(ThingTemplate... templates) {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        for (var t : templates) {
            thingFactory.addTemplate(t);
        }
        var logic = new TestLogic(thingFactory);
        logic.init();
        return logic;
    }

    @Test
    void splashDamageHitsBystandingEnemies() {
        var artillery = RtsTemplate.named("Artillery")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .module("WeaponUpdate", new WeaponUpdate.Data(20f, 30f, 2, DamageType.EXPLOSION, 8f))
                .build();
        var grunt = RtsTemplate.named("Grunt")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .build();
        var logic = logicWith(artillery, grunt);
        int me = logic.getPlayerList().addPlayer("Me").getIndex();
        int foe = logic.getPlayerList().addPlayer("Foe").getIndex();
        logic.getPlayerList().getPlayer(me).setRelationshipTo(logic.getPlayerList().getPlayer(foe), Relationship.ENEMIES);
        logic.getPlayerList().getPlayer(foe).setRelationshipTo(logic.getPlayerList().getPlayer(me), Relationship.ENEMIES);

        var gun = logic.createObject(artillery);
        gun.setPlayerIndex(me);
        gun.setPosition(Coord3D.ZERO);
        GameObject primary = spawn(logic, grunt, foe, 10f, 0f);
        GameObject bystander = spawn(logic, grunt, foe, 13f, 0f); // within 8 of primary
        GameObject distant = spawn(logic, grunt, foe, 30f, 0f);   // outside splash

        logic.issueCommand(new GameMessage.AttackObject(me, java.util.List.of(gun.getId()), primary.getId()));
        logic.update();

        assertEquals(80f, primary.getBody().getHealth(), 1e-4f);   // direct 20
        assertEquals(80f, bystander.getBody().getHealth(), 1e-4f); // splash 20
        assertEquals(100f, distant.getBody().getHealth(), 1e-4f);  // unscathed
    }

    @Test
    void promotionHealsTheUnitToFull() {
        var trooper = RtsTemplate.named("Trooper")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .module("WeaponUpdate", new WeaponUpdate.Data(40f, 10f, 1))
                // Healing on promotion is asked for here, since that is what this tests.
                .module("ExperienceModule", new ExperienceModule.Data(0,
                        java.util.List.of(new ExperienceModule.Rank(10, 1f),
                                new ExperienceModule.Rank(50, 1f),
                                new ExperienceModule.Rank(100, 1f)), true))
                .build();
        var dummy = RtsTemplate.named("Dummy")
                .module("ActiveBody", new ActiveBody.Data(30f))
                .module("ExperienceModule", ExperienceModule.Data.ofThresholds(20, 10, 50, 100)) // worth 20 -> first rank
                .build();
        var logic = logicWith(trooper, dummy);
        int me = logic.getPlayerList().addPlayer("Me").getIndex();
        int foe = logic.getPlayerList().addPlayer("Foe").getIndex();
        logic.getPlayerList().getPlayer(me).setRelationshipTo(logic.getPlayerList().getPlayer(foe), Relationship.ENEMIES);

        var attacker = logic.createObject(trooper);
        attacker.setPlayerIndex(me);
        attacker.setPosition(Coord3D.ZERO);
        attacker.getBody().damage(70f); // wounded to 30
        var victim = spawn(logic, dummy, foe, 5f, 0f);

        logic.issueCommand(new GameMessage.AttackObject(me, java.util.List.of(attacker.getId()), victim.getId()));
        for (int i = 0; i < 5; i++) {
            logic.update();
        }
        assertTrue(victim.isEffectivelyDead());
        assertEquals(1, attacker.findModule(ExperienceModule.class).getLevel());
        assertEquals(100f, attacker.getBody().getHealth(), 1e-4f); // promotion fully healed it
    }

    @Test
    void producedUnitsMoveToRallyPoint() {
        var soldier = RtsTemplate.named("Soldier")
                .module("ActiveBody", new ActiveBody.Data(50f))
                .module("MoveUpdate", new MoveUpdate.Data(20f))
                .buildCost(0)
                .buildTimeFrames(2)
                .build();
        var factory = RtsTemplate.named("Factory")
                .module("ActiveBody", new ActiveBody.Data(400f))
                .module("ProductionUpdate", new ProductionUpdate.Data())
                .build();
        var logic = logicWith(soldier, factory);
        int me = logic.getPlayerList().addPlayer("Me").getIndex();

        var fac = logic.createObject(factory);
        fac.setPlayerIndex(me);
        fac.setPosition(Coord3D.ZERO);
        var production = fac.findModule(ProductionUpdate.class);
        production.setRallyPoint(new Coord3D(50f, 0f, 0f));
        production.queue(soldier);

        for (int i = 0; i < 2; i++) {
            logic.update(); // soldier produced on the 2nd frame
        }
        var produced = logic.getObjects().stream()
                .filter(o -> o.getTemplate() == soldier).findFirst().orElseThrow();
        float xAtSpawn = produced.getPosition().x();
        assertTrue(produced.findModule(MoveUpdate.class).isMoving());

        for (int i = 0; i < 5; i++) {
            logic.update();
        }
        assertTrue(produced.getPosition().x() > xAtSpawn, "rallying unit should advance toward the rally point");
    }

    @Test
    void turnRateMakesUnitCurveInsteadOfSnapping() {
        var tank = RtsTemplate.named("Tank")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .module("MoveUpdate", new MoveUpdate.Data(30f, 90f)) // 90 deg/sec turn rate
                .build();
        var logic = logicWith(tank);
        int me = logic.getPlayerList().addPlayer("Me").getIndex();

        var unit = logic.createObject(tank);
        unit.setPlayerIndex(me);
        unit.setPosition(Coord3D.ZERO);
        unit.setOrientation(0f); // facing +x
        unit.findModule(MoveUpdate.class).moveTo(new Coord3D(0f, 50f, 0f)); // goal is +y

        logic.update();
        // It must not have snapped to facing +y; it turned a little and crept along +x.
        float facing = unit.getOrientation();
        assertTrue(facing > 0f && facing < (float) (Math.PI / 2 - 0.01),
                "should have turned partway toward +y, not snapped");
        assertTrue(unit.getPosition().x() > 0f, "should have moved along its (mostly +x) facing");
    }

    private static GameObject spawn(GameLogic logic, ThingTemplate template, int player, float x, float y) {
        var o = logic.createObject(template);
        o.setPlayerIndex(player);
        o.setPosition(new Coord3D(x, y, 0f));
        return o;
    }
}
