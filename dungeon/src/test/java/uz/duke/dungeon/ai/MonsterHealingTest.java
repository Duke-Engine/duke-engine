package uz.duke.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.content.ShippedBlock;
import uz.duke.dungeon.skill.Skill;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.game.DukeGame;

/**
 * A monster that mends its own: whom it chooses, whom it will not, and that the same
 * room is mended the same way every time.
 *
 * <p>Fought in an open room through the seam the dungeon is built on. The hero stands
 * inside the healer's band and picks no fights; the ones it mends are told to notice
 * nothing, so they stay where they were put at the share of health the test gave them.
 */
class MonsterHealingTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String HEALER = "SkeletonHealer";
    private static final float ROW = 205f;
    private static final int NO_WALL = -1;

    /** An open room 60 cells by 40; with a wall down one column, and a doorway at its far end. */
    private static String room(int wallColumn) {
        var text = new StringBuilder();
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 60; x++) {
                boolean edge = x == 0 || y == 0 || x == 59 || y == 39;
                boolean wall = x == wallColumn && y != 37;
                text.append(edge || wall ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** Somebody hurt, of a kind, standing on the row at {@code x}, left with this share of himself. */
    private record Hurt(String kind, float x, float share) {
    }

    private static Hurt hurt(float x, float share) {
        return new Hurt("Skeleton", x, share);
    }

    private record Ward(DukeGame game, GameObject healer, List<GameObject> patients) {

        GameObject patient(int index) {
            return patients.get(index);
        }
    }

    /** The shipped file, with the ones it mends told to notice nothing and answer no shout. */
    private static DungeonSettings settings(String more) {
        return DungeonSettings.parse("", """
                Monster
                  Name = Skeleton
                  SenseRadius = 1
                  ChaseRadius = 1
                  AlertRadius = 0
                End
                Monster
                  Name = Warden
                  SenseRadius = 1
                  ChaseRadius = 1
                  AlertRadius = 0
                End
                """ + more);
    }

    /** A hero the healer has noticed, standing inside its band; the healer; and the hurt. */
    private static Ward ward(DungeonSettings settings, String map, Hurt... hurt) {
        var arena = Dungeon.world(map, settings);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, ROW);
        game.spawn(HEALER, arena.dungeon(), 200f, ROW);
        for (var one : hurt) {
            game.spawn(one.kind(), arena.dungeon(), one.x(), ROW);
        }
        game.runHeadless(1);
        // A player has an index once the game has started, and not before.
        arena.orders().hold(arena.hero().getIndex(), true);
        var patients = new ArrayList<GameObject>();
        for (var object : game.getLogic().getObjects()) {
            if (object.getBody() != null && object.getPlayerIndex() == arena.dungeon().getIndex()
                    && !object.getTemplate().name().equals(HEALER)) {
                patients.add(object);
            }
        }
        for (int i = 0; i < patients.size(); i++) {
            var body = patients.get(i).getBody();
            body.setHealth(body.getMaxHealth() * hurt[i].share());
        }
        return new Ward(game, first(game, HEALER), List.copyOf(patients));
    }

    private static GameObject first(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private static Skill mending() {
        return SETTINGS.skillsFor(HEALER).get(0);
    }

    /** The shipped healer, mending at another threshold. */
    private static String mendingBelow(int percent) {
        return ShippedBlock.of(HEALER).with("HealBelowPercent", percent).text();
    }

    /** Long enough for it to look, to finish a throw it had started, and for the light to land. */
    private static int oneMending() {
        var healer = SETTINGS.monster(HEALER);
        return healer.repathFrames() + healer.swingFrames() + mending().windUpFrames() + 15;
    }

    /** Every light called down over the next frames, by the frame each appeared. */
    private static List<Integer> lightsCalled(DukeGame game, int frames) {
        var seen = new HashSet<Integer>();
        var when = new ArrayList<Integer>();
        for (int frame = 0; frame < frames; frame++) {
            game.runHeadless(1);
            for (var object : game.getLogic().getObjects()) {
                if (object.getTemplate().name().equals(mending().projectile())
                        && seen.add(object.getId().value())) {
                    when.add(game.getLogic().getFrame());
                }
            }
        }
        return when;
    }

    // ---- whom ----

    @Test
    void itMendsOneOfItsOwnWhoIsBadlyHurt() {
        var ward = ward(settings(""), room(NO_WALL), hurt(235f, 0.3f));
        float before = ward.patient(0).getBody().getHealth();

        ward.game().runHeadless(oneMending());

        assertEquals(before + mending().heal(), ward.patient(0).getBody().getHealth(), 0.01f,
                "a skeleton at a third of itself, in reach and in plain sight");
    }

    /** Never on the whole and never on the barely hurt: either would be a mending wasted. */
    @Test
    void itLeavesTheWholeAndTheBarelyHurtAlone() {
        float threshold = mending().healBelowPercent() / 100f;
        var ward = ward(settings(""), room(NO_WALL), hurt(230f, 1f), hurt(245f, threshold + 0.05f));
        float whole = ward.patient(0).getBody().getHealth();
        float scratched = ward.patient(1).getBody().getHealth();

        var called = lightsCalled(ward.game(), 300);

        assertTrue(called.isEmpty(), "light was called down on nobody who needed it: " + called);
        assertEquals(whole, ward.patient(0).getBody().getHealth(), 0.01f);
        assertEquals(scratched, ward.patient(1).getBody().getHealth(), 0.01f);
        assertTrue(ward.healer().findModule(SkillBook.class).isReady(mending().key()),
                "and its cooldown was never spent");
    }

    @Test
    void ofTwoHurtItMendsTheWorse() {
        var ward = ward(settings(""), room(NO_WALL), hurt(230f, 0.5f), hurt(245f, 0.25f));
        float half = ward.patient(0).getBody().getHealth();
        float quarter = ward.patient(1).getBody().getHealth();

        ward.game().runHeadless(oneMending());

        assertEquals(half, ward.patient(0).getBody().getHealth(), 0.01f, "the less hurt one waits");
        assertEquals(quarter + mending().heal(), ward.patient(1).getBody().getHealth(), 0.01f,
                "and the worse one is mended first");
    }

    /** Worse as a share of himself: a boss down more points is not worse than a skeleton at half. */
    @Test
    void worstHurtIsCountedAgainstHisOwnHealth() {
        var ward = ward(settings(""), room(NO_WALL), hurt(228f, 0.5f),
                new Hurt("Warden", 250f, 0.45f));
        float skeleton = ward.patient(0).getBody().getHealth();
        float warden = ward.patient(1).getBody().getHealth();
        assertTrue(ward.patient(1).getBody().getMaxHealth() - warden
                > ward.patient(0).getBody().getMaxHealth() - skeleton, "the Warden is down more points");

        ward.game().runHeadless(oneMending());

        assertEquals(skeleton, ward.patient(0).getBody().getHealth(), 0.01f);
        assertEquals(warden + mending().heal(), ward.patient(1).getBody().getHealth(), 0.01f,
                "at 45% of himself the Warden is worse hurt than a skeleton at half");
    }

    /** As hurt as each other: the one the world made first, wherever the two are standing. */
    @Test
    void ofTwoAsHurtItMendsTheOneTheWorldMadeFirst() {
        for (boolean nearerFirst : new boolean[] {true, false}) {
            var ward = ward(settings(""), room(NO_WALL),
                    hurt(nearerFirst ? 230f : 250f, 0.4f), hurt(nearerFirst ? 250f : 230f, 0.4f));
            float first = ward.patient(0).getBody().getHealth();
            float second = ward.patient(1).getBody().getHealth();

            ward.game().runHeadless(oneMending());

            assertEquals(first + mending().heal(), ward.patient(0).getBody().getHealth(), 0.01f,
                    "the one made first, standing " + (nearerFirst ? "nearer" : "further"));
            assertEquals(second, ward.patient(1).getBody().getHealth(), 0.01f);
        }
    }

    @Test
    void itNeverMendsItself() {
        var ward = ward(settings(""), room(NO_WALL));
        // The one arrow he loosed before he was told to hold lands first: at a fifth of
        // itself the healer would not live through it, and a dead healer mends nobody.
        ward.game().runHeadless(30);
        var body = ward.healer().getBody();
        body.setHealth(body.getMaxHealth() * 0.2f);
        float before = body.getHealth();

        assertTrue(lightsCalled(ward.game(), 200).isEmpty(), "it called its light down on itself");
        assertEquals(before, body.getHealth(), 0.01f);
    }

    /** Only through air, as everything down here: the same two, and a wall between. */
    @Test
    void aWallBetweenThemKeepsTheLightOffHim() {
        var walled = ward(settings(""), room(22), hurt(245f, 0.3f));
        float before = walled.patient(0).getBody().getHealth();

        assertTrue(lightsCalled(walled.game(), 200).isEmpty(),
                "light was called down through the stone at column 22");
        assertEquals(before, walled.patient(0).getBody().getHealth(), 0.01f);

        var open = ward(settings(""), room(NO_WALL), hurt(245f, 0.3f));
        open.game().runHeadless(oneMending());
        assertEquals(before + mending().heal(), open.patient(0).getBody().getHealth(), 0.01f,
                "and without the wall he is mended");
    }

    // ---- when ----

    /** The light lies a moment first, and the health comes back when it lands, not before. */
    @Test
    void theMendingLandsWithTheLightAMomentAfterItIsCalled() {
        var ward = ward(settings(""), room(NO_WALL), hurt(235f, 0.3f));
        var patient = ward.patient(0);
        float before = patient.getBody().getHealth();
        int called = -1;
        int landed = -1;
        for (int frame = 0; frame < oneMending() + 30 && landed < 0; frame++) {
            ward.game().runHeadless(1);
            boolean lying = ward.game().getLogic().getObjects().stream().anyMatch(object ->
                    object.getTemplate().name().equals(mending().projectile())
                            && !object.isDestroyed());
            if (lying) {
                called = called < 0 ? frame : called;
                assertEquals(before, patient.getBody().getHealth(), 0.01f,
                        "mended on frame " + frame + ", while the light was still lying there");
            } else if (called >= 0) {
                landed = frame;
            }
        }

        assertTrue(called >= 0 && landed > called, "no light came down at all");
        assertTrue(Math.abs(landed - called - mending().windUpFrames()) <= 1,
                "it lay " + (landed - called) + " frames against a wind-up of " + mending().windUpFrames());
        assertEquals(before + mending().heal(), patient.getBody().getHealth(), 0.01f,
                "and when it landed, he was mended");
    }

    @Test
    void itWaitsForItsCooldownBetweenMendings() {
        var ward = ward(settings(""), room(NO_WALL), hurt(230f, 0.05f), hurt(245f, 0.05f));
        int cooldown = mending().cooldownFrames();

        var called = lightsCalled(ward.game(), cooldown + oneMending());

        assertEquals(2, called.size(), "two badly hurt and one cooldown between them: " + called);
        assertTrue(called.get(1) - called.get(0) >= cooldown,
                "the second came " + (called.get(1) - called.get(0)) + " frames after the first");
    }

    // ---- the file, and the same every time ----

    /** How hurt is hurt enough is the file's to say. */
    @Test
    void howHurtIsHurtEnoughIsTheFiles() {
        var shipped = ward(settings(""), room(NO_WALL), hurt(235f, 0.8f));
        float before = shipped.patient(0).getBody().getHealth();
        shipped.game().runHeadless(oneMending());
        assertEquals(before, shipped.patient(0).getBody().getHealth(), 0.01f,
                "at four fifths of himself he is not below " + mending().healBelowPercent() + "%");

        var generous = ward(settings(mendingBelow(90)), room(NO_WALL), hurt(235f, 0.8f));
        generous.game().runHeadless(oneMending());
        var body = generous.patient(0).getBody();
        assertEquals(Math.min(body.getMaxHealth(), before + mending().heal()), body.getHealth(), 0.01f,
                "and with 90% in the file he is");
    }

    @Test
    void theSameWardIsMendedTheSameEveryTime() {
        assertEquals(checksums(), checksums());
    }

    private static String checksums() {
        var ward = ward(settings(""), room(NO_WALL), hurt(230f, 0.2f), hurt(245f, 0.35f));
        var line = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            ward.game().runHeadless(30);
            line.append(ward.game().getLogic().checksum()).append('|');
        }
        return line.toString();
    }
}
