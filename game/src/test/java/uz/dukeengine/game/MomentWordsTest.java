package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.view.MomentWords;
import uz.dukeengine.game.view.UnitView;
import uz.dukeengine.rts.event.WeaponFired;

/** The game's words for a thing's moments, held by its view while they last, for its looks to choose by. */
class MomentWordsTest {

    private static final MomentWords WORDS = new MomentWords("MOVING", "ATTACKING", List.of("FIRING_A"),
            List.of("BETWEEN_FIRING_SHOTS_A"), List.of("RELOADING_A"), "TURRET_ROTATE");

    /** A gunner with a clip of two, a quarter second between shots and two thirds of one to reload. */
    private static final String GUNNER = """
            Object
              Name = Gunner
              KindOf = [INFANTRY, SELECTABLE, CAN_ATTACK]
              Geometry = Cylinder
                Radius = 3
                Height = 9
              End
              VisionRange = 80
              Modules = [
                ActiveBody
                  MaxHealth = 100000
                End,
                MoveUpdate
                  Speed = 14
                End,
                WeaponUpdate
                  Damage = 1
                  AttackRange = 60
                  ReloadFrames = 8
                  ClipSize = 2
                  ClipReloadFrames = 20
                  AutoReload = Yes
                End
              ]
            End
            """;

    private static UnitView viewOf(DukeGame game, int id) {
        return game.getSnapshot().units().stream().filter(one -> one.id() == id).findFirst().orElseThrow();
    }

    @Test
    void movingItHoldsItsMovingWordAndStandingItDoesNot() {
        var game = DukeGame.create("moments").loadUnits(DukeGame.STARTER_UNITS).momentWords(WORDS).map(40, 40);
        var me = game.addPlayer("Me", Color.BLUE);
        game.localPlayer(me).spawn("Rifleman", me, 100f, 100f);
        game.runHeadless(1);
        int id = game.getLogic().getObjects().getFirst().getId().value();
        assertFalse(viewOf(game, id).conditions().contains("MOVING"), "standing");

        game.getLogic().getObjects().getFirst().getLocomotor().moveTo(new Coord3D(300f, 100f, 0f));
        game.runHeadless(2);
        assertTrue(viewOf(game, id).conditions().contains("MOVING"), "moving");
        game.runHeadless(900);
        assertFalse(viewOf(game, id).conditions().contains("MOVING"), "there, and standing again");
    }

    /**
     * A slot's words follow its status: its firing word on the frame it fires, its between-shots word until it is
     * ready again, its reloading word while it refills — and its attacking word while it has a target.
     */
    @Test
    void aSlotsWordsFollowItsWeaponStatus() {
        var game = DukeGame.create("moments").loadUnits(GUNNER).momentWords(WORDS).map(40, 40);
        var me = game.addPlayer("Me", Color.BLUE);
        var them = game.addPlayer("Them", Color.RED);
        game.enemies(me, them).localPlayer(me).spawn("Gunner", me, 100f, 100f).spawn("Gunner", them, 140f, 100f);
        game.runHeadless(1);
        int id = game.getLogic().getObjects().getFirst().getId().value();

        var words = new ArrayList<List<String>>();
        var fired = new ArrayList<Boolean>();
        for (int frame = 0; frame < 90; frame++) {
            game.runHeadless(1);
            var snapshot = game.getSnapshot();
            words.add(viewOf(game, id).conditions());
            fired.add(snapshot.events().stream().anyMatch(event -> event instanceof WeaponFired shot
                    && shot.shooter().value() == id));
        }

        int shots = 0;
        boolean sawBetween = false;
        boolean sawReloading = false;
        for (int frame = 0; frame < words.size(); frame++) {
            var holding = words.get(frame);
            if (fired.get(frame)) {
                shots++;
                assertTrue(holding.contains("FIRING_A"), "its firing word the frame it fires: " + holding);
                assertFalse(holding.contains("BETWEEN_FIRING_SHOTS_A") || holding.contains("RELOADING_A"));
                continue;
            }
            assertFalse(holding.contains("FIRING_A"), "only the frame it fires: " + frame + " " + holding);
            sawBetween |= holding.contains("BETWEEN_FIRING_SHOTS_A");
            sawReloading |= holding.contains("RELOADING_A");
            if (shots > 0) {
                assertTrue(holding.contains("ATTACKING"), "attacking the whole time");
            }
        }
        assertTrue(shots >= 3, "it fired: " + shots);
        assertTrue(sawBetween, "between its shots");
        assertTrue(sawReloading, "and refilling its clip");
    }
}
