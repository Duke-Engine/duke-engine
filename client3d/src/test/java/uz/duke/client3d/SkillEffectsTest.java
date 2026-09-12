package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;

/**
 * The rings a skill opens: that they are a pool, that they obey their ceiling,
 * and that the curve in them is a curve.
 *
 * <p>Runs headless. {@code DesktopAssetManager} loads a material definition
 * without a window, so the rings here are the real thing rather than a stand-in.
 *
 * <p>Nothing here asserts that anything looks good, which is not a thing a test
 * can say. What it guards is the three ways this could go wrong silently: a pool
 * that is not a pool, a ceiling that does not hold, and an "ease" that is a
 * straight line with a name on it.
 */
class SkillEffectsTest {

    private static final int CAP = 3;

    private static final Coord3D SOMEWHERE = new Coord3D(100f, 100f, 0f);
    private static final Vector3f EYE = new Vector3f(100f, 40f, 100f);

    private record Scene(SkillEffects effects, Node root) {
    }

    private static Scene scene() {
        return scene(visuals -> visuals.effect("Boom", recipe -> recipe
                .kind(Visuals.EffectVisual.SHOCKWAVE)
                .colours(java.awt.Color.WHITE, java.awt.Color.BLACK)
                .wave(2f, 30f, 0.4f, 2.4f, 1f, 0.2f)));
    }

    private static Scene scene(java.util.function.Consumer<Visuals> art) {
        var visuals = Visuals.create();
        art.accept(visuals);
        var root = new Node("effects");
        return new Scene(new SkillEffects(new DesktopAssetManager(true), root, visuals, null,
                RangeLook.DEFAULT, CAP, 0f), root);
    }

    private static void run(Scene scene, float seconds) {
        for (float gone = 0f; gone < seconds; gone += 1f / 60f) {
            scene.effects().update(1f / 60f, (x, y) -> 0f);
        }
    }

    // ---- the pool ----

    /**
     * A ring is built once and lent out again.
     *
     * <p>The thing this guards is a leak, and a leak of exactly the shape that
     * never shows up in a bug report: a {@link GroundRing} owns a vertex buffer
     * and two materials, a fight is thirty casts, and nothing about a scene graph
     * says "this is getting heavier" out loud — it just gets slower.
     */
    @Test
    void ringsAreLentOutRatherThanBuilt() {
        var scene = scene();
        for (int cast = 0; cast < 20; cast++) {
            scene.effects().cast("Boom", SOMEWHERE, EYE, 0f, (x, y) -> 0f);
            run(scene, 0.5f); // let it finish, so the ring goes back
        }

        assertEquals(1, scene.effects().madeSoFar(),
                "twenty casts one after another built more than one ring");
    }

    /** And the ceiling holds when they overlap instead of following each other. */
    @Test
    void theCeilingHolds() {
        var scene = scene();
        for (int cast = 0; cast < 20; cast++) {
            scene.effects().cast("Boom", SOMEWHERE, EYE, 0f, (x, y) -> 0f);
        }

        assertEquals(CAP, scene.effects().openCount(), "more rings are open than the cap allows");
        assertTrue(scene.effects().madeSoFar() <= CAP,
                "the pool built " + scene.effects().madeSoFar() + " for a cap of " + CAP);
    }

    /** Over the ceiling the skill loses a decoration, not its cast. */
    @Test
    void pastTheCeilingItSimplyDrawsNothing() {
        var scene = scene();
        for (int cast = 0; cast < CAP + 5; cast++) {
            scene.effects().cast("Boom", SOMEWHERE, EYE, 0f, (x, y) -> 0f);
        }
        run(scene, 0.5f);

        assertEquals(0, scene.effects().openCount(), "they never closed");
    }

    /** A world rebuilt has nothing left open in it. */
    @Test
    void clearingTakesThemAllBack() {
        var scene = scene();
        scene.effects().cast("Boom", SOMEWHERE, EYE, 0f, (x, y) -> 0f);
        scene.effects().clear();

        assertEquals(0, scene.effects().openCount());
        scene.effects().cast("Boom", SOMEWHERE, EYE, 0f, (x, y) -> 0f);
        assertEquals(1, scene.effects().madeSoFar(), "the cleared one was thrown away");
    }

    // ---- the curve ----

    /**
     * The ring leaps and then slows, which is the whole of why it reads as an
     * impact rather than as a circle being resized.
     *
     * <p>Asserted against the straight line rather than against a number: at any
     * ease above 1 the ring has to be WIDER at the halfway point than an evenly
     * opening one would be, and that is the whole claim. A version of this that
     * pinned {@code widthAt(0.5)} to 21.3 would break every time a designer
     * touched the number and would say nothing about whether the curve is a
     * curve.
     */
    @Test
    void theRingLeapsAndThenSlows() {
        float even = SkillEffects.widthAt(0f, 100f, 0.5f, 1f);
        float eased = SkillEffects.widthAt(0f, 100f, 0.5f, 2.4f);

        assertEquals(50f, even, 0.01f, "an ease of 1 is a straight line, which is the baseline");
        assertTrue(eased > even + 10f,
                "half way through it is " + eased + " wide against a straight line's " + even
                        + " — which is a circle being resized with a curve's name on it");
    }

    /** And it still starts where it starts and ends where it ends. */
    @Test
    void theCurveKeepsBothEnds() {
        assertEquals(4f, SkillEffects.widthAt(4f, 40f, 0f, 2.4f), 0.01f);
        assertEquals(40f, SkillEffects.widthAt(4f, 40f, 1f, 2.4f), 0.01f);
        assertEquals(40f, SkillEffects.widthAt(4f, 40f, 9f, 2.4f), 0.01f, "and is clamped past it");
    }

    // ---- reading the line the game sends ----

    /** A cast is read out of the middle of whatever else the line carries. */
    @Test
    void aCastIsReadOutOfTheLine() {
        var casts = SkillEffects.castsIn(
                "name=Erika|hp=1/2|cast=FrostNova,412,150.5,160.25,40.0|note=found a sword", 0);

        assertEquals(1, casts.size());
        assertEquals("FrostNova", casts.get(0).look());
        assertEquals(412, casts.get(0).frame());
        assertEquals(150.5f, casts.get(0).at().x(), 0.001f);
        assertEquals(160.25f, casts.get(0).at().y(), 0.001f);
        assertEquals(40f, casts.get(0).radius(), 0.001f);
    }

    /**
     * Who the mark belongs to and who cast it are two different answers.
     *
     * <p>They are the same for a guard, which is drawn round the man who cast it,
     * and different for everything aimed away from him: a meteor's mark belongs
     * to the patch of floor it is going to land on, and nobody stands there. So a
     * line carrying only the first cannot say which creature to draw making the
     * gesture — which is the whole reason for the second.
     */
    @Test
    void whoItIsOnAndWhoCastItAreAskedSeparately() {
        var meteor = SkillEffects.castsIn(
                "name=Lira|cast=MeteorCall,900,150.0,160.0,44.0,0,7", 0).get(0);

        assertEquals(SkillEffects.NOBODY, meteor.on(), "a meteor's mark is the floor's");
        assertEquals(7, meteor.by(), "and the mage is still the one who called it");
    }

    /** A line from before either field existed is read as belonging to nobody. */
    @Test
    void anOlderLineIsStillRead() {
        var casts = SkillEffects.castsIn("name=Erika|cast=FrostNova,412,150.0,150.0,40.0", 0);

        assertEquals(1, casts.size(), "the ring is the part that must not be lost");
        assertEquals(SkillEffects.NOBODY, casts.get(0).on());
        assertEquals(SkillEffects.NOBODY, casts.get(0).by());
    }

    /**
     * A blink is two places at one frame, and BOTH come back.
     *
     * <p>The bug this is here for: the mark of what had been drawn was moved on
     * as each field was read, so the second half of a blink was refused for
     * having the same frame as the first. What the player saw was a flash where
     * he left and a man standing somewhere else with no explanation — which
     * reads as the effect being broken rather than as an off-by-one in a filter.
     */
    @Test
    void bothHalvesOfABlinkComeBack() {
        var casts = SkillEffects.castsIn(
                "name=Lira|cast=MageBlink,900,100.0,100.0,0.0"
                        + "|cast=MageBlink,900,160.0,100.0,0.0", 0);

        assertEquals(2, casts.size(), "half a blink is a teleport with a bug");
        assertEquals(100f, casts.get(0).at().x(), 0.001f);
        assertEquals(160f, casts.get(1).at().x(), 0.001f);
    }

    /** The same line arriving again draws nothing, since nothing new happened. */
    @Test
    void theSameLineAgainIsNotASecondCast() {
        var line = "name=Erika|cast=FrostNova,412,150.0,150.0,40.0";

        assertEquals(1, SkillEffects.castsIn(line, 0).size());
        assertEquals(0, SkillEffects.castsIn(line, 412).size(),
                "a nova would open its ring thirty times a second");
        assertEquals(1, SkillEffects.castsIn(line, 411).size(), "and one frame earlier it is new");
    }

    /** Another game's line, and a broken one, are both simply no ring. */
    @Test
    void aLineThatIsNotOursIsNoRing() {
        assertEquals(0, SkillEffects.castsIn(null, 0).size());
        assertEquals(0, SkillEffects.castsIn("", 0).size());
        assertEquals(0, SkillEffects.castsIn("Wave 4    2 bases left", 0).size());
        assertEquals(0, SkillEffects.castsIn("cast=FrostNova,soon,150.0,150.0,40.0", 0).size(),
                "a frame that is not a number");
        assertEquals(0, SkillEffects.castsIn("cast=FrostNova,412,150.0", 0).size(),
                "and a cast with half its fields");
    }

    // ---- the knock ----

    /**
     * Two things landing together is one thump.
     *
     * <p>Adding them is how a meteor cast beside a nova becomes something a
     * player cannot look at, and it is the sort of thing that is only ever found
     * by someone playing rather than by anyone reading.
     */
    @Test
    void aSecondKnockTakesTheLouderRatherThanTheSum() {
        var scene = scene();
        scene.effects().knock(0.2f, 4f);
        float loud = scene.effects().shakeStrength();

        scene.effects().knock(0.2f, 1f);

        assertEquals(loud, scene.effects().shakeStrength(), 0.0001f,
                "a quiet knock landing on top of a loud one changed it");
        assertEquals(4f, loud, 0.0001f, "and the loud one is the one that was asked for");
    }

    /** A file that turns the shake down turns down every knock, and at 0 turns them off. */
    @Test
    void theFilesShakeScaleIsOnEveryKnock() {
        var halved = scene(visuals -> visuals.shakeScale(0.5f));
        halved.effects().knock(0.2f, 4f);
        assertEquals(2f, halved.effects().shakeStrength(), 0.0001f);

        var still = scene(visuals -> visuals.shakeScale(0f));
        still.effects().knock(0.3f, 5f);
        assertEquals(0f, still.effects().shakeStrength(), 0.0001f, "a camera told not to move moved");
    }

    /** A louder one, though, takes over — it is the newest and biggest thing. */
    @Test
    void aLouderKnockTakesOver() {
        var scene = scene();
        scene.effects().knock(0.2f, 1f);

        scene.effects().knock(0.2f, 5f);

        assertEquals(5f, scene.effects().shakeStrength(), 0.0001f);
    }

    /** The direction it knocks in is noise, which is the one part that must be. */
    @Test
    void theDirectionIsNoise() {
        var scene = scene();
        scene.effects().knock(1f, 4f);

        var first = scene.effects().shakeNow();
        boolean everDiffered = false;
        for (int sample = 0; sample < 20 && !everDiffered; sample++) {
            everDiffered = !scene.effects().shakeNow().equals(first);
        }

        assertTrue(everDiffered, "it knocks the same way every frame, which is a lean, not a shake");
    }

    /** And it dies away rather than stopping. */
    @Test
    void theKnockDiesAway() {
        var scene = scene();
        scene.effects().knock(0.3f, 5f);
        float first = scene.effects().shakeStrength();

        run(scene, 0.2f);
        float later = scene.effects().shakeStrength();

        assertTrue(later < first * 0.5f,
                "two thirds through it is still at " + later + " of " + first
                        + " — it is dying away as a square, so most of it should be gone");
        run(scene, 0.2f);
        assertEquals(0f, scene.effects().shakeStrength(), 0.0001f, "and it never stopped");
    }

    /** A skill nobody wrote a block for draws nothing, rather than throwing. */
    @Test
    void anUnknownRecipeIsNoRing() {
        var scene = scene();

        scene.effects().cast("NobodyWroteThis", SOMEWHERE, EYE, 0f, (x, y) -> 0f);

        assertEquals(0, scene.effects().openCount());
        assertEquals(0, scene.effects().madeSoFar(), "and it did not even build one");
    }

    /**
     * The cast's own width wins over the block's.
     *
     * <p>Which is what lets one block serve two heroes: the ring is drawn as wide
     * as the skill actually reached, so a nova at 40 and a whirlwind at 34 are
     * honest about themselves without either needing a block of its own.
     */
    @Test
    void aCastMaySayHowWideItReached() {
        var scene = scene();

        scene.effects().cast("Boom", SOMEWHERE, EYE, 80f, (x, y) -> 0f);
        scene.effects().update(0.4f, (x, y) -> 0f);

        // It is finished, so it stopped at its widest -- which is what it was told.
        assertEquals(0, scene.effects().openCount());
    }

    /** Far enough off and it is refused before it is started. */
    @Test
    void aRingTwoRoomsAwayIsNotWorthDrawing() {
        var visuals = Visuals.create();
        visuals.effect("Boom", recipe -> recipe.kind(Visuals.EffectVisual.SHOCKWAVE)
                .wave(2f, 30f, 0.4f, 2.4f, 1f, 0.2f));
        var effects = new SkillEffects(new DesktopAssetManager(true), new Node("effects"),
                visuals, null, RangeLook.DEFAULT, CAP, 100f);

        effects.cast("Boom", new Coord3D(5000f, 5000f, 0f), EYE, 0f, (x, y) -> 0f);

        assertEquals(0, effects.openCount(), "a ring two rooms away is a pixel");
    }

    // ---- a mark on a man rather than on the floor ----

    /** Any number at all; what matters is that a mark carries one and a wave does not. */
    private static final int HIM = 7;

    private static Scene guarded() {
        return scene(visuals -> visuals.effect("Guard", recipe -> recipe
                .kind(Visuals.EffectVisual.GROUND_MARK)
                .colours(java.awt.Color.WHITE, java.awt.Color.BLACK)
                .mark(9f, 4f)
                // Edge and wash are on the wave line even for a block that has no
                // wave, exactly as they are in the file. Without them the disc is
                // drawn at no strength at all, which is to say not drawn.
                .wave(0f, 0f, 0f, 1f, 1f, 0.2f)));
    }

    private static java.util.function.IntFunction<Coord3D> standingAt(float[] him) {
        return id -> id == HIM ? new Coord3D(him[0], him[1], 0f) : null;
    }

    /** Where the one ring on the scene is actually being drawn. */
    private static Vector3f ringAt(Scene scene) {
        assertEquals(1, scene.effects().openCount(), "not exactly one ring to look at");
        return scene.root().getChild(0).getLocalTranslation();
    }

    /**
     * ★ A disc laid on a man keeps itself under him.
     *
     * <p>The fault this is here for, and it was reported from a chair: the knight
     * puts up his guard, walks three paces, and the disc is still on the flagstone
     * he cast it from. What the player is then shown is a patch of floor being
     * protected while the man standing outside it takes half damage — and every
     * part of it was right, the ring simply had no way of being told he had moved.
     */
    @Test
    void aMarkLaidOnAManKeepsUpWithHim() {
        var scene = guarded();
        var he = new float[] {100f, 100f};
        scene.effects().cast("Guard", new Coord3D(he[0], he[1], 0f), EYE, 0f, HIM, (x, y) -> 0f);
        scene.effects().update(1f / 60f, (x, y) -> 0f, standingAt(he));
        assertEquals(100f, ringAt(scene).x, 0.01f, "the premise: it started under him");

        he[0] = 160f;
        scene.effects().update(1f / 60f, (x, y) -> 0f, standingAt(he));

        assertEquals(160f, ringAt(scene).x, 0.01f,
                "he walked off and his guard stayed on the stone he cast it from");
    }

    /**
     * A wave does not, even when whose it is happens to be known.
     *
     * <p>The other half of the distinction, and the reason this is not simply
     * "rings follow their caster": a wave is a third of a second of something
     * having HAPPENED somewhere, and one dragged along behind a charging knight
     * is a hoop he is running inside.
     */
    @Test
    void aWaveStaysWhereItWentOff() {
        var scene = scene();

        scene.effects().cast("Boom", SOMEWHERE, EYE, 0f, HIM, (x, y) -> 0f);
        scene.effects().update(1f / 60f, (x, y) -> 0f, id -> new Coord3D(900f, 900f, 0f));

        assertEquals(SOMEWHERE.x(), ringAt(scene).x, 0.01f,
                "it followed him instead of staying where it went off");
    }

    /**
     * And a mark goes when the man it is on does, rather than outliving him.
     *
     * <p>A guard belongs to him, so a guard still glowing over his corpse is the
     * game saying something that is not true. The second half of this is the part
     * that would rot in silence: a mark cut short is still a ring that was
     * borrowed, and a path that forgets to hand it back is a leak nothing
     * announces.
     */
    @Test
    void aMarkGoesWhenTheManItIsOnDoes() {
        var scene = guarded();
        var he = new float[] {100f, 100f};
        scene.effects().cast("Guard", SOMEWHERE, EYE, 0f, HIM, (x, y) -> 0f);
        scene.effects().update(1f / 60f, (x, y) -> 0f, standingAt(he));
        assertEquals(1, scene.effects().openCount(), "the premise: it was open");

        scene.effects().update(1f / 60f, (x, y) -> 0f, id -> null);

        assertEquals(0, scene.effects().openCount(), "he is gone and his guard is still lit");
        scene.effects().cast("Guard", SOMEWHERE, EYE, 0f, HIM, (x, y) -> 0f);
        assertEquals(1, scene.effects().madeSoFar(), "the ring cut short was never given back");
    }

    /** The line says whose a cast is. */
    @Test
    void aCastSaysWhoseItIs() {
        var casts = SkillEffects.castsIn("cast=KnightGuard,412,150.0,150.0,0.0,7", 0);

        assertEquals(1, casts.size());
        assertEquals(7, casts.get(0).on());
    }

    /** And one that names nobody is the floor's, rather than being refused. */
    @Test
    void aCastThatNamesNobodyBelongsToTheFloor() {
        var casts = SkillEffects.castsIn("cast=FrostNova,412,150.0,150.0,40.0", 0);

        assertEquals(1, casts.size(), "a line with no owner on the end was dropped entirely");
        assertEquals(SkillEffects.NOBODY, casts.get(0).on());
    }
}
