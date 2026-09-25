package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** Decks laid over the ground at run time: driven over, passed under, closed and opened again. */
class DeckTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private TestLogic logic;
    private ThingTemplate walker;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        walker = ThingTemplate.named("Walker")
                .geometry(new Geometry.Cylinder(4f, 10f))
                .module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(30f))
                .build();
        thingFactory.addTemplate(walker);
        logic = new TestLogic(thingFactory);
        logic.init();
    }

    /** A river ten cells wide down the map, from the bottom to {@code riverEnd}, and a deck 20 over it, bank to bank. */
    private int river(int riverEnd) {
        var grid = new PathGrid(40, 40);
        for (int cy = 0; cy < riverEnd; cy++) {
            for (int cx = 15; cx < 25; cx++) {
                grid.setBlocked(cx, cy, true);
            }
        }
        logic.setPathGrid(grid);
        return logic.addDeck(new Coord3D(130f, 180f, 20f), new Coord3D(130f, 220f, 20f),
                new Coord3D(270f, 220f, 20f), new Coord3D(270f, 180f, 20f));
    }

    /** A valley four cells wide across the map, walls either side, and a deck {@code height} over it. */
    private void valley(float height) {
        var grid = new PathGrid(40, 40);
        for (int cy = 0; cy < 40; cy++) {
            for (int cx = 0; cx < 40; cx++) {
                grid.setBlocked(cx, cy, cy < 18 || cy > 21);
            }
        }
        logic.setPathGrid(grid);
        logic.addDeck(new Coord3D(180f, 150f, height), new Coord3D(220f, 150f, height),
                new Coord3D(220f, 250f, height), new Coord3D(180f, 250f, height));
    }

    private void walk(GameObject unit, java.util.function.Consumer<Coord3D> each) {
        var legs = unit.findModule(MoveUpdate.class);
        for (int frame = 0; frame < 3000 && legs.isMoving(); frame++) {
            logic.update();
            each.accept(unit.getPosition());
        }
    }

    @Test
    void aUnitSentAcrossTheRiverDrivesOverTheDeckAtItsHeight() {
        int deck = river(40);
        var unit = logic.spawn(walker, new Coord3D(50f, 200f, 0f), 1);
        unit.findModule(MoveUpdate.class).moveTo(new Coord3D(350f, 200f, 0f));
        var over = new ArrayList<Coord3D>();

        walk(unit, at -> {
            if (at.x() > 150f && at.x() < 250f) {
                over.add(at);
                assertEquals(deck, unit.getFloor(), "over the river it is on the deck, at " + at);
            }
        });

        assertTrue(!over.isEmpty(), "it crossed");
        over.forEach(at -> assertEquals(20f, at.z(), 1e-3f, "at the deck's height"));
        assertEquals(350f, unit.getPosition().x(), 0.5f);
        assertEquals(200f, unit.getPosition().y(), 0.5f);
        assertEquals(0f, unit.getPosition().z(), 1e-3f, "and back on the ground beyond it");
        assertEquals(0, unit.getFloor());
    }

    @Test
    void aUnitPassesUnderADeckHighEnoughAndNotUnderALowOne() {
        valley(15f);
        var under = logic.spawn(walker, new Coord3D(50f, 200f, 0f), 1);
        under.findModule(MoveUpdate.class).moveTo(new Coord3D(350f, 200f, 0f));
        walk(under, at -> assertEquals(0f, at.z(), 1e-3f, "under it, on the ground"));
        assertEquals(350f, under.getPosition().x(), 0.5f, "through the valley, under the deck 15 up");

        setUp();
        valley(5f);
        var stopped = logic.spawn(walker, new Coord3D(50f, 200f, 0f), 1);
        var legs = stopped.findModule(MoveUpdate.class);
        legs.moveTo(new Coord3D(350f, 200f, 0f));
        walk(stopped, at -> { });
        assertTrue(stopped.getPosition().x() < 180f, "a deck 5 up closes the valley: " + stopped.getPosition());
        assertTrue(legs.stoppedShort());
    }

    @Test
    void aThingOnTheDeckAndOneUnderItAreNotInEachOthersWay() {
        river(40);
        var above = logic.spawn(walker, new Coord3D(140f, 205f, 20f), 1);
        assertEquals(1, above.getFloor(), "put down at the deck's height, it is on the deck");
        var below = logic.spawn(walker, new Coord3D(140f, 100f, 0f), 1);
        below.findModule(MoveUpdate.class).moveTo(new Coord3D(140f, 300f, 0f));

        walk(below, at -> assertEquals(140f, at.x(), 1e-3f, "straight on under it, no step aside: " + at));

        assertEquals(300f, below.getPosition().y(), 0.5f);
        assertEquals(new Coord3D(140f, 205f, 20f), above.getPosition(), "and the one above never moved");
    }

    @Test
    void closingADeckTellsTheGameWhoWasOnItAndTheRouteGoesRoundUntilItOpens() {
        int deck = river(35); // a ford at the top
        var onIt = logic.spawn(walker, new Coord3D(200f, 205f, 20f), 1);
        var walkerAway = logic.spawn(walker, new Coord3D(50f, 200f, 0f), 1);
        var told = new ArrayList<List<GameObject>>();
        logic.onDeckClosed((floor, things) -> {
            assertEquals(deck, floor);
            told.add(things);
        });
        var farBank = new Coord3D(350f, 200f, 0f);
        assertTrue(logic.findPath(walkerAway, farBank).getWaypoints().stream().allMatch(p -> p.y() < 230f),
                "open, the short way over it");

        logic.setDeckOpen(deck, false);

        assertEquals(List.of(List.of(onIt)), told, "the game is told who was on it");
        assertEquals(0, onIt.getFloor());
        assertEquals(0f, onIt.getPosition().z(), 1e-3f, "handed down to the ground under it");
        logic.update();
        assertTrue(logic.findPath(walkerAway, farBank).getWaypoints().stream().anyMatch(p -> p.y() > 340f),
                "closed, the long way round by the ford");

        logic.setDeckOpen(deck, true);
        logic.update();
        assertTrue(logic.findPath(walkerAway, farBank).getWaypoints().stream().allMatch(p -> p.y() < 230f),
                "open again, the short way back");
    }

    @Test
    void theDeckAndTheFloorsOfThingsAreInTheChecksum() {
        int deck = river(40);
        var unit = logic.spawn(walker, new Coord3D(140f, 205f, 20f), 1);
        long open = logic.checksum();
        logic.setDeckOpen(deck, false);
        long closed = logic.checksum();
        assertTrue(open != closed, "a deck closed sums differently");
        unit.setFloor(1);
        assertTrue(logic.checksum() != closed, "and so does the floor a thing is on");
    }
}
