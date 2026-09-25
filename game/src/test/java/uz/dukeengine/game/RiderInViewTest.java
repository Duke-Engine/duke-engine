package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.rts.module.ContainModule;

/** A rider is shown, on what it rides, and clicked as that: never selectable itself. */
class RiderInViewTest {

    @Test
    void aRiderIsInTheViewRidingOnItsCarrierAndCannotBeSelectedItself() {
        var game = DukeGame.create("overlord").loadUnits(DukeGame.STARTER_UNITS).map(40, 40);
        var china = game.addPlayer("China", Color.RED);
        game.localPlayer(china);
        game.spawn("Barracks", china, 100f, 100f);
        game.spawn("Rifleman", china, 100f, 100f);
        game.runHeadless(1);
        var carrier = game.getLogic().getObjects().get(0);
        var rider = game.getLogic().getObjects().get(1);
        var hold = new ContainModule(carrier, new ContainModule.Data(1, null, false, "GUNNER"));
        carrier.addModule(hold);
        hold.load(rider);
        game.runHeadless(1);

        var view = game.getSnapshot().units().stream().filter(one -> one.id() == rider.getId().value()).findFirst()
                .orElseThrow();
        assertEquals(carrier.getId().value(), view.ridesOn(), "on what it rides");
        assertFalse(view.selectable(), "clicked as its carrier, never itself");
    }

    /** A hold riding vehicles: the tank on it is in the view, riding; the two riflemen inside it are not. */
    @Test
    void aHoldRidingOneKindShowsItsRiderAndNotThoseInside() {
        var game = DukeGame.create("helix").loadUnits(DukeGame.STARTER_UNITS).map(40, 40);
        var china = game.addPlayer("China", Color.RED);
        game.localPlayer(china);
        game.spawn("Barracks", china, 100f, 100f);
        game.spawn("Tank", china, 100f, 100f);
        game.spawn("Rifleman", china, 100f, 100f);
        game.spawn("Rifleman", china, 100f, 100f);
        game.runHeadless(1);
        var objects = game.getLogic().getObjects();
        var carrier = objects.get(0);
        var hold = new ContainModule(carrier, new ContainModule.Data(3, null, false, "GUNNER", false, null, null,
                null, false, List.of(Kind.of("VEHICLE"))));
        carrier.addModule(hold);
        var inside = List.of(objects.get(2).getId().value(), objects.get(3).getId().value());
        objects.subList(1, 4).forEach(hold::load);
        game.runHeadless(1);

        var views = game.getSnapshot().units();
        var tank = views.stream().filter(one -> one.id() == objects.get(1).getId().value()).findFirst().orElseThrow();
        assertEquals(carrier.getId().value(), tank.ridesOn(), "the tank rides on top");
        assertTrue(views.stream().noneMatch(one -> inside.contains(one.id())), "the riflemen inside are not drawn");
    }

    /**
     * A hold that shows its passengers, the reference's fire base: the two in it drawn where the game stood them,
     * turned its way, not selectable and clicked as the hold, firing without being moved; a hold beside it that does
     * not show its two draws neither.
     */
    @Test
    void aShowingHoldsPassengersAreDrawnWhereTheGameStandsThem() {
        var game = DukeGame.create("firebase").loadUnits(DukeGame.STARTER_UNITS).map(40, 40);
        var china = game.addPlayer("China", Color.RED);
        game.localPlayer(china);
        game.spawn("Barracks", china, 100f, 100f);
        game.spawn("Barracks", china, 200f, 100f);
        for (int one = 0; one < 4; one++) {
            game.spawn("Rifleman", china, 150f, 150f);
        }
        game.runHeadless(1);
        var objects = game.getLogic().getObjects();
        var base = objects.get(0);
        var plain = objects.get(1);
        var shows = new ContainModule(base, new ContainModule.Data(4, null, true, null, false, null, null, null,
                false, List.of(), true));
        base.addModule(shows);
        var hides = new ContainModule(plain, new ContainModule.Data(4));
        plain.addModule(hides);
        shows.load(objects.get(2));
        shows.load(objects.get(3));
        hides.load(objects.get(4));
        hides.load(objects.get(5));
        objects.get(2).setPosition(new uz.dukeengine.core.math.Coord3D(117f, 107f, 0f)); // in its trenches
        objects.get(2).setOrientation(0.5f);
        objects.get(3).setPosition(new uz.dukeengine.core.math.Coord3D(83f, 93f, 0f));
        game.runHeadless(2);

        var views = game.getSnapshot().units();
        for (var stood : objects.subList(2, 4)) {
            var view = views.stream().filter(one -> one.id() == stood.getId().value()).findFirst().orElseThrow();
            assertEquals(stood.getPosition().x(), view.x(), "drawn where the game stood it");
            assertEquals(stood.getPosition().y(), view.y());
            assertFalse(view.selectable(), "not selectable itself");
            assertEquals(base.getId().value(), view.ridesOn(), "clicked as the hold");
        }
        var turned = views.stream().filter(one -> one.id() == objects.get(2).getId().value()).findFirst()
                .orElseThrow();
        assertEquals(0.5f, turned.orientation(), "turned its way");
        assertEquals(117f, objects.get(2).getPosition().x(), "not moved by its hold");
        var hidden = List.of(objects.get(4).getId().value(), objects.get(5).getId().value());
        assertTrue(views.stream().noneMatch(one -> hidden.contains(one.id())), "a hold not showing them draws neither");
    }
}
