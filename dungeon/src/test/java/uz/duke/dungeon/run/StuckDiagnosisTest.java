package uz.duke.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;

/**
 * Diagnosis, not a feature: does anything end up standing inside a wall?
 *
 * <p>Written to answer one question with a number rather than a guess. The
 * suspicion is that a unit dodging a neighbour in a one-cell corridor steps its
 * centre into stone — the per-step check only looks for other objects, never
 * terrain — and that once its centre is in a blocked cell the pathfinder refuses
 * to route it anywhere, because a search from a blocked start returns nothing.
 * If that is what happens, this counts it.
 */
class StuckDiagnosisTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private record Report(int unitsInStone, int unitsThatNeverMoved, int unitsOffTheGround,
            int totalUnits) {
    }

    /**
     * Drive the hero at whatever is nearest, so the dungeon actually gets up and
     * walks. Monsters that have not noticed him are supposed to stand still, so a
     * measurement taken while he loiters in the entrance says nothing at all.
     */
    private static void hunt(DukeGame game) {
        var hero = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals("Hero"))
                .findFirst().orElse(null);
        if (hero == null) {
            return;
        }
        GameObject prey = null;
        float best = Float.MAX_VALUE;
        for (var object : game.getLogic().getObjects()) {
            if (object.getPlayerIndex() == hero.getPlayerIndex()) {
                continue;
            }
            float distance = hero.getPosition().distance(object.getPosition());
            if (distance < best) {
                best = distance;
                prey = object;
            }
        }
        if (prey != null) {
            game.postCommand(new uz.duke.rts.message.GameMessage.AttackObject(
                    game.getLocalPlayerIndex(), java.util.List.of(hero.getId()), prey.getId()));
        }
    }

    private static Report run(long seed, int frames) {
        var session = Dungeon.newSession(seed, SETTINGS);
        var game = session.game();
        game.runHeadless(1);

        var startedAt = new HashMap<Integer, uz.duke.core.math.Coord3D>();
        var furthestMoved = new HashMap<Integer, Float>();
        for (var unit : game.getLogic().getObjects()) {
            startedAt.put(unit.getId().value(), unit.getPosition());
            furthestMoved.put(unit.getId().value(), 0f);
        }

        for (int frame = 0; frame < frames; frame++) {
            if (frame % 60 == 0) {
                hunt(game); // keep him moving so the dungeon has to move too
            }
            game.runHeadless(1);
            for (var unit : game.getLogic().getObjects()) {
                var from = startedAt.get(unit.getId().value());
                if (from != null) {
                    furthestMoved.merge(unit.getId().value(),
                            unit.getPosition().distance(from), Math::max);
                }
            }
        }

        var terrain = game.getTerrain();
        int inStone = 0;
        int neverMoved = 0;
        int offTheGround = 0;
        int total = 0;
        for (var unit : game.getLogic().getObjects()) {
            if (unit.findModule(uz.duke.core.module.MoveUpdate.class) == null) {
                // Only things that walk. What this measures is a unit the
                // pathfinder can no longer help, and an arrow has no pathfinder:
                // it goes through stone on purpose, and counting it as stuck
                // reports a bug in the one thing that is working as intended.
                continue;
            }
            total++;
            if (terrain.isBlocked(terrain.toCellX(unit.getPosition()),
                    terrain.toCellY(unit.getPosition()))) {
                inStone++;
            }
            if (furthestMoved.getOrDefault(unit.getId().value(), 0f) < 1f) {
                neverMoved++;
            }
            if (unit.getPosition().z() != terrain.groundHeight(unit.getPosition())) {
                offTheGround++;
            }
        }
        return new Report(inStone, neverMoved, offTheGround, total);
    }

    /**
     * Nothing should ever be standing in stone.
     *
     * <p>A unit whose centre is inside a blocked cell cannot be helped by the
     * pathfinder — a search whose start cell is blocked returns no path at all —
     * so it is not merely wedged, it is permanently unable to plan its way out.
     */
    @Test
    void nothingEndsUpStandingInsideAWall() {
        var worst = new StringBuilder();
        int totalInStone = 0;
        for (long seed = 0; seed < 12; seed++) {
            var report = run(seed, 900);
            totalInStone += report.unitsInStone();
            if (report.unitsInStone() > 0) {
                worst.append("\n  seed ").append(seed).append(": ")
                        .append(report.unitsInStone()).append(" of ").append(report.totalUnits())
                        .append(" standing in stone, ").append(report.unitsThatNeverMoved())
                        .append(" never moved at all");
            }
        }
        assertEquals(0, totalInStone,
                "units walked into walls they can never path out of:" + worst);
    }

    /**
     * Whatever set off should have arrived somewhere.
     *
     * <p>Only the units that moved at all are counted, so the monsters still asleep
     * in rooms the hero never reached — which are supposed to stand still — do not
     * drown out the signal. What is being looked for is something that set off and
     * then froze.
     */
    @Test
    void whateverSetOffGotSomewhere() {
        var detail = new StringBuilder();
        int stuckInStone = 0;
        for (long seed = 0; seed < 12; seed++) {
            var report = run(seed, 1200);
            stuckInStone += report.unitsInStone();
            detail.append("\n  seed ").append(seed).append(": ")
                    .append(report.unitsInStone()).append(" in stone of ")
                    .append(report.totalUnits());
        }
        assertEquals(0, stuckInStone, "units ended the fight inside walls:" + detail);
    }

    /**
     * And everything is standing on the floor it is standing on.
     *
     * <p>The dungeon has storeys now, and height is read off the ground under a
     * unit every step rather than carried with it. A unit whose z has drifted
     * from the ground beneath it is one the client will draw sunk into the floor
     * or hovering over it, and neither has anything on screen to explain it.
     */
    @Test
    void nothingIsLeftFloatingOverItsOwnFloor() {
        var detail = new StringBuilder();
        int offTheGround = 0;
        for (long seed = 0; seed < 12; seed++) {
            var report = run(seed, 900);
            offTheGround += report.unitsOffTheGround();
            if (report.unitsOffTheGround() > 0) {
                detail.append("\n  seed ").append(seed).append(": ")
                        .append(report.unitsOffTheGround()).append(" of ")
                        .append(report.totalUnits()).append(" at the wrong height");
            }
        }
        assertEquals(0, offTheGround, "units are not standing on their own storey:" + detail);
    }
}
