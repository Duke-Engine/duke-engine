package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.thing.RtsKinds;

/**
 * A thing inside a hold sees nothing, as the reference's passenger does not look — a Ranger driven off in a Humvee
 * used to keep the ground he got in at in sight for as long as he rode — but in a garrison it looks from the building.
 */
class HeldSightTest {

    private CombatTest.CombatLogic world;
    private int ours;

    private void world() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Ranger").visionRange(100f).geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build());
        factory.addTemplate(RtsTemplate.named("Humvee").geometry(new Geometry.Box(10f, 6f, 6f))
                .module(new ActiveBody.Data(300f)).module(new MoveUpdate.Data(60f))
                .module(new ContainModule.Data(5)).build());
        factory.addTemplate(RtsTemplate.named("Bunker").kindOf(RtsKinds.STRUCTURE)
                .geometry(new Geometry.Box(20f, 20f, 10f)).module(new ActiveBody.Data(1000f))
                .module(new ContainModule.Data(5, null, false, null, false, null, null, null, true)).build());
        world = new CombatTest.CombatLogic(factory);
        world.init();
        ours = world.getPlayerList().addPlayer("Ours").getIndex();
    }

    private GameObject put(String template, float x, float y) {
        return world.spawn(world.getThingFactory().findTemplate(template), new Coord3D(x, y, 0f), ours);
    }

    @Test
    void aPassengersLoadPointIsFoggedOnceHisTransportHasDrivenAway() {
        world();
        var humvee = put("Humvee", 400f, 400f);
        var ranger = put("Ranger", 400f, 420f);
        var near = new Coord3D(490f, 420f, 0f);
        assertTrue(world.canSee(ours, near), "standing, he sees 90 off");

        humvee.findModule(ContainModule.class).load(ranger);
        humvee.setPosition(new Coord3D(1200f, 400f, 0f)); // driven 800 away
        world.update();
        assertFalse(world.canSee(ours, near), "riding away, where he got in is fogged");

        humvee.findModule(ContainModule.class).unloadAll();
        assertTrue(world.canSee(ours, new Coord3D(1200f, 470f, 0f)), "unloaded, he sees from where he stands");
        assertFalse(world.canSee(ours, near));
    }

    @Test
    void aSoldierInAHoldThatSeesOutClearsTheFogRoundTheHoldWhereverHeGotIn() {
        world();
        var bunker = put("Bunker", 800f, 800f);
        var ranger = put("Ranger", 400f, 420f);

        bunker.findModule(ContainModule.class).load(ranger);
        world.update();

        assertTrue(world.canSee(ours, new Coord3D(880f, 800f, 0f)), "round the bunker, by his 100");
        assertFalse(world.canSee(ours, new Coord3D(490f, 420f, 0f)), "not where he got in");
    }
}
