package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.KeepsDead;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.rts.RtsTemplate;

/**
 * Still things seen once, drawn as last seen while their ground is fogged — the reference's ghosts ({@code
 * W3DGhostObject}) — and things out of sight kept a while ({@code GameClient::update}: 60 frames, 150 if dead).
 */
class RememberedThingsTest {

    /** Keeps its thing in the world, dead, while its death plays out. */
    static final class Hulk extends Module implements KeepsDead {
        Hulk(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean keepsDead() {
            return true;
        }
    }

    private record Field(DukeGame game, GameObject scout, GamePlayer them) {

        UnitView viewOf(String template) {
            return game.getSnapshot().units().stream().filter(view -> view.templateName().equals(template))
                    .findFirst().orElse(null);
        }

        GameObject spawn(String template, float x, float y) {
            game.spawn(template, them, x, y);
            game.runHeadless(1);
            return game.getLogic().getObjects().getLast();
        }
    }

    private static Field field(boolean cells) {
        var game = DukeGame.create("ghosts").loadUnits(DukeGame.STARTER_UNITS).map(60, 60)
                .addUnits(List.of(
                        RtsTemplate.named("Bunker").visionRange(10f).module(new ActiveBody.Data(100f)).build(),
                        RtsTemplate.named("Tank").visionRange(10f).module(new ActiveBody.Data(100f))
                                .module(new MoveUpdate.Data(60f)).build()))
                .remember(thing -> !thing.isMobile())
                .keepOutOfSight(60, 150);
        var me = game.addPlayer("Me", Color.BLUE);
        var them = game.addPlayer("Them", Color.RED);
        game.enemies(me, them).localPlayer(me).spawn("Rifleman", me, 100f, 100f);
        game.runHeadless(1);
        if (cells) {
            game.getLogic().setSightCells(40f, 10);
        }
        return new Field(game, game.getLogic().getObjects().getFirst(), them);
    }

    @Test
    void aBuildingSeenAtHalfHealthIsDrawnSoOnceItsGroundIsFoggedAndGoneOnceALookerComesBack() {
        var field = field(true);
        var bunker = field.spawn("Bunker", 150f, 100f);
        bunker.getBody().setHealth(50f);
        field.game().runHeadless(2);
        assertFalse(field.viewOf("Bunker").remembered(), "in sight: as it is");

        field.scout().setPosition(new Coord3D(500f, 500f, 0f));
        field.game().runHeadless(12);
        var ghost = field.viewOf("Bunker");
        assertTrue(ghost.remembered(), "its ground fogged: remembered");
        assertEquals(50f, ghost.health(), "at the half health it was seen at");
        assertFalse(ghost.selectable(), "and not picked");

        bunker.markDestroyed();
        field.game().runHeadless(5);
        assertTrue(field.viewOf("Bunker") != null, "destroyed out of sight, still drawn as last seen");

        field.scout().setPosition(new Coord3D(100f, 100f, 0f));
        field.game().runHeadless(2);
        assertNull(field.viewOf("Bunker"), "a looker back: gone");
    }

    @Test
    void aStillThingOnGroundNeverSeenIsNotDrawn() {
        var field = field(true);
        field.spawn("Bunker", 500f, 500f);
        field.game().runHeadless(3);
        assertNull(field.viewOf("Bunker"));
    }

    @Test
    void aTankThatDrivesOutOfSightIsDrawnMovingFor60FramesAndADeadOneFor150() {
        var field = field(false);
        var tank = field.spawn("Tank", 110f, 100f);
        field.game().runHeadless(1);
        tank.getLocomotor().moveTo(new Coord3D(560f, 100f, 0f));
        int lastSeen = -1;
        int drawnUntil = -1;
        for (int frame = 0; frame < 400; frame++) {
            field.game().runHeadless(1);
            var view = field.viewOf("Tank");
            int now = field.game().getSnapshot().frame();
            if (field.game().getLogic().canSee(field.game().getLocalPlayerIndex(), tank)) {
                lastSeen = now;
            } else if (view != null) {
                drawnUntil = now;
                assertTrue(view.moving(), "drawn as it is: moving");
            }
        }
        assertEquals(60, drawnUntil - lastSeen, "60 frames after it was last in sight");

        var dying = field(false);
        var hulk = dying.spawn("Tank", 110f, 100f);
        hulk.addModule(new Hulk(hulk));
        dying.game().runHeadless(1);
        hulk.getBody().setHealth(0f);
        dying.game().runHeadless(1);
        int seen = dying.game().getSnapshot().frame();
        dying.scout().setPosition(new Coord3D(500f, 500f, 0f));
        int gone = -1;
        for (int frame = 1; frame <= 400 && gone < 0; frame++) {
            dying.game().runHeadless(1);
            if (dying.viewOf("Tank") == null) {
                gone = dying.game().getSnapshot().frame();
            }
        }
        assertEquals(151, gone - seen, "a dead one kept 150 frames after it was last in sight");
    }
}
