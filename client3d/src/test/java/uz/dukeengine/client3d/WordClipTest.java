package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.dukeengine.client3d.Visuals.ClipMode.HOLD;
import static uz.dukeengine.client3d.Visuals.ClipMode.LOOP;
import static uz.dukeengine.client3d.Visuals.ClipMode.LOOP_BACKWARDS;
import static uz.dukeengine.client3d.Visuals.ClipMode.ONCE;
import static uz.dukeengine.client3d.Visuals.ClipMode.ONCE_BACKWARDS;
import static uz.dukeengine.client3d.Visuals.ClipStart.FIRST;
import static uz.dukeengine.client3d.Visuals.ClipStart.LAST;
import static uz.dukeengine.client3d.Visuals.ClipStart.RANDOM;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.TransformTrack;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A clip chosen by the words a thing holds, where it stands frame by frame of the game: a second is 30 of them. */
class WordClipTest {

    private static final double SECOND = 1.0;
    private static final double END = Math.nextDown(SECOND);

    /** A war factory's door: the one clip, opened once, held open, and run back shut. */
    private static Visuals.UnitVisual door() {
        return Visuals.create().unit("WarFactory", look -> look.model("models/abwarfact.glb")
                .clip(Set.of("DOOR_1_OPENING"), "ABWarFact_A8", ONCE, FIRST, null)
                .clip(Set.of("DOOR_1_WAITING_OPEN"), "ABWarFact_A8", HOLD, LAST, null)
                .clip(Set.of("DOOR_1_CLOSING"), "ABWarFact_A8", ONCE_BACKWARDS, LAST, null)).of("WarFactory");
    }

    @Test
    void aDoorOpensOnceFromItsFirstFrameIsHeldOnItsLastAndRunsBackShut() {
        var look = door();
        var clip = new WordClip();

        clip.choose(look.clipStateFor(Set.of("DOOR_1_OPENING")), look.clipStates, SECOND, 100, 7);
        assertEquals(0, clip.timeAt(100), "the first frame, the frame the word is set");
        assertEquals(0.5, clip.timeAt(115), 1e-6);
        assertEquals(END, clip.timeAt(130), "and on its last");
        assertEquals(END, clip.timeAt(200), "where it stays");

        clip.choose(look.clipStateFor(Set.of("DOOR_1_WAITING_OPEN")), look.clipStates, SECOND, 230, 7);
        assertEquals(END, clip.timeAt(230), "held on its last frame");
        assertEquals(END, clip.timeAt(900));

        clip.choose(look.clipStateFor(Set.of("DOOR_1_CLOSING")), look.clipStates, SECOND, 1000, 7);
        assertEquals(END, clip.timeAt(1000), "from its last frame");
        assertEquals(0.5, clip.timeAt(1015), 1e-6, "backwards");
        assertEquals(0, clip.timeAt(1030), "back to its first");
        assertEquals(0, clip.timeAt(1100), "and there it stays");
    }

    @Test
    void aLoopGoesRoundEitherWay() {
        var look = Visuals.create().unit("Radar", l -> l.model("models/radar.glb")
                .clip(Set.of("SPINNING"), "Spin", LOOP, null, null)
                .clip(Set.of("UNWINDING"), "Spin", LOOP_BACKWARDS, null, null)).of("Radar");
        var clip = new WordClip();
        clip.choose(look.clipStateFor(Set.of("SPINNING")), look.clipStates, SECOND, 0, 1);
        assertEquals(0.5, clip.timeAt(45), 1e-6, "a second and a half is half way round again");
        clip.choose(look.clipStateFor(Set.of("UNWINDING")), look.clipStates, SECOND, 100, 1);
        assertEquals(END, clip.timeAt(100), "backwards starts at its end");
        assertEquals(0.5, clip.timeAt(115), 1e-6);
        assertEquals(0.5, clip.timeAt(145), 1e-6, "and goes round again");
    }

    @Test
    void twoStatesSharingAKeepGroupHandTheFrameOverAndAnyOtherStartsAgain() {
        var look = Visuals.create().unit("Tank", l -> l.model("models/tank.glb")
                .clip(Set.of("MOVING"), "Drive", LOOP, null, "move")
                .clip(Set.of("MOVING", "REALLYDAMAGED"), "DriveDamaged", LOOP, null, "move")
                .clip(Set.of("MOVING", "SNOW"), "DriveSnow", LOOP, null, null)).of("Tank");
        var clip = new WordClip();
        clip.choose(look.clipStateFor(Set.of("MOVING")), look.clipStates, SECOND, 0, 3);
        clip.choose(look.clipStateFor(Set.of("MOVING", "REALLYDAMAGED")), look.clipStates, 2 * SECOND, 9, 3);
        assertEquals(0.6, clip.timeAt(9), 1e-6, "three tenths through the one, three tenths through the other");
        assertEquals(1.1, clip.timeAt(24), 1e-6, "and on from there");

        clip.choose(look.clipStateFor(Set.of("MOVING", "SNOW")), look.clipStates, SECOND, 30, 3);
        assertEquals(0, clip.timeAt(30), "no group in common: from its start");
    }

    @Test
    void aRandomStartIsTheSameOnEveryMachineAndInsideTheClip() {
        var look = Visuals.create().unit("Tree", l -> l.model("models/tree.glb")
                .clip(Set.of(), "Sway", LOOP, RANDOM, null)).of("Tree");
        var here = new WordClip();
        var there = new WordClip();
        here.choose(look.clipStateFor(Set.of()), look.clipStates, SECOND, 40, 12);
        there.choose(look.clipStateFor(Set.of()), look.clipStates, SECOND, 40, 12);
        assertEquals(here.timeAt(40), there.timeAt(40), "the same thing, the same frame: the same start");
        assertTrue(here.timeAt(40) >= 0 && here.timeAt(40) < SECOND);
    }

    @Test
    void theModelShowsTheFrameTheGameIsAtHoldingItsLastWhateverTheWindowDoes() {
        var door = new Node("Door");
        var factory = new Node("WarFactory");
        factory.attachChild(door);
        var swing = new AnimClip("ABWarFact_A8");
        swing.setTracks(new AnimTrack<?>[] {new TransformTrack(door, new float[] {0f, 1f},
                new Vector3f[] {new Vector3f(), new Vector3f(10f, 0f, 0f)},
                null, null)});
        var composer = new AnimComposer();
        factory.addControl(composer);
        composer.addAnimClip(swing);
        var clip = new WordClip();
        clip.choose(1, door().clipStates, swing.getLength(), 50, 7); // held open, on its last frame

        // What the client does each frame it draws.
        composer.setCurrentAction("ABWarFact_A8", AnimComposer.DEFAULT_LAYER, true).setSpeed(0);
        composer.setTime(AnimComposer.DEFAULT_LAYER, clip.timeAt(50));
        composer.update(0.5f); // half a second of the window's own time
        assertEquals(10f, door.getLocalTranslation().x, 1e-3f, "its last frame: not wrapped round to its first");
        composer.update(0.5f);
        assertEquals(10f, door.getLocalTranslation().x, 1e-3f, "and not moved by the window's time");
    }

    @Test
    void aThingGivenNoClipsByWordsOrHoldingNoneOfThemPlaysItsRoles() {
        var plain = Visuals.create().unit("Ranger", l -> l.model("models/ranger.glb").idle("Idle")).of("Ranger");
        assertEquals(-1, plain.clipStateFor(Set.of("DOOR_1_OPENING")), "no clips by words at all");
        assertEquals(-1, door().clipStateFor(Set.of("DAMAGED")), "none of its words named: its idle, as today");

        var clip = new WordClip();
        clip.choose(0, door().clipStates, SECOND, 5, 7);
        assertTrue(clip.choose(-1, door().clipStates, 0, 6, 7), "leaving a clip chosen by words is a change");
        assertEquals(0, clip.timeAt(6));
    }

    /**
     * A state that names no clip, as the reference's damaged power plant's: holding DAMAGED, its model plays nothing,
     * nothing is said missing, and its idle does not stand in; holding nothing it plays A.
     */
    @Test
    void aStateThatNamesNoClipLeavesTheModelStillAndItsRolesOut() {
        var look = Visuals.create().unit("PowerPlant", l -> l.model("models/power.glb").idle("Idle")
                .clip(Set.of(), "A", LOOP, null, null)
                .clip(Set.of("DAMAGED"), null, LOOP, null, null)).of("PowerPlant");
        var fan = new Node("Fan");
        var plant = new Node("PowerPlant");
        plant.attachChild(fan);
        var composer = new AnimComposer();
        plant.addControl(composer);
        for (var name : java.util.List.of("A", "Idle")) {
            var spin = new AnimClip(name);
            spin.setTracks(new AnimTrack<?>[] {new TransformTrack(fan, new float[] {0f, 1f},
                    new Vector3f[] {new Vector3f(), new Vector3f(10f, 0f, 0f)}, null, null)});
            composer.addAnimClip(spin);
        }
        var clip = new WordClip();
        var missing = new java.util.ArrayList<String>();

        assertEquals("A", WordClip.playOn(composer, clip, null, look, Set.of(), 0, 7, missing::add), "it plays A");
        var damaged = WordClip.playOn(composer, clip, "A", look, Set.of("DAMAGED"), 10, 7, missing::add);

        org.junit.jupiter.api.Assertions.assertNull(composer.getCurrentAction(), "its model plays nothing");
        org.junit.jupiter.api.Assertions.assertNotNull(damaged, "its words chose: its idle does not stand in");
        assertTrue(clip.chosen());
        assertTrue(missing.isEmpty(), "nothing said missing: " + missing);
        assertEquals("A", WordClip.playOn(composer, clip, damaged, look, Set.of(), 20, 7, missing::add),
                "and A again, holding nothing");
    }

    /**
     * A moment's word chosen by as any other word it holds, the moving word named MOVING: moving, RUN; moving and
     * damaged, LIMP; standing, no state fits and its idle plays — its roles only where none fits.
     */
    @Test
    void theMovingWordChoosesItsRunMovingHurtItsLimpAndStandingItsIdle() {
        var look = Visuals.create().unit("Ranger", l -> l.model("models/ranger.glb").idle("Idle")
                .clip(Set.of("MOVING"), "RUN", LOOP, null, null)
                .clip(Set.of("MOVING", "DAMAGED"), "LIMP", LOOP, null, null)).of("Ranger");

        assertEquals("RUN", look.clipStates.get(look.clipStateFor(Set.of("MOVING"))).clip());
        assertEquals("LIMP", look.clipStates.get(look.clipStateFor(Set.of("MOVING", "DAMAGED"))).clip());
        assertEquals(-1, look.clipStateFor(Set.of("DAMAGED")), "standing: none fits, and its idle plays");
    }

    // ---- several clips ----

    private static AnimComposer composerWith(java.util.Map<String, Float> clips) {
        var part = new Node("Part");
        var model = new Node("Model");
        model.attachChild(part);
        var composer = new AnimComposer();
        model.addControl(composer);
        clips.forEach((name, seconds) -> {
            var clip = new AnimClip(name);
            clip.setTracks(new AnimTrack<?>[] {new TransformTrack(part, new float[] {0f, seconds},
                    new Vector3f[] {new Vector3f(), new Vector3f(1f, 0f, 0f)}, null, null)});
            composer.addAnimClip(clip);
        });
        return composer;
    }

    /** The reference's stance, 35 times in 37, and two fidgets: over 3700 entries of the state, 3500 of the stance. */
    @Test
    void weightsThirtyFiveOneAndOneOver3700EntriesDrawTheFirst3500Times() {
        var look = Visuals.create().unit("Ranger", l -> l.model("models/ranger.glb")
                .clip(Set.of(), java.util.List.of(new Visuals.Pick("STA", 35), new Visuals.Pick("IDA"),
                        new Visuals.Pick("IDB")), false, LOOP, null, null)).of("Ranger");
        int stance = 0;
        for (int entry = 0; entry < 3700; entry++) {
            if ("STA".equals(new WordClip().pickFor(0, look.clipStates.getFirst(), 30 + entry, entry))) {
                stance++;
            }
        }
        assertEquals(3500, stance, 150);
    }

    /** Idles of A, a second long, and B, half of one: A ends and B plays, B ends and A plays, on the game's frames. */
    @Test
    void anIdleStateOfTwoClipsPlaysTheOtherAsEachEnds() {
        var look = Visuals.create().unit("Ranger", l -> l.model("models/ranger.glb")
                .clip(Set.of(), java.util.List.of(new Visuals.Pick("A"), new Visuals.Pick("B")), true, LOOP, null,
                        null)).of("Ranger");
        var composer = composerWith(java.util.Map.of("A", 1f, "B", 0.5f));
        var clip = new WordClip();
        var played = new java.util.ArrayList<String>();
        for (int frame = 0; frame <= 150; frame++) {
            played.add(WordClip.playOn(composer, clip, played.isEmpty() ? null : played.getLast(), look, Set.of(),
                    frame, 7, missing -> { }));
        }

        int start = 0;
        for (int frame = 1; frame <= 150; frame++) {
            if (!played.get(frame).equals(played.get(frame - 1))) {
                int lasted = frame - start;
                assertEquals(played.get(frame - 1).equals("A") ? 30 : 15, lasted,
                        played.get(frame - 1) + " played out, from frame " + start);
                start = frame;
            }
        }
        assertTrue(played.contains("A") && played.contains("B"), "both played: " + played);
    }

    /** Two machines draw the same clip, idle and death for the same thing entering the same state on the same frame. */
    @Test
    void twoClientsDrawTheSameClipForTheSameThingOnTheSameFrame() {
        var picks = java.util.List.of(new Visuals.Pick("DTA"), new Visuals.Pick("DTB"), new Visuals.Pick("DTC"));
        var look = Visuals.create().unit("Ranger", l -> l.model("models/ranger.glb").idle(picks).die(null, picks)
                .clip(Set.of("MOVING"), picks, false, LOOP, null, null)).of("Ranger");
        var clips = java.util.Map.of("DTA", 1f, "DTB", 1f, "DTC", 1f);

        for (int thing = 1; thing <= 20; thing++) {
            var here = WordClip.playOn(composerWith(clips), new WordClip(), null, look, Set.of("MOVING"), 90, thing,
                    missing -> { });
            var there = WordClip.playOn(composerWith(clips), new WordClip(), null, look, Set.of("MOVING"), 90, thing,
                    missing -> { });
            assertEquals(here, there, "thing " + thing);
            assertEquals(look.idleFor(thing, 90), look.idleFor(thing, 90));
            assertEquals(look.dieAnimFor(null, thing, 90), look.dieAnimFor(null, thing, 90));
        }
    }

    // ---- paced to its speed ----

    /**
     * A clip a second long one play of which carries the thing 30, the reference's {@code [AIRngr_RNA 30]}: at 60 a
     * second it plays twice a second, at 15 half a time, and standing still at its own pace — where it is in its clip
     * kept as its speed changes.
     */
    @Test
    void aClipCoveringThirtyUnitsPlaysAtTheRateThatCoversThemAtTheThingsSpeed() {
        var look = Visuals.create().unit("Ranger", l -> l.model("models/ranger.glb")
                .clip(Set.of(), "RUN", LOOP, FIRST, null, 30f)).of("Ranger");
        var clip = new WordClip();
        clip.choose(0, look.clipStates, SECOND, 0, 7);

        clip.pace(60f, 0);
        assertEquals(0.2, clip.timeAt(3), 1e-6, "twice a second at 60");
        clip.pace(15f, 3);
        assertEquals(0.2 + 0.5, clip.timeAt(33), 1e-6, "half a time a second at 15, on from where it was");
        clip.pace(0f, 33);
        assertEquals(0.7 + 0.5, clip.timeAt(48) + SECOND, 1e-6, "standing still, at its own pace");

        assertEquals(2.0, Visuals.paced(1.0, 30f, 60f, 1.0), 1e-9, "a walk's rate the same way");
        assertEquals(1.0, Visuals.paced(1.0, 30f, 0f, 1.0), 1e-9);
    }

    // ---- at a speed ----

    @Test
    void aLoopingClipASecondLongAtSpeedTwoLapsInHalfASecondOfTheGamesFrames() {
        var look = Visuals.create().unit("Chinook", l -> l.model("models/chinook.glb")
                .clip(Set.of(), "Idle", LOOP, FIRST, null, 2f, 2f)).of("Chinook");
        var clip = new WordClip();
        clip.choose(look.clipStateFor(Set.of()), look.clipStates, SECOND, 0, 7);

        assertEquals(2 * 7 / 30.0, clip.timeAt(7), 1e-6, "twice its pace");
        assertEquals(0, clip.timeAt(15), 1e-6, "round once in half a second");
    }

    @Test
    void withNineTenthsToElevenTenthsTwoThingsPlayAtTheirOwnSpeedEachBetweenTheTwo() {
        var look = Visuals.create().unit("Ranger", l -> l.model("models/ranger.glb")
                .clip(Set.of(), "Idle", LOOP, FIRST, null, 0.9f, 1.1f)).of("Ranger");
        var one = new WordClip();
        var two = new WordClip();
        one.choose(look.clipStateFor(Set.of()), look.clipStates, SECOND, 0, 7);
        two.choose(look.clipStateFor(Set.of()), look.clipStates, SECOND, 0, 8);

        assertTrue(one.speed() >= 0.9 && one.speed() <= 1.1, "between the two: " + one.speed());
        assertTrue(two.speed() >= 0.9 && two.speed() <= 1.1, "between the two: " + two.speed());
        assertTrue(one.speed() != two.speed(), "each at its own pace");
    }

    // ---- standing still ----

    /** A power plant's fans, a layer of their own, standing still while their side is short of power. */
    private static Visuals.UnitVisual fans(boolean namesTheWord) {
        return Visuals.create().unit("PowerPlant", l -> {
            var fans = l.model("models/power.glb").layer("Fans").model("models/fans.glb")
                    .clip(Set.of(), "Spin", LOOP, FIRST, null);
            if (namesTheWord) {
                fans.stillWhile(Set.of("UNDERPOWERED"));
            }
        }).of("PowerPlant").layers.get("Fans");
    }

    @Test
    void aLoopingClipStandsAtTheFrameItReachedWhileItsThingHoldsTheWordAndGoesOnFromThere() {
        var look = fans(true);
        var composer = composerWith(java.util.Map.of("Spin", 1f));
        var clip = new WordClip();
        var none = Set.<String>of();
        var short_ = Set.of("UNDERPOWERED");

        WordClip.playOn(composer, clip, null, look, none, 0, 7, name -> { });
        WordClip.playOn(composer, clip, "Spin", look, none, 12, 7, name -> { });
        assertEquals(0.4, composer.getTime(AnimComposer.DEFAULT_LAYER), 1e-6);
        WordClip.playOn(composer, clip, "Spin", look, short_, 12, 7, name -> { });
        WordClip.playOn(composer, clip, "Spin", look, short_, 100, 7, name -> { });
        assertEquals(0.4, composer.getTime(AnimComposer.DEFAULT_LAYER), 1e-6, "stood where it was");

        WordClip.playOn(composer, clip, "Spin", look, none, 200, 7, name -> { });
        assertEquals(0.4, composer.getTime(AnimComposer.DEFAULT_LAYER), 1e-6, "the word off, from that frame");
        WordClip.playOn(composer, clip, "Spin", look, none, 209, 7, name -> { });
        assertEquals(0.7, composer.getTime(AnimComposer.DEFAULT_LAYER), 1e-6, "and on");
    }

    @Test
    void aLayerNamingNoWordPlaysOn() {
        var look = fans(false);
        var composer = composerWith(java.util.Map.of("Spin", 1f));
        var clip = new WordClip();
        WordClip.playOn(composer, clip, null, look, Set.of("UNDERPOWERED"), 0, 7, name -> { });
        WordClip.playOn(composer, clip, "Spin", look, Set.of("UNDERPOWERED"), 12, 7, name -> { });
        assertEquals(0.4, composer.getTime(AnimComposer.DEFAULT_LAYER), 1e-6);
    }
}
