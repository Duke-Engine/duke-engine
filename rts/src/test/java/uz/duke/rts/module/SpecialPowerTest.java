package uz.duke.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

class SpecialPowerTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private TestLogic logic;
    private ThingTemplate target;
    private GameObject launcher;
    private int usa;
    private int china;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        target = ThingTemplate.named("Target")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .build();
        var battleStation = ThingTemplate.named("Station")
                .module("ActiveBody", new ActiveBody.Data(500f))
                .module("SpecialPowerModule", new SpecialPowerModule.Data(30, 15f, 40f))
                .build();
        thingFactory.addTemplate(target);
        thingFactory.addTemplate(battleStation);

        logic = new TestLogic(thingFactory);
        logic.init();
        usa = logic.getPlayerList().addPlayer("USA").getIndex();
        china = logic.getPlayerList().addPlayer("China").getIndex();
        logic.getPlayerList().getPlayer(usa).setRelationshipTo(
                logic.getPlayerList().getPlayer(china), Relationship.ENEMIES);

        launcher = logic.createObject(battleStation);
        launcher.setPlayerIndex(usa);
        launcher.setPosition(Coord3D.ZERO);
    }

    private GameObject enemyAt(float x) {
        var o = logic.createObject(target);
        o.setPlayerIndex(china);
        o.setPosition(new Coord3D(x, 0f, 0f));
        return o;
    }

    @Test
    void blastDamagesEnemiesInRadiusOnly() {
        var inside = enemyAt(10f);  // within radius 15
        var outside = enemyAt(50f); // beyond radius

        var power = launcher.findModule(SpecialPowerModule.class);
        assertTrue(power.isReady());
        assertTrue(power.fire(new Coord3D(10f, 0f, 0f)));

        assertEquals(60f, inside.getBody().getHealth(), 1e-4f); // took 40
        assertEquals(100f, outside.getBody().getHealth(), 1e-4f);
    }

    @Test
    void friendlyUnitsAreNotHarmed() {
        var friendly = logic.createObject(target);
        friendly.setPlayerIndex(usa);
        friendly.setPosition(new Coord3D(5f, 0f, 0f));

        launcher.findModule(SpecialPowerModule.class).fire(new Coord3D(5f, 0f, 0f));
        assertEquals(100f, friendly.getBody().getHealth(), 1e-4f);
    }

    @Test
    void powerRechargesBeforeReuse() {
        var enemy = enemyAt(10f);
        var power = launcher.findModule(SpecialPowerModule.class);

        assertTrue(power.fire(new Coord3D(10f, 0f, 0f)));
        assertFalse(power.isReady());
        assertFalse(power.fire(new Coord3D(10f, 0f, 0f))); // still recharging
        assertEquals(60f, enemy.getBody().getHealth(), 1e-4f); // only hit once

        for (int i = 0; i < 30; i++) {
            logic.update();
        }
        assertTrue(power.isReady());
        assertTrue(power.fire(new Coord3D(10f, 0f, 0f))); // ready again
        assertEquals(20f, enemy.getBody().getHealth(), 1e-4f);
    }
}
