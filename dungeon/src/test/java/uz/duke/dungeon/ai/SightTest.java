package uz.duke.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.skill.CastSkill;
import uz.duke.game.DukeGame;
import uz.duke.rts.message.GameMessage;
import uz.duke.rts.module.WeaponUpdate;

/**
 * What the hero shoots at, and what he stops doing when he is told something new.
 *
 * <p>Two rules, and both are about him doing what the player meant rather than
 * what the engine's defaults would have him do:
 *
 * <ul>
 *   <li>He shoots what he can see. The engine's fog is a circle and its target
 *       acquisition knows nothing about walls, so left alone he kills things
 *       through them.
 *   <li>The last order wins. Ordering an attack while he is walking stops the
 *       walk; casting a skill stops it too and abandons what he was sent at.
 * </ul>
 *
 * <p>Fought in an open arena rather than a generated floor so the distances are
 * the test's own — a generated dungeon puts its wall wherever it likes.
 */
class SightTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static final int WIDE = 40;
    private static final int HIGH = 30;
    private static final float CELL = 10f;

    /**
     * A room, optionally divided by a wall with a doorway at the far end.
     *
     * <p>The doorway matters: a wall with no way round it is not a wall, it is two
     * rooms, and "he walks round to get a look at it" would have nowhere to go.
     * Nothing can be seen through it either way — the line these tests draw runs
     * across the middle, well away from the gap.
     */
    private static final int DOORWAY = HIGH - 2;

    private static String arena(int wallColumn) {
        var text = new StringBuilder();
        for (int y = 0; y < HIGH; y++) {
            for (int x = 0; x < WIDE; x++) {
                boolean edge = x == 0 || y == 0 || x == WIDE - 1 || y == HIGH - 1;
                text.append(edge || (x == wallColumn && y != DOORWAY) ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static final int NO_WALL = -1;

    /** The middle of a cell, which is where a creature is put. */
    private static float at(int cell) {
        return (cell + 0.5f) * CELL;
    }

    private record Arena(DukeGame game, GameObject hero, GameObject skeleton) {
    }

    private static Arena arena(int wallColumn, int heroCell, int skeletonCell) {
        return arena(Dungeon.world(arena(wallColumn), SETTINGS), heroCell, skeletonCell);
    }

    private static Arena arena(Dungeon.Arena world, int heroCell, int skeletonCell) {
        var game = world.game();
        game.spawn("Rogue", world.hero(), at(heroCell), at(15));
        game.spawn("Skeleton", world.dungeon(), at(skeletonCell), at(15));
        game.runHeadless(1);
        return new Arena(game, creature(game, "Rogue"), creature(game, "Skeleton"));
    }

    /** The shipped hero, re-tuned: his bow drawn further than his eyes reach. */
    private static String longBow() {
        var creatures = uz.duke.dungeon.content.Content.units();
        assertTrue(creatures.contains("AttackRange = 60"), "the hero's bow should still be 60");
        return creatures.replace("AttackRange = 60", "AttackRange = 200");
    }

    private static Dungeon.Arena flatWorld(String creaturesIni) {
        return Dungeon.world(arena(NO_WALL), null, SETTINGS, creaturesIni,
                new uz.duke.dungeon.loot.LootBag());
    }

    /**
     * The same room with its far half a storey up, and a flight of stairs at the
     * column where it rises — or a sheer edge, which is the case that matters.
     */
    private static String storeys(int raisedFrom, boolean withStair) {
        var text = new StringBuilder();
        for (int y = 0; y < HIGH; y++) {
            for (int x = 0; x < WIDE; x++) {
                boolean edge = x == 0 || y == 0 || x == WIDE - 1 || y == HIGH - 1;
                text.append(edge ? '#'
                        : withStair && x == raisedFrom - 1 ? '/'
                        : x >= raisedFrom ? '1' : '0');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static Arena raisedArena(int raisedFrom, boolean withStair, int heroCell,
            int skeletonCell) {
        return arena(Dungeon.world(arena(NO_WALL), storeys(raisedFrom, withStair), SETTINGS,
                uz.duke.dungeon.content.Content.units(),
                new uz.duke.dungeon.loot.LootBag()), heroCell, skeletonCell);
    }

    private static boolean sees(Arena arena) {
        return SightLine.sees(arena.hero(), arena.skeleton(), SETTINGS.storeyHeight());
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    // ---- the line itself ----

    @Test
    void aClearRoomIsClear() {
        var arena = arena(NO_WALL, 10, 15);
        assertTrue(SightLine.clear(arena.hero(), arena.skeleton()));
    }

    @Test
    void stoneBetweenThemIsNot() {
        var arena = arena(13, 10, 15);
        assertFalse(SightLine.clear(arena.hero(), arena.skeleton()));
    }

    /** Standing against a wall does not hide you: it is what is behind one that does. */
    @Test
    void aCreatureAgainstTheWallIsStillSeen() {
        var arena = arena(13, 10, 12);
        assertTrue(SightLine.clear(arena.hero(), arena.skeleton()),
                "the cell in front of the wall is in plain view");
    }

    @Test
    void sightIsTheSameFromEitherEnd() {
        var arena = arena(13, 10, 15);
        assertFalse(SightLine.clear(arena.skeleton(), arena.hero()));
    }

    // ---- and the two things besides stone that hide a creature ----

    /**
     * The dark hides as well as stone does.
     *
     * <p>His eyes reach 70 and this is 250 away down an empty room: on screen
     * there is nothing there at all, because the fog is drawn to the same number.
     */
    @Test
    void aCreatureBeyondHisEyesIsHiddenHoweverClearTheLineIs() {
        var arena = arena(NO_WALL, 5, 30);

        assertTrue(SightLine.clear(arena.hero(), arena.skeleton()),
                "there is nothing whatever in the way");
        assertFalse(sees(arena), "but it is standing in the dark, and the dark is where he stops");
    }

    /** And inside them it is not hidden, so the test above is about the distance. */
    @Test
    void aCreatureInsideThemIsSeen() {
        assertTrue(sees(arena(NO_WALL, 10, 15)));
    }

    /**
     * A floor above his own hides whoever is standing on it.
     *
     * <p>It is behind its own edge: from the corridor beneath you see the wall
     * holding it up and not the room on top, which is exactly what the client
     * draws. Nothing here knew that until floors had storeys in them, so he shot
     * at monsters the player was never shown.
     */
    @Test
    void aCreatureOnTheFloorAboveIsHiddenFromBelow() {
        var arena = raisedArena(12, false, 10, 14);

        assertTrue(SightLine.clear(arena.hero(), arena.skeleton()),
                "there is no stone between them; the floor is simply higher");
        assertFalse(sees(arena), "and you cannot see onto a floor above your own");
    }

    /** The same room without the step in it: the same two cells, in plain view. */
    @Test
    void andOnTheSameFloorItIsNotHidden() {
        assertTrue(sees(raisedArena(20, false, 10, 14)));
    }

    /**
     * A staircase is something you can see up.
     *
     * <p>A stair cell's floor climbs across it, so its middle stands half a storey
     * above the room it starts from. Rounded to the nearest that is the upper
     * storey and the stair hides itself — and with it whoever is halfway up. It
     * belongs to the floor it starts from, which is what the client says too.
     */
    @Test
    void aCreatureOnTheStairsIsSeenFromTheFootOfThem() {
        assertTrue(sees(raisedArena(12, true, 10, 11)),
                "he is looking straight up the steps at it");
    }

    // ---- and what he does about it ----

    private static float healthOf(GameObject creature) {
        return creature.getBody().getHealth();
    }

    /**
     * He does not shoot through a wall.
     *
     * <p>The skeleton is well inside his reach and would be the first thing the
     * engine's own acquisition picked. It never loses a point.
     */
    @Test
    void heDoesNotShootWhatHeCannotSee() {
        var arena = arena(13, 10, 15);
        float before = healthOf(arena.skeleton());

        arena.game().runHeadless(200);

        assertEquals(before, healthOf(arena.skeleton()), 0.001f,
                "a wall is not something to shoot through");
        assertFalse(arena.hero().findModule(WeaponUpdate.class).isAttacking(),
                "and not something to take aim at either");
    }

    /** Take the wall away and the same shot lands, so the test above means something. */
    @Test
    void heShootsWhatHeCanSee() {
        var arena = arena(NO_WALL, 10, 15);
        float before = healthOf(arena.skeleton());

        arena.game().runHeadless(200);

        assertTrue(healthOf(arena.skeleton()) < before,
                "with nothing in the way he opens fire on his own");
    }

    /**
     * A bow may be drawn further than his eyes reach, and then the dark is what
     * stops the shot.
     *
     * <p>This is the rule the shipped numbers used to stand in for: the hero's bow
     * was kept shorter than his sight so that nothing had to check the distance,
     * and the two numbers were left to keep each other honest. They are free of
     * each other now — the bow here reaches 200 and the target is 130 off, well
     * inside the bow, twice as far as his eyes, and further than the skeleton's
     * own senses so that it stays where it was put.
     */
    @Test
    void aBowThatOutrangesHisEyesDoesNotFireIntoTheDark() {
        var arena = arena(flatWorld(longBow()), 5, 18);
        float before = healthOf(arena.skeleton());

        arena.game().runHeadless(200);

        assertEquals(before, healthOf(arena.skeleton()), 0.001f,
                "nothing he has not been shown is something to shoot at");
        assertNull(arena.hero().findModule(WeaponUpdate.class).getTarget(),
                "and he has not taken aim at it either");
    }

    /** The same long bow, inside the light: it fires, so the test above is the dark. */
    @Test
    void theSameBowFiresAtWhatTheLightReaches() {
        var arena = arena(flatWorld(longBow()), 10, 16);
        float before = healthOf(arena.skeleton());

        arena.game().runHeadless(200);

        assertTrue(healthOf(arena.skeleton()) < before, "60 units off and lit; he shoots");
    }

    /**
     * And he does not shoot at what is standing on the floor above him.
     *
     * <p>Inside his reach, inside his eyes, nothing but air between — and drawn
     * nowhere, because the client will not show you a storey you have not climbed.
     */
    @Test
    void heDoesNotShootAtTheFloorAboveHim() {
        var arena = raisedArena(12, false, 10, 14);
        float before = healthOf(arena.skeleton());

        arena.game().runHeadless(200);

        assertEquals(before, healthOf(arena.skeleton()), 0.001f,
                "a room he has not climbed into is a room he cannot fight in");
    }

    /**
     * Ordered at something behind a wall, he walks round rather than shooting
     * through it — the order stands, the shot does not.
     */
    @Test
    void anOrderThroughAWallSendsHimWalking() {
        var arena = arena(13, 10, 15);
        var game = arena.game();
        game.postCommand(new GameMessage.AttackObject(game.getLocalPlayerIndex(),
                List.of(arena.hero().getId()), arena.skeleton().getId()));
        float before = healthOf(arena.skeleton());
        float startedAt = arena.hero().getPosition().x();

        game.runHeadless(60);

        assertEquals(before, healthOf(arena.skeleton()), 0.001f, "still no shot through stone");
        assertTrue(arena.hero().getPosition().x() != startedAt,
                "but he sets off to get a look at it");
    }

    // ---- one order at a time ----

    private static void sendHimAcrossTheRoom(Arena arena) {
        var game = arena.game();
        game.postCommand(new GameMessage.MoveTo(game.getLocalPlayerIndex(),
                List.of(arena.hero().getId()), new uz.duke.core.math.Coord3D(at(35), at(15), 0f)));
        game.runHeadless(10);
    }

    @Test
    void anAttackOrderStopsTheWalk() {
        // Close enough to shoot from where he starts, so stopping is the whole
        // answer rather than the first half of walking somewhere else.
        var arena = arena(NO_WALL, 10, 14);
        var game = arena.game();
        var legs = arena.hero().findModule(MoveUpdate.class);
        sendHimAcrossTheRoom(arena);
        assertTrue(legs.isMoving(), "he should be on his way");

        game.postCommand(new GameMessage.AttackObject(game.getLocalPlayerIndex(),
                List.of(arena.hero().getId()), arena.skeleton().getId()));
        game.runHeadless(3);

        assertFalse(legs.isMoving(), "the newer order wins: he stops and shoots");
    }

    /**
     * Cast the way the game casts, on an arena that has no command routing.
     *
     * <p>{@code Dungeon.world} builds the world and the creatures but not the run
     * loop, so a {@code CastSkill} posted here has nobody to route it — that
     * wiring belongs to {@code newSession}. This is the same call the routing
     * would make, one step further in.
     */
    private static void cast(Arena arena, char key) {
        var book = arena.hero().findModule(uz.duke.dungeon.skill.SkillBook.class);
        assertNotNull(book);
        assertTrue(book.cast(key, 1), "the skill should have gone off");
        arena.game().runHeadless(3);
    }

    @Test
    void castingASkillStopsTheWalk() {
        var arena = arena(NO_WALL, 10, 30);
        var legs = arena.hero().findModule(MoveUpdate.class);
        sendHimAcrossTheRoom(arena);
        assertTrue(legs.isMoving());

        // W goes off around him and needs nothing pointed at, so it is the plainest
        // cast there is.
        cast(arena, 'W');

        assertFalse(legs.isMoving(), "he does one thing at a time");
    }

    /**
     * And a cast ends the chase rather than pausing it.
     *
     * <p>Dashing away from something he was sent at is the player changing his
     * mind; walking back afterwards is not what the dash was for.
     */
    @Test
    void castingASkillAbandonsWhatHeWasSentAt() {
        var arena = arena(NO_WALL, 10, 34);
        var game = arena.game();
        var legs = arena.hero().findModule(MoveUpdate.class);
        game.postCommand(new GameMessage.AttackObject(game.getLocalPlayerIndex(),
                List.of(arena.hero().getId()), arena.skeleton().getId()));
        game.runHeadless(10);
        assertTrue(legs.isMoving(), "far away, so he sets off after it");

        cast(arena, 'W');

        assertFalse(legs.isMoving(), "the chase is over, not merely interrupted");
    }

    /** An enemy met on the way is still not a reason to stop — that rule survives. */
    @Test
    void heStillWalksPastWhatHeDidNotOrder() {
        var arena = arena(NO_WALL, 10, 14);
        var game = arena.game();
        var legs = arena.hero().findModule(MoveUpdate.class);

        sendHimAcrossTheRoom(arena);
        game.runHeadless(20);

        assertTrue(legs.isMoving(), "the errand stands; he did not ask to fight it");
        assertNull(arena.hero().findModule(WeaponUpdate.class).getTarget(),
                "and he has not taken aim at it either");
    }

    /** Standing still afterwards, he picks it up on his own. */
    @Test
    void heTakesAimOnceHeHasStopped() {
        var arena = arena(NO_WALL, 10, 14);
        var hero = arena.hero();
        assertNotNull(hero);

        arena.game().runHeadless(5);

        assertEquals(arena.skeleton().getId(),
                hero.findModule(WeaponUpdate.class).getTarget(),
                "standing in front of something he can see, he aims at it");
    }
}
