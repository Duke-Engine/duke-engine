package uz.dukeengine.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

class VisionTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private TestLogic logic;
    private ThingTemplate scout;
    private int usa;
    private int china;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        scout = ThingTemplate.named("Scout")
                .module(new uz.dukeengine.core.module.ActiveBody.Data(50f))
                .visionRange(20f)
                .build();
        thingFactory.addTemplate(scout);
        logic = new TestLogic(thingFactory);
        logic.init();
        usa = logic.getPlayerList().addPlayer("USA").getIndex();
        china = logic.getPlayerList().addPlayer("China").getIndex();
    }

    private GameObject spawn(int player, float x) {
        var o = logic.createObject(scout);
        o.setPlayerIndex(player);
        o.setPosition(new Coord3D(x, 0f, 0f));
        return o;
    }

    @Test
    void enemyWithinVisionIsSeen() {
        spawn(usa, 0f);
        var nearEnemy = spawn(china, 15f); // within vision 20
        assertTrue(logic.canSee(usa, nearEnemy));
    }

    @Test
    void enemyBeyondVisionIsHidden() {
        spawn(usa, 0f);
        var farEnemy = spawn(china, 100f); // beyond vision 20
        assertFalse(logic.canSee(usa, farEnemy));
    }

    @Test
    void ownUnitsAreAlwaysVisible() {
        var own = spawn(usa, 1000f); // far from any other unit
        assertTrue(logic.canSee(usa, own));
    }

    @Test
    void alliesShareVision() {
        var usaScout = spawn(usa, 0f);
        var enemy = spawn(china, 15f);
        // A third player allied to USA sees the enemy through USA's scout.
        var uk = logic.getPlayerList().addPlayer("UK");
        uk.setRelationshipTo(logic.getPlayerList().getPlayer(usa), Relationship.ALLIES);
        assertTrue(logic.canSee(uk.getIndex(), enemy));
        assertTrue(usaScout.isKindOf(Kind.of("OBSTACLE")) == false); // sanity, unrelated
    }

    @Test
    void visibleObjectsListReflectsFog() {
        spawn(usa, 0f);
        spawn(china, 15f);  // visible
        spawn(china, 200f); // hidden
        // USA sees: its own scout + the near enemy == 2.
        assertTrue(logic.getVisibleObjects(usa).size() == 2);
    }
}
