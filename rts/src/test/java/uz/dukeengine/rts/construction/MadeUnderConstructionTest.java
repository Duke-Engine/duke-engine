package uz.dukeengine.rts.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.RtsModules;
import uz.dukeengine.rts.module.UpgradeListener;

/**
 * A site is made under construction: the side's upgrades, and its own modules, first hear of it as a site — a
 * building put down after Fortified Structure is not drawn fortified while it rises.
 */
class MadeUnderConstructionTest {

    private static final class World extends RtsSimulation {
        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
            if (command instanceof GameMessage.Construct build) {
                construct(build);
            }
        }

        @Override
        protected void simulate() {
        }
    }

    /** What a game's fortifying module hears, and whether its thing was going up each time. */
    static final class Fortify extends Module implements UpgradeListener {
        record Data() implements ModuleData {
        }

        static final List<String> heard = new ArrayList<>();

        Fortify(GameObject owner) {
            super(owner);
        }

        @Override
        public void onUpgrade(String upgrade) {
            heard.add(upgrade + " " + getOwner().hasStatus(ObjectStatus.UNDER_CONSTRUCTION));
        }

        @Override
        public void onCreated() {
            heard.add("made " + getOwner().hasStatus(ObjectStatus.UNDER_CONSTRUCTION));
        }
    }

    @Test
    void theSidesUpgradesAndItsOwnModulesFirstHearOfItAsASite() {
        Fortify.heard.clear();
        var factory = new ThingFactory(RtsModules.withDefaults()
                .register(Fortify.Data.class, (owner, data) -> new Fortify(owner)));
        factory.addTemplate(RtsTemplate.named("Dozer").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build());
        factory.addTemplate(RtsTemplate.named("Barracks").geometry(new Geometry.Box(9f, 9f, 16f))
                .module(new ActiveBody.Data(600f)).module(new Fortify.Data()).buildCost(100).buildTimeFrames(60)
                .build());
        var world = new World(factory);
        world.init();
        world.setPathGrid(new PathGrid(40, 40));
        world.setPlacementRules(new PlacementRules(10f, 30f, 0.5f, 0.1f));
        int player = world.getPlayerList().addPlayer("GLA").getIndex();
        world.getRtsPlayer(player).deposit(1000);
        world.getRtsPlayer(player).addUpgrade("FORTIFIED_STRUCTURE");
        var dozer = world.spawn(factory.findTemplate("Dozer"), new Coord3D(100f, 100f, 0f), player);

        world.issueCommand(new GameMessage.Construct(player, dozer.getId(), "Barracks",
                new Coord3D(205f, 205f, 0f), 0f));
        for (int frame = 0; frame < 600 && Fortify.heard.size() < 2; frame++) {
            world.update();
        }

        assertEquals(List.of("FORTIFIED_STRUCTURE true", "made true"), Fortify.heard,
                "the upgrade reached a site, and so did the telling it was made");
    }
}
