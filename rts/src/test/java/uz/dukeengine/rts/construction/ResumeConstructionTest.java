package uz.dukeengine.rts.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.network.CommandPacket;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.RtsModules;
import uz.dukeengine.rts.network.CommandCodec;

/**
 * Another builder of the side takes up a half-built site — the reference's resume construction ({@code
 * DozerAIUpdate::privateResumeConstruction}, {@code ActionManager::canResumeConstructionOf}): the site rises for it
 * once it stands beside it, and one builder at a time.
 */
class ResumeConstructionTest {

    private static final int BUILD_FRAMES = 300;

    private static final class World extends RtsSimulation {
        World(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
            switch (command) {
                case GameMessage.Construct build -> construct(build);
                case GameMessage.ResumeConstruction resume -> resumeConstruction(resume);
                default -> {
                }
            }
        }

        @Override
        protected void simulate() {
        }
    }

    private record Scene(World world, int player, GameObject first, GameObject second) {
        GameObject site() {
            return world.getObjects().stream().filter(one -> one.getTemplate().name().equals("Barracks"))
                    .findFirst().orElseThrow();
        }

        float progress() {
            return site().findModule(ConstructionSite.class).progress();
        }

        void resume(GameObject builder) {
            world.issueCommand(new GameMessage.ResumeConstruction(player, builder.getId(), site().getId()));
        }
    }

    private static Scene scene() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Dozer").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build());
        factory.addTemplate(RtsTemplate.named("Barracks").geometry(new Geometry.Box(20f, 20f, 16f))
                .module(new ActiveBody.Data(600f)).buildCost(100).buildTimeFrames(BUILD_FRAMES).build());
        var world = new World(factory);
        world.init();
        world.setPathGrid(new PathGrid(60, 60));
        world.setPlacementRules(new PlacementRules(10f, 30f, 0.5f, 0.1f));
        int player = world.getPlayerList().addPlayer("Builder").getIndex();
        world.getRtsPlayer(player).deposit(1000);
        var first = world.createObject(factory.findTemplate("Dozer"));
        first.setPlayerIndex(player);
        first.setPosition(new Coord3D(180f, 300f, 0f));
        var second = world.createObject(factory.findTemplate("Dozer"));
        second.setPlayerIndex(player);
        second.setPosition(new Coord3D(300f, 150f, 0f));
        world.issueCommand(new GameMessage.Construct(player, first.getId(), "Barracks", new Coord3D(210f, 300f, 0f),
                0f));
        return new Scene(world, player, first, second);
    }

    @Test
    void anotherBuilderFinishesASiteWhoseBuilderWasKilledInTheWorkLeftFromWhenItStandsBesideIt() {
        var scene = scene();
        var world = scene.world();
        while (world.getObjects().stream().noneMatch(one -> one.getTemplate().name().equals("Barracks"))
                || scene.progress() < 0.3f) {
            world.update();
        }
        scene.first().getBody().setHealth(0f); // killed at 30%
        world.update();
        world.update();
        assertEquals(0.3f, scene.progress(), 0.01f, "it waits as it is");

        scene.resume(scene.second());
        int besideAt = -1;
        int wholeAt = -1;
        for (int frame = 0; frame < 900 && wholeAt < 0; frame++) {
            world.update();
            if (besideAt < 0 && world.isBeside(scene.second(), scene.site())) {
                besideAt = frame;
            }
            if (!scene.site().hasStatus(ObjectStatus.UNDER_CONSTRUCTION)) {
                wholeAt = frame;
            }
        }
        assertTrue(besideAt >= 0 && wholeAt >= 0, "it came and finished it");
        assertEquals(Math.round(BUILD_FRAMES * 0.7f), wholeAt - besideAt, 2, "in 70% of its build time from there");
    }

    @Test
    void aSecondBuilderIsRefusedWhileTheFirstWorksOnIt() {
        var scene = scene();
        var world = scene.world();
        while (world.getObjects().stream().noneMatch(one -> one.getTemplate().name().equals("Barracks"))) {
            world.update();
        }
        world.update();
        scene.resume(scene.second());
        world.update();
        assertFalse(scene.second().findModule(MoveUpdate.class).isMoving(), "refused: it did not set off");
        assertEquals(scene.first().getId(), scene.site().findModule(ConstructionSite.class).builder());
    }

    @Test
    void theOrderGoesOverTheWire() {
        var packet = new CommandPacket(3, 1, List.of(
                new GameMessage.ResumeConstruction(1, new ObjectId(7), new ObjectId(12))));
        assertEquals(packet, CommandCodec.INSTANCE.decode(CommandCodec.INSTANCE.encode(packet)));
    }
}
