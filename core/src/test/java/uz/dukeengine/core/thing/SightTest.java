package uz.dukeengine.core.thing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.player.Relationship;

/** A thing's own sight set while the game runs, and the sight of another side's things lent to a player. */
class SightTest {

    private GameLogic world;
    private int viewer;
    private int enemy;
    private int third;

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        factory.addTemplate(ObjectTemplate.named("ViewObject").module(new ActiveBody.Data(1f)).build());
        factory.addTemplate(ObjectTemplate.named("Tank").visionRange(150f).module(new ActiveBody.Data(100f)).build());
        world = new GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        var players = world.getPlayerList();
        viewer = players.addPlayer("Viewer").getIndex();
        enemy = players.addPlayer("Enemy").getIndex();
        third = players.addPlayer("Third").getIndex();
        for (int[] pair : new int[][] {{viewer, enemy}, {viewer, third}, {enemy, third}}) {
            players.getPlayer(pair[0]).setRelationshipTo(players.getPlayer(pair[1]), Relationship.ENEMIES);
            players.getPlayer(pair[1]).setRelationshipTo(players.getPlayer(pair[0]), Relationship.ENEMIES);
        }
    }

    @Test
    void aThingThatSeesNothingSetToSeeTwoHundredAndFiftyRevealsAPointTwoHundredAway() {
        var eye = world.spawn(world.findTemplate("ViewObject"), new Coord3D(0f, 0f, 0f), viewer);
        var point = new Coord3D(200f, 0f, 0f);
        assertFalse(world.canSee(viewer, point), "its template sees nothing");
        long before = world.checksum();

        eye.setVisionRange(250f);
        assertTrue(world.canSee(viewer, point), "a power's view object, 250 for thirty seconds");
        assertNotEquals(before, world.checksum(), "in the checksum");

        eye.setVisionRange(-1f);
        assertFalse(world.canSee(viewer, point), "set back: fogged again");
    }

    @Test
    void anEnemyTanksSightLentByTheGamesRuleIsSeenByTheViewerAndNotByAThirdSide() {
        var tank = world.spawn(world.findTemplate("Tank"), new Coord3D(500f, 500f, 0f), enemy);
        var point = new Coord3D(600f, 500f, 0f);
        assertFalse(world.canSee(viewer, point));

        world.setSharedSight((player, thing) -> player == viewer && thing == tank);

        assertTrue(world.canSee(viewer, point), "the CIA's intelligence: through the enemy's eyes");
        assertFalse(world.canSee(third, point), "and nobody else's");
    }
}
