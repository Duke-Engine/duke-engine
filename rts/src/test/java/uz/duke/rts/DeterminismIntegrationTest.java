package uz.duke.rts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplateLoader;
import uz.duke.rts.RtsTemplate;
import uz.duke.rts.message.GameMessage;
import uz.duke.rts.module.RtsModules;
import uz.duke.rts.module.WeaponUpdate;

/**
 * The engine's core promise: a simulation is fully determined by its initial
 * state and command stream. Two independent runs of the same scenario must
 * produce bit-identical worlds — this is what lets lock-step networking keep
 * peers in sync without ever shipping world state. This test is the guard
 * against accidentally introducing nondeterminism (hash-order iteration, wall
 * clock, etc.) into the logic path.
 */
class DeterminismIntegrationTest {

    private static final String UNITS_INI = """
            Object
              Name = Tank
              KindOf = [SELECTABLE, VEHICLE, CAN_ATTACK]
              Modules = [
                ActiveBody
                  MaxHealth = 100
                End
                MoveUpdate
                  Speed = 30
                End
                WeaponUpdate
                  Damage = 10
                  AttackRange = 8
                  ReloadFrames = 3
                End
              ]
            End
            """;

    /** Routes movement and attack commands to the relevant modules. */
    static final class BattleLogic extends RtsSimulation {
        BattleLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
            switch (command) {
                case GameMessage.MoveTo move -> forEach(move.units(), unit -> {
                    var ai = unit.findModule(MoveUpdate.class);
                    if (ai != null) {
                        ai.moveTo(move.destination());
                    }
                });
                case GameMessage.AttackObject attack -> forEach(attack.units(), unit -> {
                    var weapon = unit.findModule(WeaponUpdate.class);
                    if (weapon != null) {
                        weapon.attack(attack.target());
                    }
                });
                case GameMessage.StopMoving stop -> forEach(stop.units(), unit -> {
                    var ai = unit.findModule(MoveUpdate.class);
                    if (ai != null) {
                        ai.stop();
                    }
                });
                case GameMessage.QueueProduction ignored -> {
                }
                case GameMessage.SetRallyPoint ignored -> {
                }
            }
        }

        private void forEach(List<uz.duke.core.thing.ObjectId> ids, java.util.function.Consumer<GameObject> action) {
            for (var id : ids) {
                var unit = findObject(id);
                if (unit != null) {
                    action.accept(unit);
                }
            }
        }

        @Override
        protected void simulate() {
        }
    }

    private static BattleLogic newScenario() {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        RtsTemplate.register(new ThingTemplateLoader(thingFactory)).load(UNITS_INI, "units.duke");
        var logic = new BattleLogic(thingFactory);
        logic.init();

        var a = logic.getPlayerList().addPlayer("Alpha");
        var b = logic.getPlayerList().addPlayer("Bravo");
        a.setRelationshipTo(b, Relationship.ENEMIES);
        b.setRelationshipTo(a, Relationship.ENEMIES);

        var tank = thingFactory.findTemplate("Tank");

        var attacker = logic.createObject(tank);
        attacker.setPlayerIndex(a.getIndex());
        attacker.setPosition(new Coord3D(0f, 0f, 0f));

        var defender = logic.createObject(tank);
        defender.setPlayerIndex(b.getIndex());
        defender.setPosition(new Coord3D(50f, 0f, 0f));

        // Attacker advances on the defender and opens fire.
        logic.issueCommand(new GameMessage.MoveTo(a.getIndex(), List.of(attacker.getId()), new Coord3D(45f, 0f, 0f)));
        logic.issueCommand(new GameMessage.AttackObject(a.getIndex(), List.of(attacker.getId()), defender.getId()));
        return logic;
    }

    /** A comparable snapshot of the whole world. */
    private static List<String> snapshot(GameLogic logic) {
        var lines = new ArrayList<String>();
        for (var o : logic.getObjects()) {
            var p = o.getPosition();
            float health = o.getBody() == null ? -1f : o.getBody().getHealth();
            lines.add("id=%d player=%d pos=(%.5f,%.5f,%.5f) facing=%.5f hp=%.5f"
                    .formatted(o.getId().value(), o.getPlayerIndex(), p.x(), p.y(), p.z(), o.getOrientation(), health));
        }
        return lines;
    }

    @Test
    void twoIndependentRunsProduceIdenticalWorlds() {
        var runA = newScenario();
        var runB = newScenario();

        for (int frame = 0; frame < 200; frame++) {
            runA.update();
            runB.update();
            assertEquals(snapshot(runA), snapshot(runB), "worlds diverged at frame " + frame);
            // The cheap desync check must agree with the full snapshot.
            assertEquals(runA.checksum(), runB.checksum(), "checksums diverged at frame " + frame);
        }
    }

    @Test
    void checksumChangesWhenStateChanges() {
        var logic = newScenario();
        long before = logic.checksum();
        logic.update();
        long after = logic.checksum();
        // The attacker moved this frame, so the world checksum must differ.
        assertTrue(before != after, "checksum should reflect state changes");
    }

    @Test
    void scenarioActuallyProgresses() {
        // Guard against a vacuous determinism test: the battle must do something.
        var logic = newScenario();
        for (int frame = 0; frame < 200; frame++) {
            logic.update();
        }
        var defender = logic.findObject(new uz.duke.core.thing.ObjectId(2));
        assertTrue(defender == null || defender.getBody().getHealth() < 100f,
                "defender should have taken damage (or died and been reaped)");
    }
}
