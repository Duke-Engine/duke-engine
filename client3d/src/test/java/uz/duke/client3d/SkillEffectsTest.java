package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;

/**
 * The casts a status line asks to have drawn, and the knock a cast gives the camera.
 *
 * <p>Nothing here asserts that anything looks good, which is not a thing a test can say. What it
 * guards is the ways this could go wrong silently: half a blink read, the same cast read thirty
 * times a second, and two knocks adding up to an earthquake.
 */
class SkillEffectsTest {

    private static final Coord3D SOMEWHERE = new Coord3D(100f, 100f, 0f);
    private static final Vector3f EYE = new Vector3f(100f, 40f, 100f);

    private static SkillEffects effects() {
        return effects(visuals -> visuals.effect("Boom", recipe -> recipe.shake(0.3f, 4f)), 0f);
    }

    private static SkillEffects effects(java.util.function.Consumer<Visuals> art, float tooFar) {
        var visuals = Visuals.create();
        art.accept(visuals);
        return new SkillEffects(visuals, tooFar);
    }

    private static void run(SkillEffects effects, float seconds) {
        for (float gone = 0f; gone < seconds; gone += 1f / 60f) {
            effects.update(1f / 60f);
        }
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
        var meteor = SkillEffects.castsIn("name=Lira|cast=MeteorCall,900,150.0,160.0,44.0,0,7", 0).get(0);

        assertEquals(SkillEffects.NOBODY, meteor.on(), "a meteor's mark is the floor's");
        assertEquals(7, meteor.by(), "and the mage is still the one who called it");
    }

    /** A line from before either field existed is read as belonging to nobody. */
    @Test
    void anOlderLineIsStillRead() {
        var casts = SkillEffects.castsIn("name=Erika|cast=FrostNova,412,150.0,150.0,40.0", 0);

        assertEquals(1, casts.size(), "the cast is the part that must not be lost");
        assertEquals(SkillEffects.NOBODY, casts.get(0).on());
        assertEquals(SkillEffects.NOBODY, casts.get(0).by());
    }

    /**
     * A blink is two places at one frame, and BOTH come back.
     *
     * <p>The bug this is here for: the mark of what had been drawn was moved on
     * as each field was read, so the second half of a blink was refused for
     * having the same frame as the first. What the player saw was a flash where
     * he left and a man standing somewhere else with no explanation.
     */
    @Test
    void bothHalvesOfABlinkComeBack() {
        var casts = SkillEffects.castsIn(
                "name=Lira|cast=MageBlink,900,100.0,100.0,0.0|cast=MageBlink,900,160.0,100.0,0.0", 0);

        assertEquals(2, casts.size(), "half a blink is a teleport with a bug");
        assertEquals(100f, casts.get(0).at().x(), 0.001f);
        assertEquals(160f, casts.get(1).at().x(), 0.001f);
    }

    /** The same line arriving again draws nothing, since nothing new happened. */
    @Test
    void theSameLineAgainIsNotASecondCast() {
        var line = "name=Erika|cast=FrostNova,412,150.0,150.0,40.0";

        assertEquals(1, SkillEffects.castsIn(line, 0).size());
        assertEquals(0, SkillEffects.castsIn(line, 412).size(), "a nova would go off thirty times a second");
        assertEquals(1, SkillEffects.castsIn(line, 411).size(), "and one frame earlier it is new");
    }

    /** Another game's line, and a broken one, are both simply no cast. */
    @Test
    void aLineThatIsNotOursIsNoCast() {
        assertEquals(0, SkillEffects.castsIn(null, 0).size());
        assertEquals(0, SkillEffects.castsIn("", 0).size());
        assertEquals(0, SkillEffects.castsIn("Wave 4    2 bases left", 0).size());
        assertEquals(0, SkillEffects.castsIn("cast=FrostNova,soon,150.0,150.0,40.0", 0).size(),
                "a frame that is not a number");
        assertEquals(0, SkillEffects.castsIn("cast=FrostNova,412,150.0", 0).size(),
                "and a cast with half its fields");
    }

    /** The line says whose a cast is. */
    @Test
    void aCastSaysWhoseItIs() {
        var casts = SkillEffects.castsIn("cast=KnightGuard,412,150.0,150.0,0.0,7", 0);

        assertEquals(1, casts.size());
        assertEquals(7, casts.get(0).on());
    }

    // ---- the knock ----

    /** A cast knocks the camera as hard as its recipe says. */
    @Test
    void aCastKnocksAsItsRecipeSays() {
        var effects = effects();

        effects.cast("Boom", SOMEWHERE, EYE);

        assertEquals(4f, effects.shakeStrength(), 0.0001f);
    }

    /** A skill nobody wrote a block for knocks nothing, rather than throwing. */
    @Test
    void anUnknownRecipeKnocksNothing() {
        var effects = effects();

        effects.cast("NobodyWroteThis", SOMEWHERE, EYE);

        assertEquals(0f, effects.shakeStrength(), 0.0001f);
    }

    /** Far enough off and it is not felt at all. */
    @Test
    void aCastTwoRoomsAwayIsNotFelt() {
        var effects = effects(visuals -> visuals.effect("Boom", recipe -> recipe.shake(0.3f, 4f)), 100f);

        effects.cast("Boom", new Coord3D(5000f, 5000f, 0f), EYE);

        assertEquals(0f, effects.shakeStrength(), 0.0001f, "a blast two rooms away shook the camera");
    }

    /**
     * Two things landing together is one thump.
     *
     * <p>Adding them is how a meteor cast beside a nova becomes something a
     * player cannot look at, and it is the sort of thing that is only ever found
     * by someone playing rather than by anyone reading.
     */
    @Test
    void aSecondKnockTakesTheLouderRatherThanTheSum() {
        var effects = effects();
        effects.knock(0.2f, 4f);
        float loud = effects.shakeStrength();

        effects.knock(0.2f, 1f);

        assertEquals(loud, effects.shakeStrength(), 0.0001f, "a quiet knock landing on top of a loud one changed it");
        assertEquals(4f, loud, 0.0001f, "and the loud one is the one that was asked for");
    }

    /** A file that turns the shake down turns down every knock, and at 0 turns them off. */
    @Test
    void theFilesShakeScaleIsOnEveryKnock() {
        var halved = effects(visuals -> visuals.shakeScale(0.5f), 0f);
        halved.knock(0.2f, 4f);
        assertEquals(2f, halved.shakeStrength(), 0.0001f);

        var still = effects(visuals -> visuals.shakeScale(0f), 0f);
        still.knock(0.3f, 5f);
        assertEquals(0f, still.shakeStrength(), 0.0001f, "a camera told not to move moved");
    }

    /** A louder one, though, takes over — it is the newest and biggest thing. */
    @Test
    void aLouderKnockTakesOver() {
        var effects = effects();
        effects.knock(0.2f, 1f);

        effects.knock(0.2f, 5f);

        assertEquals(5f, effects.shakeStrength(), 0.0001f);
    }

    /** The direction it knocks in is noise, which is the one part that must be. */
    @Test
    void theDirectionIsNoise() {
        var effects = effects();
        effects.knock(1f, 4f);

        var first = effects.shakeNow();
        boolean everDiffered = false;
        for (int sample = 0; sample < 20 && !everDiffered; sample++) {
            everDiffered = !effects.shakeNow().equals(first);
        }

        assertTrue(everDiffered, "it knocks the same way every frame, which is a lean, not a shake");
    }

    /** And it dies away rather than stopping. */
    @Test
    void theKnockDiesAway() {
        var effects = effects();
        effects.knock(0.3f, 5f);
        float first = effects.shakeStrength();

        run(effects, 0.2f);
        float later = effects.shakeStrength();

        assertTrue(later < first * 0.5f, "two thirds through it is still at " + later + " of " + first
                + " — it is dying away as a square, so most of it should be gone");
        run(effects, 0.2f);
        assertEquals(0f, effects.shakeStrength(), 0.0001f, "and it never stopped");
    }

    /** A world rebuilt has nothing left shaking in it. */
    @Test
    void clearingStopsTheKnock() {
        var effects = effects();
        effects.knock(1f, 4f);

        effects.clear();

        assertEquals(0f, effects.shakeStrength(), 0.0001f);
    }
}
