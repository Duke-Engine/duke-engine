package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.thing.RtsKinds;
import uz.dukeengine.combat.module.WeaponUpdate;

/**
 * A thing's fog range apart from its sight, as the reference keeps its ShroudClearingRange apart from its VisionRange:
 * a Ranger looks for targets 100 round and clears the fog 400 round; a site clears only itself; a superweapon is seen
 * by everyone near it.
 */
class FogRangeTest {

    private static final class World extends RtsSimulation {
        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
            if (command instanceof GameMessage.Guard guard) {
                GuardOrder.order(this, guard);
            }
        }

        @Override
        protected void simulate() {
        }
    }

    private World world;
    private int ours;
    private int theirs;

    private void world() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Ranger").visionRange(100f).fogRange(400f)
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f))
                .module(new WeaponUpdate.Data(10f, 100f, 15, DamageType.NORMAL)).build());
        factory.addTemplate(RtsTemplate.named("Scout").module(new ActiveBody.Data(10_000f)).build());
        factory.addTemplate(RtsTemplate.named("StrategyCenter").kindOf(RtsKinds.STRUCTURE).visionRange(100f)
                .fogRange(400f).geometry(new Geometry.Box(30f, 20f, 20f)).module(new ActiveBody.Data(1000f))
                .build());
        factory.addTemplate(RtsTemplate.named("ScudStorm").kindOf(RtsKinds.STRUCTURE).visionRange(100f)
                .seenByAllWithin(60f).geometry(new Geometry.Box(20f, 20f, 20f)).module(new ActiveBody.Data(1000f))
                .build());
        world = new World(factory);
        world.init();
        ours = world.getPlayerList().addPlayer("Ours").getIndex();
        theirs = world.getPlayerList().addPlayer("Theirs").getIndex();
        world.getPlayerList().getPlayer(ours).setRelationshipTo(world.getPlayerList().getPlayer(theirs),
                Relationship.ENEMIES);
        world.getPlayerList().getPlayer(theirs).setRelationshipTo(world.getPlayerList().getPlayer(ours),
                Relationship.ENEMIES);
    }

    private GameObject put(String template, int side, float x, float y) {
        return world.spawn(world.getThingFactory().findTemplate(template), new Coord3D(x, y, 0f), side);
    }

    @Test
    void aThingClearsTheFogByItsFogRangeAndLooksForTargetsByItsSight() {
        world();
        var ranger = put("Ranger", ours, 500f, 500f);
        var enemy = put("Scout", theirs, 800f, 500f);

        assertTrue(world.canSee(ours, new Coord3D(800f, 500f, 0f)), "a point 300 away is clear");
        assertTrue(world.canSee(ours, enemy), "and the enemy on it seen");
        world.issueCommand(new GameMessage.Guard(ours, List.of(ranger.getId()), new Coord3D(500f, 500f, 0f), null,
                GameMessage.Guard.Mode.NORMAL));
        for (int frame = 0; frame < 30; frame++) {
            world.update();
        }
        assertNull(ranger.findModule(GuardOrder.class).getTarget(), "its guard looks 100 round, not 300");

        ranger.setFogRange(50f);
        assertFalse(world.canSee(ours, new Coord3D(800f, 500f, 0f)), "set to 50, the point is fogged again");
        assertEquals(50f, ranger.getFogRange());
    }

    @Test
    void aSiteClearsOnlyItsFootprintUntilItIsFinished() {
        world();
        var site = put("StrategyCenter", ours, 500f, 500f);
        site.setStatus(ObjectStatus.UNDER_CONSTRUCTION);

        assertTrue(world.canSee(ours, new Coord3D(530f, 500f, 0f)), "over itself");
        assertFalse(world.canSee(ours, new Coord3D(600f, 500f, 0f)), "and no further while it goes up");

        site.clearStatus(ObjectStatus.UNDER_CONSTRUCTION);
        assertTrue(world.canSee(ours, new Coord3D(850f, 500f, 0f)), "finished, 400 round");
    }

    @Test
    void aThingSeenByAllWithin60IsSeenByAnEnemyNearItOnceFinished() {
        world();
        var scud = put("ScudStorm", ours, 500f, 500f);

        assertTrue(world.canSee(theirs, scud), "the enemy sees it");
        assertTrue(world.canSee(theirs, new Coord3D(550f, 500f, 0f)), "and 50 round it");
        assertFalse(world.canSee(theirs, new Coord3D(570f, 500f, 0f)), "not 70");

        scud.setStatus(ObjectStatus.UNDER_CONSTRUCTION);
        assertFalse(world.canSee(theirs, scud), "not while it is being built");
    }

    /** Written in its block; a block that writes no fog range clears the fog by its sight. */
    @Test
    void theFogRangeIsWrittenInTheBlockAndIsItsSightWhereItWritesNone() {
        var loaded = RtsTemplate.register(new uz.dukeengine.core.thing.ThingTemplateLoader(
                new ThingFactory(RtsModules.withDefaults()))).load("""
                Object
                  Name = Crusader
                  VisionRange = 150
                  FogRange = 300
                End
                Object
                  Name = ParticleCannon
                  VisionRange = 200
                  SeenByAllWithin = 60
                End
                """, "things.duke");
        var crusader = (RtsTemplate) loaded.get(0);
        var cannon = (RtsTemplate) loaded.get(1);

        assertEquals(300f, crusader.fogRange());
        assertEquals(-1f, cannon.fogRange(), "none written: its sight");
        assertEquals(60f, cannon.seenByAllWithin());
    }
}
