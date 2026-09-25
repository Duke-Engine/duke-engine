package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.TargetRule;
import uz.dukeengine.rts.module.WeaponUpdate;

/**
 * The pointer over something the selection cannot hit, and the order that would follow it: the simulation says
 * no to both, the snapshot carrying the first so the window can draw it.
 */
class AttackableTest {

    private static final String UNITS = """
            Object
              Name = Tank
              KindOf = [VEHICLE, SELECTABLE, CAN_ATTACK]
              Geometry = Cylinder
                Radius = 4
                Height = 5
              End
              Modules = [
                ActiveBody
                  MaxHealth = 300
                End,
                WeaponUpdate
                  Damage = 40
                  AttackRange = 30
                  ReloadFrames = 45
                  Targets = [GROUND]
                End
              ]
            End
            Object
              Name = Worker
              KindOf = [INFANTRY, SELECTABLE]
              Geometry = Cylinder
                Radius = 3
                Height = 9
              End
              Modules = [
                ActiveBody
                  MaxHealth = 40
                End
              ]
            End
            Object
              Name = Helicopter
              KindOf = [VEHICLE, SELECTABLE]
              Geometry = Cylinder
                Radius = 5
                Height = 4
              End
              Modules = [
                ActiveBody
                  MaxHealth = 200
                End
              ]
            End
            """;

    private record Scene(DukeGame game, GameObject tank, GameObject worker, GameObject helicopter) {
    }

    private static Scene scene() {
        var game = DukeGame.create("Attackable").loadUnits(UNITS).map(40, 40);
        var me = game.addPlayer("Me", Color.CYAN);
        var them = game.addPlayer("Them", Color.RED);
        game.enemies(me, them).localPlayer(me)
                .spawn("Tank", me, 100f, 100f)
                .spawn("Worker", me, 120f, 100f)
                .spawn("Helicopter", them, 300f, 300f);
        game.runHeadless(1);
        game.getLogic().setTargetRules(List.of(
                new TargetRule(List.of(Kind.of("VEHICLE")), true, List.of("AIRBORNE_VEHICLE")),
                new TargetRule(List.of(), false, List.of("GROUND"))));
        return new Scene(game, named(game, "Tank"), named(game, "Worker"), named(game, "Helicopter"));
    }

    private static GameObject named(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(template)).findFirst().orElseThrow();
    }

    @Test
    void thePointerOverSomethingNothingSelectedCanHitSaysSo() {
        var scene = scene();
        var game = scene.game();
        scene.helicopter().setStatus(ObjectStatus.AIRBORNE);
        game.setSelection(List.of(scene.tank().getId().value()));

        game.setPointedAt(scene.helicopter().getId().value());
        game.runHeadless(1);
        assertFalse(game.getSnapshot().attackable(), "a tank gun and a helicopter overhead");

        scene.helicopter().clearStatus(ObjectStatus.AIRBORNE);
        game.runHeadless(1);
        assertTrue(game.getSnapshot().attackable(), "landed, it is ground");

        game.setPointedAt(-1);
        game.runHeadless(1);
        assertTrue(game.getSnapshot().attackable(), "nothing under the pointer is nothing refused");
    }

    /** A selection with nothing armed in it refuses nothing: the pointer is what it always was. */
    @Test
    void aSelectionWithNothingArmedRefusesNothing() {
        var scene = scene();
        var game = scene.game();
        scene.helicopter().setStatus(ObjectStatus.AIRBORNE);
        game.setSelection(List.of(scene.worker().getId().value()));
        game.setPointedAt(scene.helicopter().getId().value());
        game.runHeadless(1);

        assertTrue(game.getSnapshot().attackable());
    }

    /** The order itself is refused in the simulation, whatever the window drew. */
    @Test
    void anOrderToAttackWhatNoWeaponCanHitIsRefused() {
        var scene = scene();
        var game = scene.game();
        scene.helicopter().setStatus(ObjectStatus.AIRBORNE);
        var me = game.getLocalPlayerIndex();

        game.postCommand(new GameMessage.AttackObject(me, List.of(scene.tank().getId()), scene.helicopter().getId()));
        game.runHeadless(2);

        assertEquals(null, scene.tank().findModule(WeaponUpdate.class).getTarget(), "refused");
    }
    @Test
    void theGameNamesTheOrderAClickOnAThingWouldGiveAndTheSnapshotCarriesIt() {
        var scene = scene();
        var game = scene.game();
        game.contextOrder((selection, target) -> target.getTemplate().name().equals("Tank")
                && selection.stream().allMatch(u -> u.getTemplate().name().equals("Worker")) ? "Enter" : null);
        game.setSelection(List.of(scene.worker().getId().value()));

        game.setPointedAt(scene.tank().getId().value());
        game.runHeadless(1);
        assertEquals("Enter", game.getSnapshot().contextOrder(), "a worker pointed at the tank it may board");

        game.setPointedAt(scene.helicopter().getId().value());
        game.runHeadless(1);
        assertEquals(null, game.getSnapshot().contextOrder(), "nothing to board there");

        game.setSelection(List.of());
        game.setPointedAt(scene.tank().getId().value());
        game.runHeadless(1);
        assertEquals(null, game.getSnapshot().contextOrder(), "nothing selected, nothing ordered");
    }

    @Test
    void theGameMayNameTheOrderAClickOnTheGroundWouldGive() {
        var scene = scene();
        var game = scene.game();
        var asked = new java.util.ArrayList<uz.dukeengine.core.math.Coord3D>();
        game.groundOrder((selection, place) -> {
            asked.add(place);
            return selection.contains(scene.tank()) ? "Steer" : null;
        });
        var place = new uz.dukeengine.core.math.Coord3D(200f, 150f, 0f);

        game.setSelection(List.of(scene.tank().getId().value()));
        game.setPointedAt(-1, place);
        game.runHeadless(1);
        assertEquals("Steer", game.getSnapshot().contextOrder(), "the tank steered by a click on the ground");
        assertEquals(place, asked.getLast(), "asked with the point under the pointer");

        game.setSelection(List.of(scene.worker().getId().value()));
        game.runHeadless(1);
        assertEquals(null, game.getSnapshot().contextOrder(), "no word: a click there moves the worker");

        asked.clear();
        game.setPointedAt(scene.worker().getId().value(), place);
        game.runHeadless(1);
        assertEquals(null, game.getSnapshot().contextOrder(), "on his own unit a click selects it");
        assertTrue(asked.isEmpty(), "and the ground is not asked about");
    }

    /**
     * A rule answering its word only over ground its player has seen, as the reference steers a beam only there: over
     * ground never seen the snapshot carries no word, and the pointer is what it is with none; over seen ground, the word.
     */
    @Test
    void theGroundRuleIsToldWhetherThePlayerHasEverSeenThePoint() {
        var scene = scene();
        var game = scene.game();
        game.groundOrder((selection, place, seen) -> seen ? "Steer" : null);
        var place = new uz.dukeengine.core.math.Coord3D(200f, 150f, 0f);
        game.setSelection(List.of(scene.tank().getId().value()));

        game.setPointedAt(-1, place, false);
        game.runHeadless(1);
        assertEquals(null, game.getSnapshot().contextOrder(), "never seen: no word");

        game.setPointedAt(-1, place, true);
        game.runHeadless(1);
        assertEquals("Steer", game.getSnapshot().contextOrder(), "seen: the word");
    }
}
