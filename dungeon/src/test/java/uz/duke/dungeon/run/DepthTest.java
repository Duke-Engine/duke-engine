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

    /** The shipped file, for the one thing these tests read out of it: who the bosses are. */
    private static final DungeonSettings SHIPPED = DungeonSettings.load();

    /** Levels that arrive on the first kill, and the shipped four floors under them. */
    private static final DungeonSettings BRISK = DungeonSettings.parse("""
            DungeonDepth Descent
              Bosses = Warden Reaper Necromancer Champion
            End
            DungeonLeveling Progression
              MaxLevel = 20
              XpBase = 5
              XpStep = 0
              ArmourPercentPerLevel = 3
              MinDamageTakenPercent = 40
            End
            DungeonAttributes Conversion
              HealthPerStrength = 10
              SpeedPerAgility = 0.15
              ManaPerIntelligence = 5
              DamagePerPrimary = 1.0
            End
            DungeonHero Rogue
              Primary = AGI
              Strength = 12
              Agility = 12
              Intelligence = 8
              StrPerLevel = 3
              AgiPerLevel = 4
              IntPerLevel = 1
            End
            """);

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals(template))
                .findFirst().orElse(null);
    }

    private static GameObject hero(DukeGame game) {
        return find(game, "Rogue");
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

    /**
     * Remove the boss the way killing it would, and wait for the floor to close.
     *
     * <p>It does not close in the same frame: the boss leaves something behind,
     * and rebuilding the world at once would take it away before anyone could walk
     * to it. So the wait is the file's own {@code DescendDelayFrames} rather than
     * a number written here, and re-tuning that moves this with it.
     */
    private static void defeatTheBoss(uz.duke.dungeon.Dungeon.Session session) {
        var game = session.game();
        var boss = bossOf(session);
        assertNotNull(boss, "there should be a boss to defeat");
        game.getLogic().destroyObject(boss);
        game.runHeadless(BRISK.descendDelayFrames() + 4);
    }

    /**
     * Whichever of the four is waiting on the floor the run is on.
     *
     * <p>Asked of the settings rather than named here: there is one boss per
     * floor now, and a test that hunted for a template called "Boss" would be
     * looking for a creature that stopped existing when the descent got a bottom.
     */
    private static GameObject bossOf(uz.duke.dungeon.Dungeon.Session session) {
        return find(session.game(), SHIPPED.bossKindAt(session.run().getDepth()));
    }

    // ---- the bottom of it ----

    /**
     * The last boss ends the game rather than opening another floor.
     *
     * <p>The one thing this whole arrangement is for. A descent with no bottom
     * asks only how much further; this one can be finished, and the difference is
     * a single comparison in the run loop plus a list of four names in the file.
     */
    @Test
    void beatingTheLastBossWinsTheRunInsteadOfOpeningAnotherFloor() {
        var session = Dungeon.newSession(11L, BRISK);
        session.game().runHeadless(1);
        for (int floor = 1; floor < BRISK.finalDepth(); floor++) {
            defeatTheBoss(session);
        }
        assertEquals(BRISK.finalDepth(), session.run().getDepth(), "at the bottom of it");

        session.game().getLogic().destroyObject(bossOf(session));
        session.game().runHeadless(BRISK.descendDelayFrames() + 4);

        assertEquals(DungeonRun.State.WON, session.run().getState(), "the run should be won");
        assertEquals(BRISK.finalDepth(), session.run().getDepth(),
                "and there is no fifth floor to be sent to");
    }

    /** And a won run, like a lost one, starts again with nothing. */
    @Test
    void aWonRunStartsAgainFromTheTopWithNothing() {
        var session = Dungeon.newSession(11L, BRISK);
        var game = session.game();
        game.runHeadless(1);
        levelUp(game, session);
        assertTrue(session.progress().getLevel() > 1, "he needs something to lose");
        for (int floor = 1; floor < BRISK.finalDepth(); floor++) {
            defeatTheBoss(session);
        }
        game.getLogic().destroyObject(bossOf(session));
        game.runHeadless(BRISK.descendDelayFrames() + 4);

        game.runHeadless(BRISK.victoryFrames() + 4);

        assertEquals(DungeonRun.State.RUNNING, session.run().getState());
        assertEquals(1, session.run().getDepth(), "back to the top");
        assertEquals(1, session.progress().getLevel(), "and starting over");
    }

    /** The floor stays open for a moment after the boss falls, and then closes. */
    @Test
    void theFloorDoesNotCloseInTheSameFrameAsTheBoss() {
        var session = Dungeon.newSession(11L, BRISK);
        var game = session.game();
        game.runHeadless(1);

        game.getLogic().destroyObject(bossOf(session));
        game.runHeadless(3);
        assertEquals(1, session.run().getDepth(),
                "there has to be a moment to pick up what the boss left");

        game.runHeadless(BRISK.descendDelayFrames() + 4);
        assertEquals(2, session.run().getDepth(), "and then the floor closes");
    }

    @Test
    void everyFloorHasABossToBeat() {
        var session = Dungeon.newSession(11L, BRISK);
        session.game().runHeadless(1);

        assertNotNull(bossOf(session));
        assertEquals(1, session.run().getDepth(), "a run opens on the first floor");
    }

    @Test
    void killingTheBossOpensTheNextFloor() {
        var session = Dungeon.newSession(11L, BRISK);
        var game = session.game();
        game.runHeadless(1);
        var firstHero = hero(game).getId();

        defeatTheBoss(session);

        assertEquals(2, session.run().getDepth(), "the boss was the way down");
        assertNotNull(bossOf(session), "and the next floor has its own");
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

        defeatTheBoss(session);

        assertEquals(2, session.run().getDepth());
        assertTrue(session.progress().getLevel() >= levelBefore,
                "levels should survive the descent, was " + levelBefore
                        + " and is now " + session.progress().getLevel());
        assertTrue(session.progress().getExperience() >= experienceBefore,
                "and so should the experience behind them");
    }

    /**
     * A levelled hero arrives on the new floor with the body he earned.
     *
     * <p>Measured against what he has when he gets there rather than against what
     * he had when he set off: the fight for the floor goes on while the boss is
     * dying, so he may well have taken another level or walked over something on
     * the way. What is being held still is that the new body is the base plus
     * everything he has — not that he stopped earning.
     */
    @Test
    void hisStrengthArrivesWithHim() {
        var session = Dungeon.newSession(11L, BRISK);
        var game = session.game();
        game.runHeadless(1);
        float baseCeiling = hero(game).getBody().getMaxHealth();

        levelUp(game, session);
        assertTrue(session.progress().getLevel() > 1);

        defeatTheBoss(session);

        var his = BRISK.heroNamed("Rogue").attributes();
        var rules = BRISK.attributeRules();
        int level = session.progress().getLevel();
        assertEquals(baseCeiling + rules.health(his.atLevel(level)) - rules.health(his.atLevel(1))
                        + session.progress().getLoot().health(),
                hero(game).getBody().getMaxHealth(), 0.01f,
                "the new body should be as tough as everything he arrived with");
    }

    /** And dying takes all of it: level, experience and depth alike. */
    @Test
    void dyingSendsHimBackToTheFirstFloorWithNothing() {
        var session = Dungeon.newSession(11L, BRISK);
        var game = session.game();
        game.runHeadless(1);

        defeatTheBoss(session);
        defeatTheBoss(session);
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
        int bottom = SHIPPED.finalDepth();
        float shallow = healthOfFirstMonster(1);
        float deep = healthOfFirstMonster(bottom);

        assertTrue(deep > shallow, "a depth-" + bottom + " monster should outlast a depth-1 one, "
                + "got " + deep + " against " + shallow);
    }

    private static float healthOfFirstMonster(int depth) {
        var settings = DungeonSettings.load();
        var session = Dungeon.newSession(11L, settings);
        var game = session.game();
        game.runHeadless(1);
        for (int floor = 1; floor < depth; floor++) {
            defeatTheBoss(session);
        }
        assertEquals(depth, session.run().getDepth());

        // The boss is the one creature guaranteed on every floor.
        var boss = bossOf(session);
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
            defeatTheBoss(session);
        }
        return signature.toString();
    }
}
