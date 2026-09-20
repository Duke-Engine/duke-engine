package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.view.UnitView;

/**
 * The portrait's scene: what stands in it, what leaves it, and what happens when
 * there is nothing to draw with.
 *
 * <p>The rendering needs a graphics card and cannot be tested here. What can be —
 * and what would leak a frame buffer a run, or hold a dead hero's model for the
 * rest of a session — is the bookkeeping either side of it: one model in the
 * scene however many times the player clicks between creatures, an empty scene
 * after it is closed, and no exception anywhere on a machine with no display.
 */
class HeroPortraitTest {

    private static final PortraitLook LOOK = new PortraitLook(
            PortraitLook.Camera.DEFAULT,
            new PortraitLook.Clips("Idle_A", "Aim", "Idle_B", "Death_A", "Cheer"),
            30f, 1.5f);

    /** A creature with a skeleton's worth of nothing and a named clip or two. */
    private static Spatial bodyCarrying(String... clips) {
        var body = new Node("body");
        var composer = new AnimComposer();
        for (var clip : clips) {
            composer.addAnimClip(new AnimClip(clip));
        }
        body.addControl(composer);
        return body;
    }

    /** What the client's own body builder does, without an asset manager. */
    private static HeroPortrait.Bodies always(String... clips) {
        return (visual, alsoWanted) -> bodyCarrying(clips);
    }

    private static UnitView creature(int id, String template, float healthFraction) {
        return new UnitView(id, template, 0, 10f, 10f, 0f,
                100f * healthFraction, 100f, false, true, false, false, -1);
    }

    /** Something in the world that the player can never click on: an arrow. */
    private static UnitView unselectable(int id, String template) {
        return new UnitView(id, template, 0, 10f, 10f, 0f,
                1f, 1f, false, false, true, false, -1);
    }

    private static Visuals twoHeroes() {
        return Visuals.create()
                .unit("Rogue", unit -> unit.model("heroes/ranger.glb"))
                .unit("Mage", unit -> unit.model("heroes/mage.glb"))
                .portrait("Rogue", LOOK)
                .portrait("Mage", LOOK);
    }

    /**
     * With no render manager it builds, runs and closes, and draws nothing.
     *
     * <p>Which is what a test run is, and what a machine with no display is. The
     * alternative is every caller asking first whether there is a graphics card,
     * and one of them eventually forgetting to.
     */
    @Test
    void withNothingToDrawWithNothingIsDrawnAndNothingThrows() {
        var portrait = HeroPortrait.none();

        portrait.show(creature(1, "Rogue", 1f), "7-daraja");
        portrait.update(1f / 60f, true);
        portrait.died(1);
        assertNull(portrait.texture(), "there is nothing to draw with");
        portrait.close();
    }

    /** And a portrait for a game that described none stays empty. */
    @Test
    void aCreatureNobodyDescribedKeepsTheDrawing() {
        var portrait = HeroPortrait.open(null,
                Visuals.create().unit("Skeleton", unit -> unit.model("monsters/bones.glb")),
                always("Idle_A"));

        portrait.show(creature(4, "Skeleton", 1f), "");

        assertNull(portrait.standing(), "nothing was described, so nothing stands in the frame");
        assertEquals(0, portrait.scene().getQuantity());
    }

    /**
     * One block gives every selectable creature a face, named or not.
     *
     * <p>The alternative is a block per monster, which is the same paragraph ten
     * times over and an eleventh monster that arrives without one.
     */
    @Test
    void oneBlockGivesEverySelectableCreatureAFace() {
        var everyone = Visuals.create()
                .unit("Skeleton", unit -> unit.model("monsters/bones.glb").idle("Idle_A"))
                .portraits(new PortraitLook(PortraitLook.Camera.DEFAULT,
                        PortraitLook.Clips.NONE, 30f, 1.5f));
        var portrait = HeroPortrait.open(null, everyone, always("Idle_A"));

        portrait.show(creature(4, "Skeleton", 1f), "");

        assertEquals("Skeleton", portrait.standing(),
                "a monster nobody named was left with the drawing");
    }

    /**
     * And nothing else gets one: an arrow is not what the bar is describing.
     *
     * <p>The gate is the engine's own word for "the player can click this", not a
     * list the game has to keep in step — arrows, chests and barrels all have
     * models, and a portrait of a chest is a frame the player can never see and a
     * model loaded for nothing.
     */
    @Test
    void whatCannotBeSelectedGetsNoFace() {
        var everyone = Visuals.create()
                .unit("Arrow", unit -> unit.model("heroes/arrow.gltf").idle("Idle_A"))
                .portraits(new PortraitLook(PortraitLook.Camera.DEFAULT,
                        PortraitLook.Clips.NONE, 30f, 1.5f));
        var portrait = HeroPortrait.open(null, everyone, always("Idle_A"));

        portrait.show(unselectable(9, "Arrow"), "");

        assertNull(portrait.standing(), "an arrow was given a portrait");
        assertEquals(0, portrait.scene().getQuantity());
    }

    /**
     * A creature's own clips are what it wears where the portrait names none.
     *
     * <p>What makes a monster's face free: its idle and its death are already
     * bound on it. Fighting and being hurt fall back to standing rather than to
     * its attack clip, because an attack is one blow and looped it is a monster
     * shadow-boxing — the same mistake the world's own animation paid for.
     */
    @Test
    void aCreatureWearsItsOwnClipsWhereThePortraitNamesNone() {
        var visual = new Visuals.UnitVisual();
        visual.idle("Skeleton_Idle").attack("Skeleton_Swing").die("Death_A");

        var filled = HeroPortrait.filledIn(
                new PortraitLook(PortraitLook.Camera.DEFAULT, PortraitLook.Clips.NONE, 30f, 1.5f),
                visual).clips();

        assertEquals("Skeleton_Idle", filled.calm());
        assertEquals("Skeleton_Idle", filled.fight(), "a looped swing is shadow-boxing");
        assertEquals("Skeleton_Idle", filled.hurt());
        assertEquals("Death_A", filled.dead());
        assertNull(filled.levelUp(), "nothing but a hero has a level to celebrate");
    }

    /** And what the portrait does name wins over them. */
    @Test
    void whatThePortraitNamesWinsOverTheCreaturesOwn() {
        var visual = new Visuals.UnitVisual();
        visual.idle("Skeleton_Idle").die("Death_A");

        var filled = HeroPortrait.filledIn(new PortraitLook(PortraitLook.Camera.DEFAULT,
                new PortraitLook.Clips("Bow_Idle", "Bow_Aiming", null, null, "Cheer"),
                30f, 1.5f), visual).clips();

        assertEquals("Bow_Idle", filled.calm());
        assertEquals("Bow_Aiming", filled.fight());
        assertEquals("Bow_Idle", filled.hurt(), "unnamed falls back to standing, not to its idle");
        assertEquals("Death_A", filled.dead());
        assertEquals("Cheer", filled.levelUp());
    }

    @Test
    void theCreatureTheCardIsAboutStandsInTheFrame() {
        var portrait = HeroPortrait.open(null, twoHeroes(), always("Idle_A"));

        portrait.show(creature(1, "Rogue", 1f), "7-daraja");

        assertEquals("Rogue", portrait.standing());
        assertEquals(1, portrait.scene().getQuantity());
        assertNull(portrait.texture(), "nothing has been drawn into it yet");
    }

    /**
     * Clicking between two creatures does not leave the first one standing there.
     *
     * <p>The leak this class is really about: a scene that grew by one model per
     * click would be invisible for an hour and then be the reason a run runs out
     * of memory.
     */
    @Test
    void swappingTheCreatureDoesNotLeaveTheLastOneStanding() {
        var portrait = HeroPortrait.open(null, twoHeroes(), always("Idle_A"));

        portrait.show(creature(1, "Rogue", 1f), "7-daraja");
        var first = portrait.scene().getChild(0);
        portrait.show(creature(2, "Mage", 1f), "3-daraja");

        assertEquals(1, portrait.scene().getQuantity(), "two heroes are standing in the frame");
        assertEquals("Mage", portrait.standing());
        assertNull(first.getParent(), "the first one is still attached to something");
    }

    /** The same creature twice over is not rebuilt — a model load a click would be. */
    @Test
    void theSameCreatureIsNotBuiltTwice() {
        var portrait = HeroPortrait.open(null, twoHeroes(), always("Idle_A"));

        portrait.show(creature(1, "Rogue", 1f), "7-daraja");
        var built = portrait.scene().getChild(0);
        portrait.show(creature(1, "Rogue", 0.5f), "7-daraja");

        assertSame(built, portrait.scene().getChild(0));
    }

    /** Selecting nothing empties the frame, and the drawing comes back. */
    @Test
    void selectingNothingEmptiesTheFrame() {
        var portrait = HeroPortrait.open(null, twoHeroes(), always("Idle_A"));
        portrait.show(creature(1, "Rogue", 1f), "7-daraja");

        portrait.show(null, "");

        assertNull(portrait.standing());
        assertEquals(0, portrait.scene().getQuantity());
    }

    /** But a death keeps it, because the card empties on the frame he falls. */
    @Test
    void aDeathKeepsTheFrameAlthoughTheCardHasEmptied() {
        var portrait = HeroPortrait.open(null, twoHeroes(), always("Idle_A", "Death_A"));
        portrait.show(creature(1, "Rogue", 0.1f), "7-daraja");

        portrait.died(1);
        portrait.show(null, "");

        assertEquals("Rogue", portrait.standing(), "he was taken out of his own death");
        assertEquals(1, portrait.scene().getQuantity());
    }

    /**
     * But only against nobody: the next creature clicked on takes the frame.
     *
     * <p>The fallen hero keeps the frame against an empty card, and that is the
     * whole of what he keeps it against. Kept against a selection too, the frame
     * would hold a dead man for the rest of the session — through the next
     * creature clicked on, through the next run, through everything.
     */
    @Test
    void aFallenHeroDoesNotKeepTheFrameAgainstTheNextCreature() {
        var portrait = HeroPortrait.open(null, twoHeroes(), always("Idle_A", "Death_A"));
        portrait.show(creature(1, "Rogue", 0.1f), "7-daraja");
        portrait.died(1);
        portrait.show(null, "");

        portrait.show(creature(2, "Mage", 1f), "1-daraja");

        assertEquals("Mage", portrait.standing(), "the frame is still holding the dead hero");
        assertEquals(1, portrait.scene().getQuantity());
    }

    /** And a new run's hero takes it back, rather than the old one's body holding it. */
    @Test
    void aNewRunsHeroTakesTheFrameBack() {
        var portrait = HeroPortrait.open(null, twoHeroes(), always("Idle_A", "Death_A"));
        portrait.show(creature(1, "Rogue", 0.1f), "7-daraja");
        portrait.died(1);
        portrait.show(null, "");

        // Same template, new object: what starting a run again looks like.
        portrait.show(creature(52, "Rogue", 1f), "1-daraja");
        portrait.show(null, "");

        assertNull(portrait.standing(), "the new hero was never let go of");
    }

    /** Something else dying is not his death. */
    @Test
    void anotherCreatureDyingIsNotHisDeath() {
        var portrait = HeroPortrait.open(null, twoHeroes(), always("Idle_A", "Death_A"));
        portrait.show(creature(1, "Rogue", 1f), "7-daraja");

        portrait.died(99);
        portrait.show(null, "");

        assertNull(portrait.standing(), "a skeleton across the room emptied his frame");
    }

    /**
     * A creature that has not been drawn yet hands out no picture.
     *
     * <p>What is in the texture until then is the <em>last</em> creature's face,
     * and with the world held still nothing would redraw it — so a skeleton
     * clicked on during a pause would wear the hero's head until the game was
     * started again. The drawing is what belongs in the frame in the meantime.
     */
    @Test
    void anUndrawnCreatureHandsOutNoPicture() {
        var portrait = HeroPortrait.open(null, twoHeroes(), always("Idle_A"));

        portrait.show(creature(1, "Rogue", 1f), "7-daraja");
        portrait.update(1f / 60f, false); // held still: nothing is drawn
        portrait.show(creature(2, "Mage", 1f), "1-daraja");

        assertNull(portrait.texture(), "the new creature handed out the last one's picture");
    }

    @Test
    void closingEmptiesTheFrame() {
        var portrait = HeroPortrait.open(null, twoHeroes(), always("Idle_A"));
        portrait.show(creature(1, "Rogue", 1f), "7-daraja");

        portrait.close();

        assertEquals(0, portrait.scene().getQuantity());
        assertNull(portrait.standing());
        assertNull(portrait.texture());
    }

    /**
     * A clip the model does not carry falls back to standing rather than stopping.
     *
     * <p>The clip names are in a data file and the clips are in a model, and
     * neither compiler sees the other: a kit with no death animation, or one an
     * exporter renamed, is a line in a file rather than a crash. A portrait that
     * froze would read as the game having hung.
     */
    @Test
    void aClipTheModelDoesNotCarryFallsBackToStanding() {
        var incomplete = bodyCarrying("Idle_A").getControl(AnimComposer.class);
        var whole = bodyCarrying("Idle_A", "Death_A").getControl(AnimComposer.class);

        assertEquals("Idle_A", HeroPortrait.clipOnHand(incomplete, "Death_A", "Idle_A"));
        assertEquals("Death_A", HeroPortrait.clipOnHand(whole, "Death_A", "Idle_A"),
                "a model that carries what was asked for plays it");
    }

    /** And with nothing at all to fall back on it plays nothing, rather than failing. */
    @Test
    void withNothingToFallBackOnItPlaysNothing() {
        var bare = bodyCarrying().getControl(AnimComposer.class);

        assertNull(HeroPortrait.clipOnHand(bare, "Death_A", "Idle_A"));
        assertNull(HeroPortrait.clipOnHand(null, "Death_A", "Idle_A"));
    }

    /** A model that would not load leaves the frame to the drawing. */
    @Test
    void aModelThatWillNotLoadLeavesTheDrawing() {
        HeroPortrait.Bodies none = new HeroPortrait.Bodies() {
            @Override
            public Spatial of(Visuals.UnitVisual visual, Collection<String> alsoWanted) {
                return null;
            }
        };
        var portrait = HeroPortrait.open(null, twoHeroes(), none);

        portrait.show(creature(1, "Rogue", 1f), "7-daraja");

        assertNull(portrait.standing());
        assertEquals(0, portrait.scene().getQuantity());
        assertNull(portrait.texture());
    }

    /** Every clip any of its states may call for is fetched, not only the five. */
    @Test
    void theClipsOnlyThePortraitPlaysAreAskedForToo() {
        var asked = new java.util.ArrayList<String>();
        var portrait = HeroPortrait.open(null, twoHeroes(), (visual, alsoWanted) -> {
            asked.addAll(alsoWanted);
            return bodyCarrying("Idle_A");
        });

        portrait.show(creature(1, "Rogue", 1f), "7-daraja");

        assertNotNull(portrait.standing());
        // Cheer is in no unit's five, so nothing else would ever fetch it.
        org.junit.jupiter.api.Assertions.assertTrue(asked.contains("Cheer"),
                "the portrait's own clips were not asked for: " + asked);
    }
}
