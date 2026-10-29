package uz.dukeengine.rts.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.module.PursueUpdate;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.save.ModuleStates;
import uz.dukeengine.core.save.UnsavedModuleException;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rts.RtsLogic;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.construction.ConstructionSite;
import uz.dukeengine.rts.construction.PlacementRules;
import uz.dukeengine.rts.construction.SellRules;
import uz.dukeengine.rts.construction.Selling;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.AttackMoveOrder;
import uz.dukeengine.rts.module.Dock;
import uz.dukeengine.rts.module.GuardOrder;
import uz.dukeengine.rts.module.HarvestUpdate;
import uz.dukeengine.rts.module.ProductionUpdate;
import uz.dukeengine.rts.module.RtsModules;
import uz.dukeengine.rts.module.SupplyDepot;
import uz.dukeengine.rts.module.SupplyModule;
import uz.dukeengine.rts.thing.RtsKinds;

/**
 * The things a side sets its units to do over many frames — harvesting between docks, an attack-move, a guard, a pursuit,
 * a build, a sale — saved mid-way, loaded, and played on frame for frame as the world they were saved from; and, loaded
 * with their modules made afresh, parting from it. The orders added to a unit while the match ran are made again by
 * their revivers.
 */
class OrdersResumeTest {

    private static RtsLogic rules() {
        return rules(PlacementRules.DEFAULTS);
    }

    /** Harvesters with a pile and a depot to dock at, soldiers, a builder, and a barracks, on open ground 600 across. */
    private static RtsLogic rules(PlacementRules placing) {
        var logic = new RtsLogic();
        var factory = logic.getThingFactory();
        factory.addTemplate(RtsTemplate.named("Truck").geometry(new Geometry.Box(17f, 7f, 14f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(60f))
                .module(new HarvestUpdate.Data(100, 20, 0f, 10, 0, 0, false, List.of(), 5, 5)).build());
        factory.addTemplate(RtsTemplate.named("Pile").geometry(new Geometry.Box(30f, 30f, 10f))
                .module(new SupplyModule.Data(100_000, new Dock(2))).build());
        factory.addTemplate(RtsTemplate.named("Depot").geometry(new Geometry.Box(30f, 30f, 20f))
                .module(new SupplyDepot.Data(new Dock(1))).build());
        factory.addTemplate(RtsTemplate.named("Soldier").geometry(new Geometry.Cylinder(5f, 10f)).visionRange(120f)
                .module(new ActiveBody.Data(200f)).module(new MoveUpdate.Data(40f))
                .module(new WeaponUpdate.Data(10f, 40f, 10, DamageType.NORMAL))
                .module(new PursueUpdate.Data(0f, 0)).buildCost(50).buildTimeFrames(60).build());
        factory.addTemplate(RtsTemplate.named("Factory").kindOf(RtsKinds.STRUCTURE)
                .geometry(new Geometry.Box(30f, 30f, 20f)).module(new ActiveBody.Data(1000f))
                .module(new ProductionUpdate.Data(List.of("Soldier"))).build());
        for (var chaser : List.of(new Object[] {"Hound", 150f, 15}, new Object[] {"Courser", 0f, 20})) {
            factory.addTemplate(RtsTemplate.named((String) chaser[0]).geometry(new Geometry.Cylinder(5f, 10f))
                    .visionRange(120f).module(new ActiveBody.Data(200f)).module(new MoveUpdate.Data(40f))
                    .module(new WeaponUpdate.Data(10f, 40f, 10, DamageType.NORMAL))
                    .module(new PursueUpdate.Data((Float) chaser[1], (Integer) chaser[2])).build());
        }
        factory.addTemplate(RtsTemplate.named("Runner").geometry(new Geometry.Cylinder(5f, 10f))
                .module(new ActiveBody.Data(1_000_000f)).module(new MoveUpdate.Data(30f)).build());
        factory.addTemplate(RtsTemplate.named("Dozer").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(60f)).build());
        factory.addTemplate(RtsTemplate.named("Barracks").kindOf(RtsKinds.STRUCTURE)
                .geometry(new Geometry.Box(20f, 20f, 16f)).module(new ActiveBody.Data(600f)).buildCost(100)
                .buildTimeFrames(300).build());
        logic.init();
        logic.setPathGrid(new PathGrid(60, 60));
        logic.setPlacementRules(placing);
        return logic;
    }

    private static void sides(RtsLogic logic) {
        var red = logic.getPlayerList().addPlayer("Red");
        var blue = logic.getPlayerList().addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        logic.getRtsPlayer(1).deposit(1000);
    }

    private static GameObject put(RtsLogic logic, String template, int side, float x, float y) {
        return logic.spawn(logic.getThingFactory().findTemplate(template), new Coord3D(x, y, 0f), side);
    }

    private static GameObject thing(RtsLogic logic, int id) {
        return logic.findObject(new ObjectId(id));
    }

    private static List<ObjectId> ids(int... values) {
        var ids = new ArrayList<ObjectId>();
        for (int value : values) {
            ids.add(new ObjectId(value));
        }
        return ids;
    }

    private static final BiConsumer<RtsLogic, Integer> NO_ORDERS = (logic, frame) -> {
    };

    /** The world {@code setup} makes of {@code rules}, played {@code frames} frames: what a save then would find. */
    private static RtsLogic playedTo(RtsLogic logic, Consumer<RtsLogic> setup, BiConsumer<RtsLogic, Integer> orders,
            int frames) {
        setup.accept(logic);
        for (int frame = 0; frame < frames; frame++) {
            orders.accept(logic, logic.getFrame());
            logic.update();
        }
        return logic;
    }

    private static <M> M module(GameObject thing, Class<M> type) {
        for (var module : thing.getModules()) {
            if (type.isInstance(module)) {
                return type.cast(module);
            }
        }
        return null;
    }

    // ---- harvesting ----

    /** Five trucks, a pile that lets two wait and one in, a depot that lets one wait and one in, 200 apart. */
    private static final Consumer<RtsLogic> HARVESTING = logic -> {
        sides(logic);
        put(logic, "Pile", 1, 300f, 420f);
        put(logic, "Depot", 1, 300f, 180f);
        for (int one = 0; one < 5; one++) {
            put(logic, "Truck", 1, 250f + 25f * one, 300f);
        }
    };

    @Test
    void harvestersMidLoopBetweenADockedPileAndDepotGoOnTheirTripsAlike() {
        var halfway = playedTo(rules(), HARVESTING, NO_ORDERS, 200);
        assertTrue(halfway.getRtsPlayer(1).getMoney() > 1000, "a load has been banked already");
        var carrying = 0;
        for (int truck = 3; truck <= 7; truck++) {
            carrying += thing(halfway, truck).findModule(HarvestUpdate.class).getCarrying() > 0 ? 1 : 0;
        }
        assertTrue(carrying > 0 && carrying < 5, "some are on their way home loaded and some are not: " + carrying);
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, HARVESTING, NO_ORDERS, 200, 400);
    }

    @Test
    void harvestersSavedWaitingTheirTurnAtADockKeepThePlacesTheyHeld() {
        // Saved as the first trip's crowd stands at the pile: one let in, the rest waiting by it.
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, HARVESTING, NO_ORDERS, 90, 300);
    }

    @Test
    void harvestersToldWhereToWorkAndThenOrderedElsewhereResumeAsTheyWere() {
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, logic -> {
            HARVESTING.accept(logic);
            thing(logic, 3).findModule(HarvestUpdate.class).workAt(thing(logic, 2));
            thing(logic, 4).findModule(HarvestUpdate.class).setSearchRange(250f);
        }, (logic, frame) -> {
            if (frame == 40) {
                logic.issueCommand(new CombatOrder.MoveTo(1, ids(5), new Coord3D(100f, 100f, 0f), true, false));
            }
        }, 150, 300);
    }

    // ---- attack-move and guard ----

    /** Four soldiers of Red sent on an attack-move across the map, through four of Blue's that stand in the way. */
    private static final Consumer<RtsLogic> ATTACK_MOVE = logic -> {
        sides(logic);
        for (int one = 0; one < 4; one++) {
            put(logic, "Soldier", 1, 60f, 230f + 15f * one);
        }
        for (int one = 0; one < 4; one++) {
            put(logic, "Soldier", 2, 300f, 230f + 15f * one);
        }
    };

    private static final BiConsumer<RtsLogic, Integer> SENT_ACROSS = (logic, frame) -> {
        if (frame == 0) {
            logic.issueCommand(new GameMessage.AttackMove(1, ids(1, 2, 3, 4), new Coord3D(520f, 250f, 0f)));
        }
    };

    @Test
    void anAttackMoveThatHasTakenOnAnEnemyChasesItAndGoesOnToItsPointAlike() {
        var engaged = playedTo(rules(), ATTACK_MOVE, SENT_ACROSS, 150);
        assertTrue(engaged.findObject(new ObjectId(5)).getBody().getHealth() < 200f
                || engaged.findObject(new ObjectId(1)).getBody().getHealth() < 200f, "the two sides are at it");
        assertNotNull(module(thing(engaged, 1), AttackMoveOrder.class), "and it is an attack-move that does it");
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, ATTACK_MOVE, SENT_ACROSS, 150, 500);
    }

    @Test
    void anAttackMoveSavedMidWalkAndAnotherMidFightBothResume() {
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, ATTACK_MOVE, SENT_ACROSS, 60, 300);
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, ATTACK_MOVE, SENT_ACROSS, 300, 300);
    }

    private static final Consumer<RtsLogic> GUARD = logic -> {
        sides(logic);
        put(logic, "Soldier", 1, 200f, 250f);
        put(logic, "Soldier", 1, 215f, 250f);
        put(logic, "Soldier", 2, 200f, 400f);
    };

    /** The two soldiers guard a place; Blue's walks up to them, then away when hurt, drawing them off. */
    private static BiConsumer<RtsLogic, Integer> guarding(GameMessage.Guard.Mode mode) {
        return (logic, frame) -> {
            if (frame == 0) {
                logic.issueCommand(new GameMessage.Guard(1, ids(1, 2), new Coord3D(200f, 250f, 0f), null, mode));
                logic.issueCommand(new CombatOrder.MoveTo(2, ids(3), new Coord3D(200f, 330f, 0f), true, false));
            }
            if (frame == 90) {
                logic.issueCommand(new CombatOrder.MoveTo(2, ids(3), new Coord3D(200f, 580f, 0f), true, false));
            }
        };
    }

    @Test
    void aGuardOfAPlaceSavedWhileChasingChasesOnAndWalksBackAlike() {
        var chasing = playedTo(rules(), GUARD, guarding(GameMessage.Guard.Mode.NORMAL), 170);
        var guard = module(thing(chasing, 1), GuardOrder.class);
        assertNotNull(guard.getTarget(), "it has taken the walker on");
        assertFalse(guard.isReturning());
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, GUARD, guarding(GameMessage.Guard.Mode.NORMAL),
                170, 300);
    }

    @Test
    void aGuardOfAPlaceSavedWhileWalkingBackKeepsItsPointAndItsLooks() {
        var returning = playedTo(rules(), GUARD, guarding(GameMessage.Guard.Mode.NORMAL), 300);
        var guard = module(thing(returning, 1), GuardOrder.class);
        assertTrue(guard.isReturning(), "it has let the walker go and is on its way back");
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, GUARD, guarding(GameMessage.Guard.Mode.NORMAL),
                300, 300);
    }

    @Test
    void aGuardWithoutPursuitAndAGuardAgainstTheAirResumeAlike() {
        for (var mode : List.of(GameMessage.Guard.Mode.WITHOUT_PURSUIT, GameMessage.Guard.Mode.FLYING_ONLY)) {
            SaveLoadCheck.resumes(OrdersResumeTest::rules, GUARD, guarding(mode), 120, 200);
        }
    }

    @Test
    void aGuardOfAThingFollowsItWhereverItWalksAndWhereverItWasLastSeen() {
        Consumer<RtsLogic> setup = logic -> {
            GUARD.accept(logic);
            put(logic, "Dozer", 1, 200f, 200f); // thing 4, the one guarded
        };
        BiConsumer<RtsLogic, Integer> orders = (logic, frame) -> {
            if (frame == 0) {
                logic.issueCommand(new GameMessage.Guard(1, ids(1, 2), null, new ObjectId(4),
                        GameMessage.Guard.Mode.NORMAL));
                logic.issueCommand(new CombatOrder.MoveTo(1, ids(4), new Coord3D(450f, 200f, 0f), true, false));
                logic.issueCommand(new CombatOrder.MoveTo(2, ids(3), new Coord3D(330f, 250f, 0f), true, false));
            }
            if (frame == 140) {
                thing(logic, 4).getBody().damage(10_000f); // gone: they keep to where it was last seen
            }
        };
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, setup, orders, 100, 300);
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, setup, orders, 200, 300);
    }

    // ---- pursuit ----

    /** Two hounds on a leash of 150 and two coursers on none, set on a runner that is slower and has a start. */
    private static final Consumer<RtsLogic> HUNT = logic -> {
        sides(logic);
        put(logic, "Hound", 1, 60f, 100f);
        put(logic, "Hound", 1, 60f, 115f);
        put(logic, "Courser", 1, 60f, 130f);
        put(logic, "Courser", 1, 60f, 145f);
        put(logic, "Runner", 2, 200f, 120f);
    };

    private static final BiConsumer<RtsLogic, Integer> GIVEN_CHASE = (logic, frame) -> {
        if (frame == 0) {
            logic.issueCommand(new CombatOrder.AttackObject(1, ids(1, 2, 3, 4), new ObjectId(5), true));
            logic.issueCommand(new CombatOrder.MoveTo(2, ids(5), new Coord3D(560f, 300f, 0f), true, false));
        }
    };

    @Test
    void armedMoversChasingAFleeingTargetKeepTheirLeashesAndTheirRoutesAlike() {
        var chasing = playedTo(rules(), HUNT, GIVEN_CHASE, 80);
        assertTrue(thing(chasing, 3).findModule(PursueUpdate.class).plans() > 0, "the courser has planned a route");
        assertTrue(thing(chasing, 3).getPosition().x() > 100f, "and is off after the runner");
        var strayed = playedTo(rules(), HUNT, GIVEN_CHASE, 140);
        assertNull(thing(strayed, 1).findModule(WeaponUpdate.class).getTarget(), "the hound let go past its leash");
        assertNotNull(thing(strayed, 3).findModule(WeaponUpdate.class).getTarget(), "the courser has no leash");
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, HUNT, GIVEN_CHASE, 80, 300);
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, HUNT, GIVEN_CHASE, 140, 300);
    }

    // ---- building and selling ----

    private static final PlacementRules PLACING = new PlacementRules(10f, 30f, 0.5f, 0.1f);

    /** Two builders of Red's, the first far from where it is told to build, the second near the site. */
    private static final Consumer<RtsLogic> BUILDERS = logic -> {
        sides(logic);
        put(logic, "Dozer", 1, 60f, 60f);
        put(logic, "Dozer", 1, 300f, 150f);
    };

    private static final BiConsumer<RtsLogic, Integer> BUILD = (logic, frame) -> {
        if (frame == 0) {
            logic.issueCommand(new GameMessage.Construct(1, new ObjectId(1), "Barracks", new Coord3D(300f, 300f, 0f), 0f));
        }
    };

    private static ConstructionSite siteIn(RtsLogic logic) {
        return logic.getObjects().stream().map(one -> one.findModule(ConstructionSite.class))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    @Test
    void aBuilderSavedOnItsWayToPutASiteDownPutsItDownWhereAndWhenItWould() {
        var walking = playedTo(rules(PLACING), BUILDERS, BUILD, 60);
        assertNull(siteIn(walking), "the site is put down only when the builder arrives");
        assertNotNull(module(thing(walking, 1), uz.dukeengine.rts.construction.BuildOrder.class));
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(PLACING), BUILDERS, BUILD, 60, 450);
    }

    @Test
    void aBuilderSavedOnItsWayToASiteStandingAlreadyReachesItAndBuildsItAlike() {
        var placing = PLACING.siteAtOrder(true);
        var walking = playedTo(rules(placing), BUILDERS, BUILD, 60);
        assertNotNull(siteIn(walking), "the site stands from the order");
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(placing), BUILDERS, BUILD, 60, 450);
    }

    @Test
    void aSiteSavedHalfBuiltIsGoneOnWithByItsBuilderAndRisesOnTheFrameItWould() {
        var building = playedTo(rules(PLACING), BUILDERS, BUILD, 250);
        assertEquals(1f / 3f, siteIn(building).progress(), 0.01f);
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(PLACING), BUILDERS, BUILD, 250, 300);
    }

    /** The first builder is called away at frame 200 and the site waits half built; the second is sent to take it up. */
    private static final BiConsumer<RtsLogic, Integer> TAKEN_UP = (logic, frame) -> {
        BUILD.accept(logic, frame);
        if (frame == 200) {
            logic.issueCommand(new CombatOrder.MoveTo(1, ids(1), new Coord3D(60f, 300f, 0f), true, false));
        }
        if (frame == 232) {
            logic.issueCommand(new GameMessage.ResumeConstruction(1, new ObjectId(2), siteIn(logic).getOwner().getId()));
        }
    };

    @Test
    void aSiteLeftHalfBuiltIsTakenUpByAnotherBuilderOrderedAfterTheLoad() {
        var waiting = playedTo(rules(PLACING), BUILDERS, TAKEN_UP, 230);
        assertTrue(siteIn(waiting).progress() > 0.15f && siteIn(waiting).progress() < 0.25f, "half built, and left");
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(PLACING), BUILDERS, TAKEN_UP, 230, 450);
    }

    @Test
    void aBuilderWalkingToATakenUpSiteIsSavedWithItsOrderAndFinishesIt() {
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(PLACING), BUILDERS, TAKEN_UP, 262, 450);
        var finished = playedTo(rules(PLACING), BUILDERS, TAKEN_UP, 262 + 450);
        assertFalse(siteIn(finished).getOwner().hasStatus(ObjectStatus.UNDER_CONSTRUCTION), "the second one finished it");
    }

    @Test
    void aSaleSavedWhileTheBuildingComesDownGoesOnDownAndPaysItsRefundOnTheFrameItWould() {
        Consumer<RtsLogic> setup = logic -> {
            sides(logic);
            put(logic, "Barracks", 1, 300f, 300f);
        };
        BiConsumer<RtsLogic, Integer> orders = (logic, frame) -> {
            if (frame == 10) {
                logic.issueCommand(new GameMessage.Sell(1, new ObjectId(1)));
            }
        };
        var coming = playedTo(rules(), setup, orders, 100);
        assertTrue(thing(coming, 1).hasStatus(ObjectStatus.SOLD));
        assertTrue(module(thing(coming, 1), Selling.Coming.class).share() < 99f, "and it is on its way down");
        SaveLoadCheck.resumesOnlyWithItsModules(OrdersResumeTest::rules, setup, orders, 100, 150);
        assertEquals(1050, playedTo(rules(), setup, orders, 250).getRtsPlayer(1).getMoney(), "half its cost came back");
    }

    @Test
    void ordersBuiltOnRulesTheWorldDoesNotHoldAreRefusedByName() {
        var logic = rules(PLACING);
        BUILDERS.accept(logic);
        var other = new PlacementRules(10f, 30f, 0.25f, 0.2f).siteAtOrder(true);
        assertTrue(uz.dukeengine.rts.construction.Construction.order(logic,
                new GameMessage.Construct(1, new ObjectId(1), "Barracks", new Coord3D(300f, 300f, 0f), 0f), other));
        var factory = logic.getThingFactory().getModuleFactory();
        var error = assertThrows(UnsavedModuleException.class, () -> ModuleStates.save(thing(logic, 1), factory));
        assertEquals("uz.dukeengine.rts.construction.BuildOrder", error.module());
        var site = logic.getObjects().stream().filter(one -> one.findModule(ConstructionSite.class) != null).findFirst()
                .orElseThrow();
        assertEquals("uz.dukeengine.rts.construction.ConstructionSite", assertThrows(UnsavedModuleException.class,
                () -> ModuleStates.save(site, factory)).module());

        var selling = rules(PLACING);
        sides(selling);
        put(selling, "Barracks", 1, 300f, 300f);
        selling.issueCommand(new GameMessage.Sell(1, new ObjectId(1)));
        selling.update();
        ModuleStates.save(thing(selling, 1), selling.getThingFactory().getModuleFactory()); // the world's own: written
        selling.setSellRules(new SellRules(10, 5f, 0.9f)); // the game changed them while it came down
        assertEquals("uz.dukeengine.rts.construction.Selling$Coming", assertThrows(UnsavedModuleException.class,
                () -> ModuleStates.save(thing(selling, 1), selling.getThingFactory().getModuleFactory())).module());
    }

    @Test
    void everyOrderAndSiteTheEngineAddsWhileTheMatchRunsHasAReviverAndNoTemplateMakesItAfresh() {
        var factory = RtsModules.withDefaults();
        for (var type : List.of(AttackMoveOrder.class, GuardOrder.class, uz.dukeengine.rts.construction.BuildOrder.class,
                ConstructionSite.class, Selling.Coming.class)) {
            assertNotNull(factory.reviverFor(type.getName()), type.getName() + " is made again from its save");
        }
    }

    // ---- all at once ----

    /** A harvest, a factory, an attack-move, a guard, a build and a sale in one match, about to meet. */
    private static final Consumer<RtsLogic> BATTLE = logic -> {
        sides(logic);
        put(logic, "Pile", 1, 300f, 420f); // 1
        put(logic, "Depot", 1, 300f, 180f); // 2
        for (int one = 0; one < 3; one++) {
            put(logic, "Truck", 1, 250f + 25f * one, 300f); // 3 to 5
        }
        put(logic, "Factory", 1, 450f, 100f); // 6
        put(logic, "Soldier", 1, 60f, 500f); // 7
        put(logic, "Soldier", 1, 60f, 515f); // 8
        put(logic, "Soldier", 2, 400f, 520f); // 9
        put(logic, "Soldier", 2, 400f, 535f); // 10
        put(logic, "Soldier", 1, 100f, 400f); // 11
        put(logic, "Soldier", 1, 115f, 400f); // 12
        put(logic, "Soldier", 2, 100f, 520f); // 13
        put(logic, "Dozer", 1, 100f, 100f); // 14
        put(logic, "Barracks", 1, 520f, 400f); // 15
    };

    private static final BiConsumer<RtsLogic, Integer> BATTLE_ORDERS = (logic, frame) -> {
        switch (frame) {
            case 0 -> {
                for (int one = 0; one < 3; one++) {
                    logic.issueCommand(new GameMessage.QueueProduction(1, new ObjectId(6), "Soldier"));
                }
                logic.issueCommand(new GameMessage.AttackMove(1, ids(7, 8), new Coord3D(500f, 525f, 0f)));
                logic.issueCommand(new GameMessage.Guard(1, ids(11, 12), new Coord3D(100f, 400f, 0f), null,
                        GameMessage.Guard.Mode.NORMAL));
                logic.issueCommand(new CombatOrder.MoveTo(2, ids(13), new Coord3D(100f, 330f, 0f), true, false));
                logic.issueCommand(new GameMessage.Construct(1, new ObjectId(14), "Barracks",
                        new Coord3D(200f, 100f, 0f), 0f));
            }
            case 60 -> logic.issueCommand(new GameMessage.Sell(1, new ObjectId(15)));
            case 90 -> logic.issueCommand(new CombatOrder.MoveTo(2, ids(13), new Coord3D(100f, 580f, 0f), true, false));
            default -> {
            }
        }
    };

    @Test
    void aMixedBattleOfProductionHarvestAttackMoveGuardBuildAndSaleResumesForThreeHundredFrames() {
        var midway = playedTo(rules(PLACING), BATTLE, BATTLE_ORDERS, 120);
        for (var type : List.of(AttackMoveOrder.class, GuardOrder.class, uz.dukeengine.rts.construction.BuildOrder.class,
                Selling.Coming.class, ProductionUpdate.class, HarvestUpdate.class)) {
            assertTrue(midway.getObjects().stream().anyMatch(one -> module(one, type) != null),
                    type.getSimpleName() + " is at work at the save");
        }
        SaveLoadCheck.resumesOnlyWithItsModules(() -> rules(PLACING), BATTLE, BATTLE_ORDERS, 120, 300);
    }
}
