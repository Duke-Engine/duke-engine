package uz.duke.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;

/**
 * Going down, and what survives the trip.
 *
 * <p>Two things replace the hero object: descending a floor and dying on one. They
 * have to mean opposite things — a floor keeps everything he has earned, a death
 * keeps none of it — which is why the run loop says which it is rather than
 * leaving anything to work it out from the new hero appearing.
 */
class DepthTest {

    /** Levels that arrive on the first kill, so a floor's worth of them is visible. */
    private static final DungeonSettings BRISK = DungeonSettings.parse("""
            DungeonLeveling Progression
              MaxLevel = 20
              XpBase = 5
              XpStep = 0
              HealthPerLevel = 30
              DamagePercentPerLevel = 20
              ArmourPercentPerLevel = 3
              MinDamageTakenPercent = 40
            End
            """);

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals(template))
                .findFirst().orElse(null);
    }

    private static GameObject hero(DukeGame game) {
        return find(game, "Hero");
    }

    /**
     * Fight until he has levelled at least once.
     *
     * <p>Told to go for the nearest thing each time rather than one chosen up
     * front: a generated floor puts monsters where it likes, and one picked
     * blindly may be several rooms away behind a fight he has not had yet.
     */
    private static void levelUp(DukeGame game, Dungeon.Session session) {
        for (int attempt = 0; attempt < 40 && session.progress().getLevel() == 1; attempt++) {
            var hero = hero(game);
            if (hero == null) {
                break;
            }
            var prey = nearestMonster(game, hero);
            if (prey == null) {
                break;
            }
            game.postCommand(new uz.duke.rts.message.GameMessage.AttackObject(
                    game.getLocalPlayerIndex(), java.util.List.of(hero.getId()), prey.getId()));
            game.runHeadless(120);
        }
    }

    private static GameObject nearestMonster(DukeGame game, GameObject hero) {
        GameObject nearest = null;
        float best = Float.MAX_VALUE;
        for (var object : game.getLogic().getObjects()) {
            if (object.getPlayerIndex() == hero.getPlayerIndex() || object.getBody() == null) {
                continue;
            }
            float distance = hero.getPosition().distance(object.getPosition());
            if (distance < best) {
                best = distance;
                nearest = object;
            }
        }
        return nearest;
    }

    /** Remove the boss the way killing it would, and let the run loop notice. */
    private static void defeatTheBoss(DukeGame game) {
        var boss = find(game, "Boss");
        assertNotNull(boss, "there should be a boss to defeat");
        game.getLogic().destroyObject(boss);
        game.runHeadless(3);
    }

    @Test
    void everyFloorHasABossToBeat() {
        var session = Dungeon.newSession(11L, BRISK);
        session.game().runHeadless(1);

        assertNotNull(find(session.game(), "Boss"));
        assertEquals(1, session.run().getDepth(), "a run opens on the first floor");
    }

    @Test
    void killingTheBossOpensTheNextFloor() {
        var session = Dungeon.newSession(11L, BRISK);
        var game = session.game();
        game.runHeadless(1);
        var firstHero = hero(game).getId();

        defeatTheBoss(game);

        assertEquals(2, session.run().getDepth(), "the boss was the way down");
        assertNotNull(find(game, "Boss"), "and the next floor has its own");
        assertNotEquals(firstHero, hero(game).getId(), "on a freshly laid-out floor");
    }

    /**
     * The point of depth: he takes his levels with him. Both a floor and a death
     * hand him a new body, so this is the assertion that stops the run loop from
     * treating them as the same event.
     */
    @Test
    void aHeroKeepsWhatHeEarnedWhenHeDescends() {
        var session = Dungeon.newSession(11L, BRISK);
        var game = session.game();
        game.runHeadless(1);

        levelUp(game, session); // earn something worth keeping
        int levelBefore = session.progress().getLevel();
        int experienceBefore = session.progress().getExperience();
        assertTrue(levelBefore > 1, "he needs to have levelled for this to mean anything");

        defeatTheBoss(game);

        assertEquals(2, session.run().getDepth());
        assertTrue(session.progress().getLevel() >= levelBefore,
                "levels should survive the descent, was " + levelBefore
                        + " and is now " + session.progress().getLevel());
        assertTrue(session.progress().getExperience() >= experienceBefore,
                "and so should the experience behind them");
    }

    /** A levelled hero arrives on the new floor with the body his levels bought. */
    @Test
    void hisStrengthArrivesWithHim() {
        var session = Dungeon.newSession(11L, BRISK);
        var game = session.game();
        game.runHeadless(1);
        float baseCeiling = hero(game).getBody().getMaxHealth();

        levelUp(game, session);
        int level = session.progress().getLevel();
        assertTrue(level > 1);

        defeatTheBoss(game);

        assertEquals(baseCeiling + BRISK.levelling().bonusHealth(level),
                hero(game).getBody().getMaxHealth(), 0.01f,
                "the new body should be as tough as the levels he arrived with");
    }

    /** And dying takes all of it: level, experience and depth alike. */
    @Test
    void dyingSendsHimBackToTheFirstFloorWithNothing() {
        var session = Dungeon.newSession(11L, BRISK);
        var game = session.game();
        game.runHeadless(1);

        defeatTheBoss(game);
        defeatTheBoss(game);
        assertEquals(3, session.run().getDepth(), "two floors down");

        game.getLogic().destroyObject(hero(game));
        game.runHeadless(BRISK.respawnDelayFrames() + 5);

        assertEquals(1, session.run().getDepth(), "back to the top");
        assertEquals(1, session.progress().getLevel(), "with nothing earned");
        assertEquals(0, session.progress().getExperience());
        assertNotNull(hero(game), "and a new hero to try again with");
    }

    /** Monsters get harder as the floors go down, by the factors the file states. */
    @Test
    void deeperMonstersAreTougher() {
        float shallow = healthOfFirstMonster(1);
        float deep = healthOfFirstMonster(5);

        assertTrue(deep > shallow,
                "a depth-5 monster should outlast a depth-1 one, got " + deep + " against " + shallow);
    }

    private static float healthOfFirstMonster(int depth) {
        var settings = DungeonSettings.load();
        var session = Dungeon.newSession(11L, settings);
        var game = session.game();
        game.runHeadless(1);
        for (int floor = 1; floor < depth; floor++) {
            defeatTheBoss(game);
        }
        assertEquals(depth, session.run().getDepth());

        // The boss is the one creature guaranteed on every floor.
        var boss = find(game, "Boss");
        assertNotNull(boss);
        return boss.getBody().getMaxHealth();
    }

    /** A whole session is a function of its seed: same seed, same floors. */
    @Test
    void theSameSeedPlaysTheSameFloors() {
        assertEquals(floorSignature(7L), floorSignature(7L));
        assertNotEquals(floorSignature(7L), floorSignature(8L));
    }

    private static String floorSignature(long seed) {
        var session = Dungeon.newSession(seed, DungeonSettings.load());
        var game = session.game();
        game.runHeadless(1);
        var signature = new StringBuilder();
        for (int floor = 0; floor < 3; floor++) {
            signature.append(game.getLogic().getObjectCount()).append(':')
                    .append(game.getLogic().checksum()).append('|');
            defeatTheBoss(game);
        }
        return signature.toString();
    }
}
