package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The effects are lent, capped, placed and put away — and play the same whether
 * or not a window is open.
 *
 * <p>Headless, with a real camera and a real asset manager: the material is
 * loaded and dressed exactly as it is in the game, only never drawn. What cannot
 * be checked here is whether an explosion is beautiful. What can be is
 * everything a beautiful explosion must not do — grow the scene, take more lights
 * than there are, keep burning after its moment, or appear where nobody can see
 * it.
 */
class LayeredEffectsTest {

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);

    /** A world with a floor at zero, a few creatures in it, and eyes everywhere. */
    private static final class Place implements LayeredEffects.Surroundings {
        final Map<Integer, Vector3f> creatures = new HashMap<>();
        final List<Integer> enemies = new ArrayList<>();
        boolean seen = true;
        /** Out of sight within this far of the middle of the world, as behind a pillar. */
        float blind = -1f;

        @Override
        public float floorAt(float x, float z) {
            return 0f;
        }

        @Override
        public Vector3f whereIs(int unitId) {
            var at = creatures.get(unitId);
            return at == null ? null : at.clone();
        }

        @Override
        public int[] enemiesNear(float x, float z, float radius, int caster) {
            return enemies.stream().mapToInt(Integer::intValue).toArray();
        }

        @Override
        public boolean canSee(float x, float z) {
            if (blind > 0f && x * x + z * z <= blind * blind) {
                return false;
            }
            return seen;
        }
    }

    private record Rig(LayeredEffects effects, Node root, Visuals visuals, LightPool lights,
            Place place, Camera camera) {
    }

    private static Rig rig(int particles, int lights) {
        var root = new Node("root");
        var visuals = Visuals.create();
        var pool = new LightPool(root, lights);
        var place = new Place();
        var camera = new Camera(1600, 900);
        camera.setFrustumPerspective(45f, 1600f / 900f, 1f, 2000f);
        camera.setLocation(new Vector3f(0f, 120f, 120f));
        camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        camera.update();
        var effects = new LayeredEffects(ASSETS, root, visuals, pool, place, particles, 600f,
                new Random(7));
        return new Rig(effects, root, visuals, pool, place, camera);
    }

    private static EffectLayer.Builder burst(int count) {
        return EffectLayer.builder().type(EffectLayer.BURST).count(count)
                .lifeMin(0.3f).lifeMax(0.5f).speedMin(4f).speedMax(8f);
    }

    private static LayeredEffects.Moment at(float x, float z) {
        return new LayeredEffects.Moment(new Vector3f(x, 0f, z), null, null, 0f, 1f, 0f,
                LayeredEffects.NOBODY, LayeredEffects.NOBODY);
    }

    private static void run(Rig rig, float seconds) {
        for (float gone = 0f; gone < seconds; gone += 1f / 30f) {
            rig.effects().update(1f / 30f, rig.camera());
        }
    }

    private static int particleGeometries(Node root) {
        int found = 0;
        for (var child : root.getChildren()) {
            if (child instanceof Geometry geometry && "particles".equals(geometry.getName())) {
                found++;
            }
        }
        return found;
    }

    // ---- lent and given back ----

    @Test
    void aBurstIsLentAndGivenBack() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Sparks", recipe -> recipe.layer(burst(32).build()));

        rig.effects().cast("Sparks", at(0f, 0f), rig.camera());
        assertEquals(1, rig.effects().playingCount());
        assertEquals(32, rig.effects().particlesInUse());
        assertEquals(1, particleGeometries(rig.root()));

        run(rig, 1f);
        assertEquals(0, rig.effects().playingCount(), "a burst is over when its sparks are");
        assertEquals(0, rig.effects().particlesInUse());
        assertEquals(0, particleGeometries(rig.root()), "and nothing of it is left in the scene");
    }

    /**
     * A fight's worth of casts builds only what the busiest moment needed.
     *
     * <p>The number that says the scene is not growing. Three layers go off at
     * once, fifty times over; three layers are ever built.
     */
    @Test
    void aFightsWorthOfCastsBuildsOnlyWhatTheBusiestMomentNeeded() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Blast", recipe -> recipe
                .layer(burst(16).build())
                .layer(burst(16).texture("effects/particles/smoke_01.png").additive(false).build())
                .layer(EffectLayer.builder().type(EffectLayer.RING).count(1).lifeMax(0.4f)
                        .texture("effects/particles/circle_02.png").build()));

        for (int fight = 0; fight < 50; fight++) {
            rig.effects().cast("Blast", at(fight % 5, 0f), rig.camera());
            run(rig, 0.6f);
        }

        assertEquals(3, rig.effects().layersMade(), "a finished layer should be lent again");
        assertEquals(0, rig.effects().particlesInUse());
    }

    // ---- ceilings ----

    @Test
    void theLightCeilingHolds() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Flash", recipe -> recipe.layer(EffectLayer.builder()
                .type(EffectLayer.LIGHT).seconds(0.5f).lightPower(3f).lightRadius(40f)
                .lightColour(0xFF8A28).build()));

        for (int shot = 0; shot < 6; shot++) {
            rig.effects().cast("Flash", at(shot, 0f), rig.camera());
            assertTrue(rig.lights().lit() <= 2, "more lights burning than there are");
        }
        run(rig, 1f);
        assertEquals(0, rig.lights().lit(), "and every one is put out when it is done");
    }

    /**
     * Past the particle ceiling a new layer is drawn thinner, and past that not at
     * all — never more than the ceiling.
     */
    @Test
    void theParticleCeilingThinsANewLayerRatherThanBreakingIt() {
        var rig = rig(40, 2);
        rig.visuals().effect("Sparks", recipe -> recipe.layer(burst(32).build()));

        rig.effects().cast("Sparks", at(0f, 0f), rig.camera());
        rig.effects().cast("Sparks", at(1f, 0f), rig.camera());
        rig.effects().cast("Sparks", at(2f, 0f), rig.camera());

        assertTrue(rig.effects().particlesInUse() <= 40,
                "burning " + rig.effects().particlesInUse() + " against a ceiling of 40");
        assertEquals(2, rig.effects().playingCount(), "the second is thinner; the third is not drawn");
    }

    // ---- what the file says is what is drawn ----

    /**
     * Change the block and the drawing changes with it.
     *
     * <p>The whole promise of putting effects in a file: nothing in the code knows
     * what a fireball looks like, so a colour, a count or a texture changed there is
     * a colour, a count or a texture changed here.
     */
    @Test
    void whatTheFileSaysIsWhatIsDressed() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Embers", recipe -> recipe.layer(burst(20)
                .texture("effects/particles/spark_03.png")
                .colourStart(0xFF7A2A).alphaStart(0.8f).sizeStart(3f).sizeEnd(0.5f)
                .additive(false).build()));

        rig.effects().cast("Embers", at(0f, 0f), rig.camera());

        var drawn = (Geometry) rig.root().getChild("particles");
        assertNotNull(drawn);
        var material = drawn.getMaterial();
        var start = (ColorRGBA) material.getParam("StartColour").getValue();
        assertEquals(0xFF / 255f, start.r, 0.01f);
        assertEquals(0x7A / 255f, start.g, 0.01f);
        assertEquals(0.8f, start.a, 0.01f);
        assertEquals(3f, (Float) material.getParam("SizeStart").getValue(), 0.001f);
        assertEquals(0.5f, (Float) material.getParam("SizeEnd").getValue(), 0.001f);
        assertEquals(1f, (Float) material.getParam("Cover").getValue(), 0.001f,
                "smoke covers, it does not glow");
        assertNotNull(material.getTextureParam("Texture"));
        assertEquals(32, rig.effects().particlesInUse(), "twenty, lent from the pool of 32");
    }

    /**
     * A shape measured in reach is as wide as the skill reached, wherever that
     * number came from: the cast, or -- for a landing, which carries none -- the
     * skill the game said threw it.
     */
    @Test
    void aShapeMeasuredInReachIsAsWideAsTheSkill() {
        var rig = rig(1000, 2);
        rig.visuals().effectReach("Blast", 26f);
        rig.visuals().effect("Blast", recipe -> recipe.layer(EffectLayer.builder()
                .type(EffectLayer.RING).count(1).lifeMax(0.4f).turnJitter(0f)
                .measure(EffectLayer.REACH).sizeStart(2f).sizeEnd(2f).build()));

        rig.effects().cast("Blast", new LayeredEffects.Moment(new Vector3f(), null, null, 0f,
                1f, 40f, LayeredEffects.NOBODY, LayeredEffects.NOBODY), rig.camera());
        assertEquals(40f, scaleOfTheFirst(rig), 0.001f, "the cast said it reached forty");
        rig.effects().clear();

        rig.effects().landed(5, "Blast", new Vector3f(), rig.camera());
        assertEquals(26f, scaleOfTheFirst(rig), 0.001f,
                "a landing says nothing, so it is the skill's");
    }

    /** How big the first particle of the only layer drawn was made, against its layer. */
    private static float scaleOfTheFirst(Rig rig) {
        var drawn = (Geometry) rig.root().getChild("particles");
        var lives = (java.nio.FloatBuffer) drawn.getMesh()
                .getBuffer(com.jme3.scene.VertexBuffer.Type.TexCoord2).getData();
        return lives.get(3);
    }

    /** Light covers nothing unless it says so, and fire may say how much. */
    @Test
    void lightCoversNothingUnlessItSaysHowMuch() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Spark", recipe -> recipe.layer(burst(4).build()));
        rig.visuals().effect("Flame", recipe -> recipe.layer(burst(4).cover(0.4f).build()));

        rig.effects().cast("Spark", at(0f, 0f), rig.camera());
        var spark = ((Geometry) rig.root().getChild("particles")).getMaterial();
        assertEquals(0f, (Float) spark.getParam("Cover").getValue(), 0.001f);
        rig.effects().clear();

        rig.effects().cast("Flame", at(0f, 0f), rig.camera());
        var flame = ((Geometry) rig.root().getChild("particles")).getMaterial();
        assertEquals(0.4f, (Float) flame.getParam("Cover").getValue(), 0.001f);
    }

    /** A texture that is not there is a plain glow and a line in the log, never a crash. */
    @Test
    void aTextureThatIsNotThereIsAGlow() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Typo", recipe -> recipe.layer(burst(8)
                .texture("effects/particles/no_such_thing.png").build()));

        rig.effects().cast("Typo", at(0f, 0f), rig.camera());

        assertEquals(1, rig.effects().playingCount());
    }

    // ---- where things happen ----

    @Test
    void anAuraFollowsItsManAndLastsAsLongAsTheSkill() {
        var rig = rig(1000, 2);
        rig.place().creatures.put(7, new Vector3f(10f, 0f, 10f));
        rig.visuals().effectSeconds("Shield", 2f);
        rig.visuals().effect("Shield", recipe -> recipe.layer(EffectLayer.builder()
                .type(EffectLayer.AURA).count(6).lifeMin(0.5f).lifeMax(0.5f).build()));

        rig.effects().cast("Shield", new LayeredEffects.Moment(new Vector3f(10f, 0f, 10f), null,
                null, 0f, 1f, 0f, 7, 7), rig.camera());
        run(rig, 0.5f);
        rig.place().creatures.put(7, new Vector3f(30f, 0f, 10f));
        run(rig, 0.1f);

        var drawn = (Geometry) rig.root().getChild("particles");
        assertEquals(30f, drawn.getLocalTranslation().x, 0.01f, "it should have gone with him");
        run(rig, 1.2f);
        assertEquals(1, rig.effects().playingCount(), "still inside the skill's two seconds");
        run(rig, 0.4f);
        assertEquals(0, rig.effects().playingCount(), "and gone once they are up");
    }

    @Test
    void aSecondCastOfARunningAuraDoesNotStackASecond() {
        var rig = rig(1000, 2);
        rig.place().creatures.put(7, new Vector3f());
        rig.visuals().effectSeconds("Whirl", 4f);
        rig.visuals().effect("Whirl", recipe -> recipe.layer(EffectLayer.builder()
                .type(EffectLayer.AURA).count(2).build()));
        var moment = new LayeredEffects.Moment(new Vector3f(), null, null, 0f, 1f, 0f, 7, 7);

        for (int blow = 0; blow < 6; blow++) {
            rig.effects().cast("Whirl", moment, rig.camera());
            run(rig, 0.5f);
        }

        assertEquals(1, rig.effects().playingCount(), "one whirlwind, however many blows it lands");
    }

    @Test
    void anAuraGoesWhenTheManItIsOnDoes() {
        var rig = rig(1000, 2);
        rig.place().creatures.put(7, new Vector3f());
        rig.visuals().effectSeconds("Shield", 10f);
        rig.visuals().effect("Shield", recipe -> recipe.layer(EffectLayer.builder()
                .type(EffectLayer.AURA).count(4).lifeMax(0.4f).build()));

        rig.effects().cast("Shield", new LayeredEffects.Moment(new Vector3f(), null, null, 0f,
                1f, 0f, 7, 7), rig.camera());
        run(rig, 0.2f);
        rig.place().creatures.remove(7);
        run(rig, 0.5f);

        assertEquals(0, rig.effects().playingCount(), "a shield does not stand where he died");
    }

    @Test
    void whatIsCaughtIsDrawnOnEveryoneCaught() {
        var rig = rig(1000, 2);
        rig.place().creatures.put(3, new Vector3f(5f, 0f, 0f));
        rig.place().creatures.put(4, new Vector3f(-5f, 0f, 0f));
        rig.place().enemies.addAll(List.of(3, 4));
        rig.visuals().effectSeconds("Frost", 1f);
        rig.visuals().effect("Frost", recipe -> recipe.layer(EffectLayer.builder()
                .type(EffectLayer.AURA).at(EffectLayer.CAUGHT).count(4).build()));

        rig.effects().cast("Frost", new LayeredEffects.Moment(new Vector3f(), null, null, 0f,
                1f, 40f, LayeredEffects.NOBODY, 1), rig.camera());

        assertEquals(2, rig.effects().playingCount(), "one each for the two it caught");
    }

    @Test
    void bothEndsOfARunAreDrawn() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Blink", recipe -> recipe.layer(burst(8).at(EffectLayer.BOTH).build()));

        rig.effects().cast("Blink", new LayeredEffects.Moment(new Vector3f(20f, 0f, 0f),
                new Vector3f(), new Vector3f(20f, 0f, 0f), 1f, 0f, 0f, 7, 7), rig.camera());

        assertEquals(2, rig.effects().playingCount(), "the place he left and the place he arrived");
    }

    // ---- flight ----

    /**
     * A trail keeps burning after what laid it has landed.
     *
     * <p>The sparks already in the air were let go of; they do not vanish with the
     * arrow. What stops is the feeding — and then, a spark's life later, the layer.
     */
    @Test
    void aTrailOutlivesTheThingThatLaidIt() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Arrow", recipe -> recipe
                .layer(EffectLayer.builder().type(EffectLayer.TRAIL).count(16).rate(40f)
                        .lifeMin(0.4f).lifeMax(0.4f).build())
                .layer(burst(8).build()));
        var arrow = new Node("arrow");
        rig.root().attachChild(arrow);

        rig.effects().flying(11, "Arrow", arrow, Vector3f.ZERO, rig.camera());
        assertEquals(1, rig.effects().playingCount(), "only the trail flies; the burst waits");
        for (int frame = 0; frame < 10; frame++) {
            arrow.move(2f, 0f, 0f);
            rig.effects().update(1f / 30f, rig.camera());
        }

        rig.effects().landed(11, "Arrow", arrow.getWorldTranslation(), rig.camera());
        assertEquals(2, rig.effects().playingCount(), "the trail burning out, and the burst");
        run(rig, 0.25f);
        assertTrue(rig.effects().playingCount() >= 1, "the sparks in the air are still in the air");
        run(rig, 0.5f);
        assertEquals(0, rig.effects().playingCount());
    }

    /**
     * A trail is tested against the camera round everywhere it has been, not only
     * round where the arrow is now -- or its own smoke is culled behind it while
     * the player is looking straight at it.
     */
    @Test
    void aTrailsBoundCoversTheWayItWasLaid() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Arrow", recipe -> recipe.layer(EffectLayer.builder()
                .type(EffectLayer.TRAIL).count(32).rate(60f).lifeMin(0.5f).lifeMax(0.5f)
                .build()));
        var arrow = new Node("arrow");
        rig.root().attachChild(arrow);

        rig.effects().flying(12, "Arrow", arrow, Vector3f.ZERO, rig.camera());
        for (int frame = 0; frame < 15; frame++) {
            arrow.move(6f, 0f, 0f);
            rig.effects().update(1f / 30f, rig.camera());
        }

        var bound = ((Geometry) rig.root().getChild("particles")).getMesh().getBound();
        assertTrue(bound.contains(new Vector3f(6f, 0f, 0f)), "where it began to be laid");
        assertTrue(bound.contains(new Vector3f(90f, 0f, 0f)), "and where the arrow is now");
    }

    // ---- what is not started at all ----

    @Test
    void underTheFogNothingStarts() {
        var rig = rig(1000, 2);
        rig.place().seen = false;
        rig.visuals().effect("Sparks", recipe -> recipe.layer(burst(8).build()));

        rig.effects().cast("Sparks", at(0f, 0f), rig.camera());

        assertEquals(0, rig.effects().playingCount());
    }

    /**
     * A blast behind a pillar throws its fire past it: drawn if any of it is in
     * sight, and not if none of it is.
     */
    @Test
    void aBlastWhoseMiddleIsHiddenButWhoseReachIsNotIsDrawn() {
        var rig = rig(1000, 2);
        rig.place().blind = 6f;
        rig.visuals().effect("Wide", recipe -> recipe.layer(burst(8).speedMin(40f)
                .speedMax(60f).build()));
        rig.visuals().effect("Small", recipe -> recipe.layer(EffectLayer.builder()
                .type(EffectLayer.IMPACT).count(1).lifeMin(0.2f).lifeMax(0.2f).build()));

        rig.effects().cast("Wide", at(0f, 0f), rig.camera());
        assertEquals(1, rig.effects().playingCount(), "its sparks fly out past the pillar");

        rig.effects().cast("Small", at(0f, 0f), rig.camera());
        assertEquals(1, rig.effects().playingCount(), "a flash that stays behind it is not drawn");
    }

    @Test
    void aBurstOffTheScreenIsNotStarted() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Sparks", recipe -> recipe.layer(burst(8).build()));

        rig.effects().cast("Sparks", at(0f, 400f), rig.camera());

        assertEquals(0, rig.effects().playingCount(), "behind the camera is not worth a spark");
    }

    @Test
    void clearingTakesEverythingBack() {
        var rig = rig(1000, 3);
        rig.place().creatures.put(7, new Vector3f());
        rig.visuals().effectSeconds("All", 5f);
        rig.visuals().effect("All", recipe -> recipe
                .layer(burst(16).build())
                .layer(EffectLayer.builder().type(EffectLayer.AURA).count(4).build())
                .layer(EffectLayer.builder().type(EffectLayer.LIGHT).seconds(2f)
                        .lightPower(2f).lightRadius(30f).build()));

        rig.effects().cast("All", new LayeredEffects.Moment(new Vector3f(), null, null, 0f, 1f,
                0f, 7, 7), rig.camera());
        rig.effects().clear();

        assertEquals(0, rig.effects().playingCount());
        assertEquals(0, rig.effects().particlesInUse());
        assertEquals(0, rig.lights().lit());
        assertEquals(0, particleGeometries(rig.root()));
    }

    // ---- columns of light ----

    private static EffectLayer.Builder pillar(String way) {
        return EffectLayer.builder().type(EffectLayer.PILLAR).count(1).direction(way)
                .height(60f).sizeStart(10f).sizeEnd(10f).lifeMin(0.6f).lifeMax(0.6f)
                .rise(0.25f).riseEase(3f);
    }

    private static Geometry theOnlyLayer(Node root) {
        for (var child : root.getChildren()) {
            if (child instanceof Geometry geometry && "particles".equals(geometry.getName())) {
                return geometry;
            }
        }
        throw new AssertionError("nothing is drawn");
    }

    /** What one slot's first corner was written with, out of one of the mesh's buffers. */
    private static float written(Geometry drawn, com.jme3.scene.VertexBuffer.Type buffer,
            int slot, int component, int components) {
        var data = (java.nio.FloatBuffer) drawn.getMesh().getBuffer(buffer).getData();
        return data.get(slot * 4 * components + component);
    }

    @Test
    void aPillarStandsAsTallAsItSaysAndGoesTheWayItSays() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Rising", recipe -> recipe.layer(pillar(EffectLayer.UP).build()));
        rig.visuals().effect("Falling", recipe -> recipe.layer(pillar(EffectLayer.DOWN).build()));

        rig.effects().cast("Rising", at(5f, 0f), rig.camera());
        var rising = theOnlyLayer(rig.root());
        assertEquals(1f, written(rising, com.jme3.scene.VertexBuffer.Type.TexCoord3, 0, 1, 4),
                "UP is written as up");
        assertEquals(60f, written(rising, com.jme3.scene.VertexBuffer.Type.TexCoord3, 0, 3, 4),
                0.001f, "and as tall as the layer says");
        assertEquals(5f, written(rising, com.jme3.scene.VertexBuffer.Type.Position, 0, 0, 3),
                0.001f, "standing where it happened");
        rig.effects().clear();

        rig.effects().cast("Falling", at(5f, 0f), rig.camera());
        assertEquals(-1f, written(theOnlyLayer(rig.root()),
                com.jme3.scene.VertexBuffer.Type.TexCoord3, 0, 1, 4), "DOWN is written as down");
    }

    @Test
    void aPillarIsDressedToStandUpright() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Column", recipe -> recipe.layer(pillar(EffectLayer.DOWN).build()));

        rig.effects().cast("Column", at(0f, 0f), rig.camera());
        var material = theOnlyLayer(rig.root()).getMaterial();

        assertEquals(Boolean.TRUE, material.getParamValue("Pillar"));
        org.junit.jupiter.api.Assertions.assertNull(material.getParam("Axis"), "it is not a beam");
        org.junit.jupiter.api.Assertions.assertNull(material.getParam("Streak"),
                "nor drawn out along a speed it does not have");
        assertEquals(0.25f, (Float) material.getParamValue("Rise"), 0.001f);
        assertEquals(3f, (Float) material.getParamValue("RiseEase"), 0.001f);
    }

    /**
     * A pillar draws the columns it asks for and no more. The pool lends four slots
     * for one, and a column is light: four drawn over one another would be four
     * times as bright as the file said.
     */
    @Test
    void aPillarDrawsTheColumnsItAsksForAndNoMore() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Column", recipe -> recipe.layer(pillar(EffectLayer.UP).build()));

        rig.effects().cast("Column", at(0f, 0f), rig.camera());
        var drawn = theOnlyLayer(rig.root());

        assertEquals(0f, written(drawn, com.jme3.scene.VertexBuffer.Type.TexCoord2, 0, 0, 4),
                "the one it asked for is born at once");
        for (int slot = 1; slot < 4; slot++) {
            assertTrue(written(drawn, com.jme3.scene.VertexBuffer.Type.TexCoord2, slot, 0, 4) > 1000f,
                    "slot " + slot + " stays dark");
        }
    }

    @Test
    void aPillarAndItsLightArePutAwayWhenTheyAreOver() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Column", recipe -> recipe
                .layer(pillar(EffectLayer.UP).build())
                .layer(EffectLayer.builder().type(EffectLayer.LIGHT).seconds(0.6f)
                        .lightPower(3f).lightRadius(60f).build()));

        rig.effects().cast("Column", at(0f, 0f), rig.camera());
        assertEquals(1, particleGeometries(rig.root()));
        assertEquals(1, rig.lights().lit());

        run(rig, 1f);
        assertEquals(0, rig.effects().playingCount());
        assertEquals(0, rig.effects().particlesInUse());
        assertEquals(0, particleGeometries(rig.root()), "nothing of it is left in the scene");
        assertEquals(0, rig.lights().lit(), "and its light is out");
    }

    /**
     * A layer that says it follows goes where the man it happened to goes -- a hero
     * who levels mid-stride does not walk out of his own light -- and one that does
     * not say so stays where it was started.
     */
    @Test
    void aLayerThatFollowsGoesWhereHeGoesAndOneThatDoesNotStays() {
        var rig = rig(1000, 2);
        var onHim = new LayeredEffects.Moment(new Vector3f(10f, 0f, 0f), null, null, 0f, 1f, 0f,
                9, 9);
        rig.visuals().effect("Worn", recipe -> recipe.layer(pillar(EffectLayer.UP)
                .at(EffectLayer.CASTER).follows(true).lifeMin(2f).lifeMax(2f).build()));
        rig.visuals().effect("Left", recipe -> recipe.layer(pillar(EffectLayer.UP)
                .at(EffectLayer.CASTER).lifeMin(2f).lifeMax(2f).build()));

        rig.place().creatures.put(9, new Vector3f(10f, 0f, 0f));
        rig.effects().cast("Worn", onHim, rig.camera());
        rig.place().creatures.put(9, new Vector3f(40f, 0f, 25f));
        run(rig, 0.2f);
        assertEquals(new Vector3f(40f, 0f, 25f), theOnlyLayer(rig.root()).getLocalTranslation(),
                "it went with him");
        rig.effects().clear();

        rig.place().creatures.put(9, new Vector3f(10f, 0f, 0f));
        rig.effects().cast("Left", onHim, rig.camera());
        rig.place().creatures.put(9, new Vector3f(40f, 0f, 25f));
        run(rig, 0.2f);
        var left = theOnlyLayer(rig.root());
        assertEquals(Vector3f.ZERO, left.getLocalTranslation(), "it is drawn where it was born");
        assertEquals(10f, written(left, com.jme3.scene.VertexBuffer.Type.Position, 0, 0, 3), 0.001f,
                "which is where he was");
    }

    /** A moment drawn bigger is the same recipe with every size and the height times the scale. */
    @Test
    void aBiggerMomentIsTheSameLookAtAnotherSize() {
        var rig = rig(1000, 2);
        rig.visuals().effect("Column", recipe -> recipe.layer(pillar(EffectLayer.UP).build()));

        rig.effects().cast("Column", at(0f, 0f), rig.camera(), 1.5f);
        var drawn = theOnlyLayer(rig.root());

        assertEquals(90f, written(drawn, com.jme3.scene.VertexBuffer.Type.TexCoord3, 0, 3, 4),
                0.001f, "sixty tall, half as tall again");
        assertEquals(1.5f, written(drawn, com.jme3.scene.VertexBuffer.Type.TexCoord2, 0, 3, 4),
                0.001f, "and half as wide again");
    }

    /**
     * Fifty moments at once -- a crowd of heroes levelling in one breath -- stay under
     * both ceilings, and leave nothing behind.
     */
    @Test
    void manyColumnsAtOnceStayUnderTheCeilingsAndLeaveNothing() {
        var rig = rig(300, 4);
        rig.visuals().effect("LevelUp", recipe -> recipe
                .layer(pillar(EffectLayer.UP).build())
                .layer(pillar(EffectLayer.UP).sizeStart(30f).sizeEnd(34f).build())
                .layer(burst(16).build())
                .layer(EffectLayer.builder().type(EffectLayer.LIGHT).seconds(0.6f)
                        .lightPower(3f).lightRadius(60f).build()));

        for (int hero = 0; hero < 50; hero++) {
            rig.effects().cast("LevelUp", at(hero % 10, hero / 10f), rig.camera());
            assertTrue(rig.effects().particlesInUse() <= 300, "over the particle ceiling");
            assertTrue(rig.lights().lit() <= 4, "more lights burning than there are");
        }
        run(rig, 1.5f);

        assertEquals(0, rig.effects().playingCount());
        assertEquals(0, rig.effects().particlesInUse());
        assertEquals(0, particleGeometries(rig.root()));
        assertEquals(0, rig.lights().lit());
    }
}
