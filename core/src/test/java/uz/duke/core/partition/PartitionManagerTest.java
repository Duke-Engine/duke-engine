package uz.duke.core.partition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

class PartitionManagerTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private TestLogic logic;
    private PartitionManager partition;
    private ThingTemplate unit;
    private int me;
    private int foe;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        unit = ThingTemplate.named("Unit").module(new ActiveBody.Data(100f)).build();
        thingFactory.addTemplate(unit);
        logic = new TestLogic(thingFactory);
        logic.init();
        partition = new PartitionManager(logic::getObjects);

        var a = logic.getPlayerList().addPlayer("Me");
        var b = logic.getPlayerList().addPlayer("Foe");
        a.setRelationshipTo(b, Relationship.ENEMIES);
        b.setRelationshipTo(a, Relationship.ENEMIES);
        me = a.getIndex();
        foe = b.getIndex();
    }

    private GameObject spawn(int player, float x) {
        var o = logic.createObject(unit);
        o.setPlayerIndex(player);
        o.setPosition(new Coord3D(x, 0f, 0f));
        return o;
    }

    @Test
    void objectsInRangeRespectsDistanceAndFilter() {
        spawn(me, 0f);            // self/ally
        var nearEnemy = spawn(foe, 5f);
        var midEnemy = spawn(foe, 3f);
        spawn(foe, 100f);         // far enemy, out of range

        var enemiesNearby = partition.objectsInRange(
                Coord3D.ZERO, 10f, PartitionFilter.enemiesOf(logic, me));

        assertEquals(2, enemiesNearby.size());
        assertTrue(enemiesNearby.contains(nearEnemy));
        assertTrue(enemiesNearby.contains(midEnemy));
    }

    @Test
    void closestObjectPicksNearestMatch() {
        spawn(me, 0f);
        spawn(foe, 5f);
        var closest = spawn(foe, 3f);

        var found = partition.closestObject(Coord3D.ZERO, 10f, PartitionFilter.enemiesOf(logic, me));
        assertSame(closest, found);
    }

    @Test
    void deadObjectsAreExcludedByAliveFilter() {
        spawn(me, 0f);
        var enemy1 = spawn(foe, 5f);
        var enemy2 = spawn(foe, 3f);
        enemy2.getBody().damage(999f); // killed

        var liveEnemies = partition.objectsInRange(
                Coord3D.ZERO, 10f, PartitionFilter.enemiesOf(logic, me).and(PartitionFilter.alive()));

        assertEquals(1, liveEnemies.size());
        assertSame(enemy1, liveEnemies.get(0));
    }

    @Test
    void excludingFilterDropsSelf() {
        var self = spawn(me, 0f);
        spawn(me, 1f);
        var inRange = partition.objectsInRange(Coord3D.ZERO, 10f, PartitionFilter.excluding(self));
        assertEquals(1, inRange.size());
        assertTrue(!inRange.contains(self));
    }
}
