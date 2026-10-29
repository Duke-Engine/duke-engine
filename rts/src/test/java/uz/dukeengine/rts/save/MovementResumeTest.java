package uz.dukeengine.rts.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.MoveUpdate.Gait;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.TraversalLink;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.save.ModuleStates;
import uz.dukeengine.core.save.UnsavedModuleException;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.RtsLogic;
import uz.dukeengine.rts.RtsTemplate;

/**
 * Ground movers saved mid-walk — round stone, in a group, giving way in a corridor, braking and turning on treads and
 * wheels, on a locomotor the game gave them, waiting their turn for a route, over a deck — loaded, and walked on frame
 * for frame as the world they were saved from; and, loaded with their legs made afresh, parting from it.
 */
class MovementResumeTest {

    private static MoveUpdate.Data moving(float speed, float turnRate, float acceleration, float braking,
            boolean backwards, Gait gait) {
        return new MoveUpdate.Data(speed, turnRate, acceleration, braking, 0f, 0f, 0.1f, 5f, 8f, 1f, backwards, gait);
    }

    /** Infantry on legs; a box on treads; one on wheels that backs; a thing moving as things always moved; stone. */
    private static RtsLogic rules(Consumer<PathGrid> ground, int budget) {
        var logic = new RtsLogic();
        var factory = logic.getThingFactory();
        factory.addTemplate(RtsTemplate.named("Walker").geometry(new Geometry.Cylinder(5f, 10f))
                .module(new ActiveBody.Data(100f)).module(moving(30f, 360f, 60f, 60f, false, Gait.LEGS)).build());
        factory.addTemplate(RtsTemplate.named("Tank").geometry(new Geometry.Box(15f, 10f, 10f))
                .module(new ActiveBody.Data(400f)).module(moving(30f, 90f, 20f, 40f, false, Gait.TREADS)).build());
        factory.addTemplate(RtsTemplate.named("Jeep").geometry(new Geometry.Box(10f, 6f, 6f))
                .module(new ActiveBody.Data(200f)).module(moving(40f, 120f, 30f, 50f, true, Gait.WHEELS)).build());
        factory.addTemplate(RtsTemplate.named("Cart").geometry(new Geometry.Cylinder(6f, 6f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(25f, 180f)).build());
        factory.addTemplate(RtsTemplate.named("Rock").geometry(new Geometry.Cylinder(12f, 20f))
                .module(new ActiveBody.Data(50f)).build());
        logic.init();
        var grid = new PathGrid(60, 60);
        ground.accept(grid);
        logic.setPathGrid(grid);
        logic.setPathfindBudget(budget);
        return logic;
    }

    private static RtsLogic rules() {
        return rules(grid -> {
        }, GameLogic.DEFAULT_PATHFIND_BUDGET);
    }

    private static void sides(RtsLogic logic) {
        var red = logic.getPlayerList().addPlayer("Red");
        var blue = logic.getPlayerList().addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
    }

    private static GameObject put(RtsLogic logic, String template, int side, float x, float y) {
        return logic.spawn(logic.getThingFactory().findTemplate(template), new Coord3D(x, y, 0f), side);
    }

    private static MoveUpdate legs(GameObject thing) {
        return thing.findModule(MoveUpdate.class);
    }

    private static GameObject thing(RtsLogic logic, int id) {
        return logic.findObject(new ObjectId(id));
    }

    /** A wall of rocks down x = 250 from y = 130 to 370, the first rock thing 1. */
    private static List<GameObject> wall(RtsLogic logic) {
        var rocks = new ArrayList<GameObject>();
        for (float y = 130f; y <= 370f; y += 24f) {
            rocks.add(put(logic, "Rock", 0, 250f, y));
        }
        return rocks;
    }

    @Test
    void aWalkerSavedRoundStoneGoesOnRoundIt() {
        SaveLoadCheck.resumesOnlyWithItsModules(MovementResumeTest::rules, logic -> {
            sides(logic);
            wall(logic);
            legs(put(logic, "Walker", 1, 120f, 250f)).moveTo(new Coord3D(420f, 260f, 0f));
            legs(put(logic, "Cart", 1, 120f, 280f)).moveTo(new Coord3D(400f, 200f, 0f));
        }, (logic, frame) -> {
        }, 60, 240);
    }

    @Test
    void aGroupSentWithOneOrderWalksOnInItsPlaces() {
        SaveLoadCheck.resumesOnlyWithItsModules(MovementResumeTest::rules, logic -> {
            sides(logic);
            wall(logic);
            var units = new ArrayList<ObjectId>();
            for (int i = 0; i < 6; i++) {
                units.add(put(logic, "Walker", 1, 80f + 20f * (i % 3), 220f + 20f * (i / 3)).getId());
            }
            for (int i = 0; i < 3; i++) {
                units.add(put(logic, "Tank", 1, 90f + 40f * i, 300f).getId());
            }
            logic.issueCommand(new CombatOrder.MoveTo(1, units, new Coord3D(460f, 250f, 0f), true));
        }, (logic, frame) -> {
            if (frame == 52) {
                thing(logic, 6).getBody().damage(1000f); // every route stale: each plans again, to the block it holds
            }
        }, 45, 300);
    }

    /** Stone everywhere across x = 200 to 410 but a corridor from y = 200 to 240. */
    private static RtsLogic corridor() {
        return rules(grid -> {
            for (int cx = 20; cx <= 40; cx++) {
                for (int cy = 0; cy < 60; cy++) {
                    grid.setBlocked(cx, cy, cy < 20 || cy > 23);
                }
            }
        }, GameLogic.DEFAULT_PATHFIND_BUDGET);
    }

    @Test
    void vehiclesMeetingInACorridorGiveWayAsTheyWouldHave() {
        SaveLoadCheck.resumesOnlyWithItsModules(MovementResumeTest::corridor, logic -> {
            sides(logic);
            put(logic, "Tank", 1, 300f, 220f); // standing in the way: asked aside by the first route through it
            legs(put(logic, "Tank", 1, 150f, 220f)).moveTo(new Coord3D(470f, 220f, 0f));
            legs(put(logic, "Jeep", 1, 120f, 225f)).moveTo(new Coord3D(480f, 230f, 0f));
            legs(put(logic, "Tank", 1, 460f, 220f)).moveTo(new Coord3D(130f, 220f, 0f));
            legs(put(logic, "Walker", 1, 470f, 230f)).moveTo(new Coord3D(120f, 225f, 0f));
        }, (logic, frame) -> {
        }, 160, 400);
    }

    @Test
    void treadsAndWheelsTurningAndBrakingGoOnAtTheSpeedTheyHad() {
        SaveLoadCheck.resumesOnlyWithItsModules(MovementResumeTest::rules, logic -> {
            sides(logic);
            legs(put(logic, "Tank", 1, 300f, 300f)).moveTo(new Coord3D(180f, 320f, 0f)); // behind it: it turns first
            var jeep = put(logic, "Jeep", 1, 300f, 150f);
            legs(jeep).moveTo(new Coord3D(270f, 152f, 0f)); // a little behind: it backs
            legs(put(logic, "Jeep", 1, 100f, 450f)).moveTo(new Coord3D(20f, 440f, 0f)); // far behind: three points
        }, (logic, frame) -> {
            if (frame == 40) {
                legs(thing(logic, 2)).moveTo(new Coord3D(420f, 160f, 0f)); // on again, braking onto the first
            }
        }, 25, 220);
    }

    @Test
    void aLocomotorGivenWhileItWalksIsTheOneItWalksOnBy() {
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(grid -> {
            for (int cx = 22; cx <= 30; cx++) {
                for (int cy = 15; cy <= 35; cy++) {
                    grid.setGroundClass(cx, cy, "RUBBLE");
                }
            }
        }, GameLogic.DEFAULT_PATHFIND_BUDGET), logic -> {
            sides(logic);
            var walker = put(logic, "Walker", 1, 100f, 250f);
            var climbing = moving(12f, 180f, 20f, 20f, false, Gait.LEGS);
            legs(walker).setLocomotor(new MoveUpdate.Data(42f, 270f, 90f, 30f, 0f, 0f, 0.1f, 4f, 0f, 2f, false,
                    Gait.LEGS, 2, MoveUpdate.MovePriority.MIDDLE, List.of("MUD"), Map.of("RUBBLE", climbing)));
            legs(walker).moveTo(new Coord3D(500f, 260f, 0f));
            var cart = put(logic, "Cart", 1, 100f, 300f);
            legs(cart).setSpeed(18f, 45f);
            legs(cart).moveTo(new Coord3D(480f, 320f, 0f));
        }, (logic, frame) -> {
        }, 150, 200); // the walker is on the rubble, walked by its class's locomotor
    }

    /** Ten walkers sent in one frame on a budget one search a frame spends: they get their routes in turn. */
    private static final Consumer<RtsLogic> TEN_SENT = logic -> {
        sides(logic);
        wall(logic);
        var post = put(logic, "Rock", 2, 400f, 400f);
        for (int i = 0; i < 9; i++) {
            legs(put(logic, "Walker", 1, 60f + 15f * i, 100f + 30f * i)).moveTo(new Coord3D(450f, 120f + 30f * i, 0f));
        }
        legs(put(logic, "Walker", 1, 100f, 450f)).moveWithin(post, 0f, 20f);
        post.getBody().damage(1000f); // killed and gone before the one closing on it gets its route
    };

    private static RtsLogic onABudget() {
        return rules(grid -> {
        }, 40);
    }

    @Test
    void walkersWaitingTheirTurnForARouteGetItInTheSameTurn() {
        var played = onABudget();
        TEN_SENT.accept(played);
        for (int frame = 0; frame < 4; frame++) {
            played.update();
        }
        long waiting = played.getObjects().stream()
                .filter(thing -> legs(thing) != null && legs(thing).isWaitingForRoute()).count();
        assertTrue(waiting >= 3, "several still wait their turn at the save: " + waiting);

        SaveLoadCheck.resumesOnlyWithItsModules(MovementResumeTest::onABudget, TEN_SENT, (logic, frame) -> {
        }, 4, 300);
    }

    @Test
    void aWalkerOverADeckGoesOnOverIt() {
        Consumer<PathGrid> river = grid -> {
            for (int cy = 0; cy < 45; cy++) {
                for (int cx = 15; cx < 25; cx++) {
                    grid.setBlocked(cx, cy, true);
                }
            }
        };
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(river, GameLogic.DEFAULT_PATHFIND_BUDGET), logic -> {
            sides(logic);
            logic.addDeck(new Coord3D(130f, 180f, 20f), new Coord3D(130f, 220f, 20f), new Coord3D(270f, 220f, 20f),
                    new Coord3D(270f, 180f, 20f));
            legs(put(logic, "Walker", 1, 60f, 200f)).moveTo(new Coord3D(340f, 200f, 0f));
            legs(put(logic, "Cart", 1, 70f, 160f)).moveTo(new Coord3D(330f, 120f, 0f));
        }, (logic, frame) -> {
        }, 110, 200);
    }

    /** A chasm down x = 250 to 270 all the way across, and a jump over it from (245, 300) to (275, 300). */
    private static RtsLogic chasm() {
        var logic = rules(grid -> {
            for (int cy = 0; cy < 60; cy++) {
                grid.setBlocked(25, cy, true);
                grid.setBlocked(26, cy, true);
            }
        }, GameLogic.DEFAULT_PATHFIND_BUDGET);
        logic.setTraversalLinks(List.of(new TraversalLink(new Coord3D(245f, 300f, 0f), new Coord3D(275f, 300f, 0f),
                "jump", 40, 10f, "Jump", false, false)));
        return logic;
    }

    @Test
    void oneCarriedOverALinkLandsAndOneOnItsWayToItGoesOnToIt() {
        SaveLoadCheck.resumesOnlyWithItsModules(MovementResumeTest::chasm, logic -> {
            sides(logic);
            legs(put(logic, "Cart", 1, 195f, 300f)).moveTo(new Coord3D(400f, 300f, 0f));
            legs(put(logic, "Cart", 1, 60f, 320f)).moveTo(new Coord3D(400f, 340f, 0f));
        }, (logic, frame) -> {
        }, 80, 300);
    }

    @Test
    void stoneTakenAwayTheFrameBeforeTheSaveIsRoutedThroughAfterIt() {
        Consumer<RtsLogic> setup = logic -> {
            sides(logic);
            wall(logic);
            legs(put(logic, "Walker", 1, 120f, 250f)).moveTo(new Coord3D(420f, 250f, 0f));
        };
        java.util.function.BiConsumer<RtsLogic, Integer> orders = (logic, frame) -> {
            if (frame == 29) {
                for (int id = 4; id <= 8; id++) {
                    thing(logic, id).getBody().damage(1000f); // the middle of the wall falls
                }
            }
        };
        var played = rules();
        setup.accept(played);
        for (int frame = 0; frame < 30; frame++) {
            orders.accept(played, frame);
            played.update();
        }
        int version = played.getNavigationVersion();
        played.update();
        assertEquals(version + 1, played.getNavigationVersion(), "the ground is laid again only on the frame after");

        SaveLoadCheck.resumesOnlyWithItsModules(MovementResumeTest::rules, setup, orders, 30, 200);
    }

    @Test
    void aMoverSentBesideAThingIsStillTakenAsBesideItOnceThere() {
        Map<RtsLogic, List<Boolean>> beside = new LinkedHashMap<>(); // a world is equal only to itself
        var gap = new float[1];
        SaveLoadCheck.resumes(MovementResumeTest::rules, logic -> {
            sides(logic);
            var rock = put(logic, "Rock", 0, 300f, 300f);
            var walker = put(logic, "Walker", 1, 100f, 280f);
            // Arriving 8 short of its point, it stands more than a cell off the rock: beside it only as it was sent.
            legs(walker).setLocomotor(new MoveUpdate.Data(30f, 360f, 60f, 60f, 0f, 0f, 0.1f, 5f, 8f, 8f, false,
                    Gait.LEGS));
            legs(walker).moveTo(logic.standingNextTo(walker, rock));
        }, (logic, frame) -> {
            beside.computeIfAbsent(logic, _ -> new ArrayList<>()).add(logic.isBeside(thing(logic, 2), thing(logic, 1)));
            gap[0] = thing(logic, 2).getPosition().distance(thing(logic, 1).getPosition()) - 12f - 5f;
        }, 100, 150);
        var worlds = new ArrayList<>(beside.values());
        var played = worlds.get(0);
        var loaded = worlds.get(1);
        assertEquals(played.subList(played.size() - loaded.size(), played.size()), loaded);
        assertTrue(loaded.getLast(), "beside it, once there");
        assertTrue(gap[0] > 10f, "more than a cell off its outline, so beside it only as it was sent: " + gap[0]);
    }

    @Test
    void aPlaceOneHoldsIsNotGivenToAnotherSentThereAfterTheSave() {
        var place = new Coord3D(300f, 300f, 0f);
        Map<RtsLogic, Coord3D> given = new LinkedHashMap<>();
        SaveLoadCheck.resumes(MovementResumeTest::rules, logic -> {
            sides(logic);
            legs(put(logic, "Tank", 1, 240f, 300f)).moveTo(place);
            put(logic, "Tank", 1, 100f, 300f);
        }, (logic, frame) -> {
            if (frame == 150) {
                legs(thing(logic, 2)).moveTo(place);
                given.put(logic, legs(thing(logic, 2)).getDestination());
            }
        }, 150, 200);
        var blocks = new ArrayList<>(given.values());
        assertEquals(blocks.get(0), blocks.get(1), "the loaded world gives the second the block the saved one does");
        var held = new Coord3D(300f, 300f, 0f);
        assertTrue(Math.abs(blocks.get(1).x() - held.x()) + Math.abs(blocks.get(1).y() - held.y()) > 10f,
                "not the block the first holds: " + blocks.get(1));
    }

    @Test
    void aMoverWalkingByASteeringIsRefused() {
        var logic = rules();
        sides(logic);
        var walker = put(logic, "Walker", 1, 100f, 100f);
        legs(walker).steerBy((mover, step) -> mover.stepStraightTo(mover.getOwner().getPosition()
                .add(new Coord3D(step, 0f, 0f))));
        logic.update();
        var refused = assertThrows(UnsavedModuleException.class,
                () -> ModuleStates.save(walker, logic.getThingFactory().getModuleFactory()));
        assertEquals(MoveUpdate.class.getName(), refused.module());
    }
}
