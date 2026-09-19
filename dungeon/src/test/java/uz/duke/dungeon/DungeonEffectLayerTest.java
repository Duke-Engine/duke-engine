package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import uz.duke.client3d.EffectLayer;
import uz.duke.client3d.Visuals;
import uz.duke.core.GameConstants;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * The layers in the settings file are layers the client can draw, from textures
 * that are there, lasting as long as the skills they belong to.
 *
 * <p>Every fault this guards against looks the same from a chair: an effect that
 * is not there. A misspelt type is ignored, a texture that is not on the classpath
 * is a plain glow, a layer headed with an effect nobody casts is never played —
 * and none of those raises anything, because an effect that threw would take the
 * frame with it. So the file is checked here instead, where a mistake is a red test
 * rather than a fight that looks slightly flatter than it should.
 */
class DungeonEffectLayerTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** What the file's layers turn into, exactly as Main hands them over. */
    private static EffectLayer drawn(DungeonSettings.EffectLayerArt art) {
        return Main.layerOf(art);
    }

    /**
     * Every skill a hero has is drawn in layers, and so is everything a skill throws.
     *
     * <p>A look left without layers is not an error anywhere else: it falls back to
     * the old recipe kinds, draws something plain, and nobody notices that one skill
     * out of twelve was never given its effect.
     */
    @Test
    void everySkillAndEverythingItThrowsIsDrawnInLayers() {
        var layered = SETTINGS.effectLayers().stream()
                .map(DungeonSettings.EffectLayerArt::effect).collect(Collectors.toSet());
        var carries = new java.util.HashMap<String, String>();
        for (var projectile : SETTINGS.projectiles()) {
            carries.put(projectile.name(), projectile.effect());
        }
        var bare = new ArrayList<String>();
        for (var skill : SETTINGS.skills()) {
            if (skill.hasLook() && !layered.contains(skill.look())) {
                bare.add(skill.look());
            }
            var thrown = skill.hasProjectile() ? carries.get(skill.projectile()) : null;
            if (thrown != null && !thrown.isBlank() && !layered.contains(thrown)) {
                bare.add(thrown + " (thrown)");
            }
        }
        assertTrue(bare.isEmpty(), "drawn without layers: " + bare);
    }

    /**
     * A layer headed with an effect that does not exist is never played.
     *
     * <p>The heading is the only thing connecting a layer to anything, and a
     * misspelling in it is a block that parses perfectly and draws nothing forever.
     */
    @Test
    void everyLayerBelongsToAnEffectThatExists() {
        var effects = SETTINGS.effects().stream()
                .map(uz.duke.dungeon.content.Effect::name).collect(Collectors.toSet());
        for (var layer : SETTINGS.effectLayers()) {
            assertTrue(effects.contains(layer.effect()), "Layer " + layer.effect()
                    + " " + layer.name() + " belongs to no Effect");
        }
    }

    /** Two layers of one effect with one name are one layer too many to talk about. */
    @Test
    void noEffectNamesTwoLayersTheSame() {
        var seen = new java.util.HashSet<String>();
        for (var layer : SETTINGS.effectLayers()) {
            assertTrue(seen.add(layer.effect() + " " + layer.name()),
                    "DungeonEffectLayer " + layer.effect() + " " + layer.name() + " is there twice");
        }
    }

    @Test
    void everyLayerIsOfAKindTheClientDraws() {
        var wrong = new ArrayList<String>();
        for (var art : SETTINGS.effectLayers()) {
            var layer = drawn(art);
            var where = art.effect() + " " + art.name();
            if (!EffectLayer.TYPES.contains(layer.type())) {
                wrong.add(where + ": no such type " + layer.type());
            }
            if (!EffectLayer.DIRECTIONS.contains(layer.direction())) {
                wrong.add(where + ": no such direction " + layer.direction());
            }
            if (!EffectLayer.PLACES.contains(layer.at())) {
                wrong.add(where + ": no such place " + layer.at());
            }
            if (!EffectLayer.MEASURES.contains(layer.measure())) {
                wrong.add(where + ": no such measure " + layer.measure());
            }
        }
        if (!wrong.isEmpty()) {
            fail(String.join("\n", wrong));
        }
    }

    /**
     * Every texture named is on the classpath.
     *
     * <p>The client draws a soft glow in place of one it cannot find, which is the
     * right thing to do in a fight and exactly why it has to be caught here: a
     * fireball whose fire texture is misspelt still looks like SOMETHING.
     */
    @Test
    void everyTextureNamedIsThere() {
        var loader = DungeonEffectLayerTest.class.getClassLoader();
        var missing = new ArrayList<String>();
        for (var art : SETTINGS.effectLayers()) {
            var layer = drawn(art);
            if (layer.draws() && loader.getResource(layer.texture()) == null) {
                missing.add(art.effect() + " " + art.name() + ": " + layer.texture());
            }
        }
        if (!missing.isEmpty()) {
            fail("not on the classpath:\n" + String.join("\n", missing));
        }
    }

    /**
     * A block becomes exactly the layer it describes.
     *
     * <p>Read against the file's own text rather than against numbers written here,
     * so tuning the block never breaks this -- and a field lost anywhere on the way
     * from the text to what the client is handed always does. What the block leaves
     * out is the client's default, so this is also the test that the defaults are
     * not quietly replaced on the way across.
     */
    @Test
    void aBlockBecomesTheLayerItDescribes() throws java.io.IOException {
        var said = saidIn("MageFireball", "Fire");
        var layer = drawn(SETTINGS.effectLayers().stream()
                .filter(art -> art.effect().equals("MageFireball") && art.name().equals("Fire"))
                .findFirst().orElseThrow());

        assertEquals(said.get("Type"), layer.type());
        assertEquals(said.get("Texture"), layer.texture());
        assertEquals(!"Alpha".equalsIgnoreCase(said.get("Blend")), layer.additive());
        assertEquals(Integer.parseInt(said.get("Count")), layer.count());
        var life = said.get("Life").split("\\s+");
        assertEquals(Float.parseFloat(life[0]), layer.lifeMin(), 0.001f);
        assertEquals(Float.parseFloat(life[1]), layer.lifeMax(), 0.001f);
        var colour = said.get("Colour").split("\\s+");
        assertEquals((int) Integer.decode(colour[0]), layer.colourStart());
        assertEquals((int) Integer.decode(colour[1]), layer.colourEnd());
        assertEquals(Float.parseFloat(said.get("Drag")), layer.drag(), 0.001f);
        assertEquals(Float.parseFloat(said.get("Cover")), layer.cover(), 0.001f);

        assertFalse(said.containsKey("Stretch"), "the block this reads says nothing of stretch");
        assertEquals(EffectLayer.builder().build().stretch(), layer.stretch(), 0.001f,
                "so it has the client's own default");
    }

    /** One layer block as the file writes it: each field's name, and its value, a list's items spaced. */
    private static java.util.Map<String, String> saidIn(String effect, String name) {
        var said = new java.util.LinkedHashMap<String, String>();
        for (var block : uz.duke.core.data.DukeText.parse(uz.duke.dungeon.content.Content.data(), "data")) {
            if (!block.word().equals("Effect") || !named(block, effect)) {
                continue;
            }
            var layers = block.fields().stream().filter(field -> field.key().equals("Layers"))
                    .map(field -> ((uz.duke.core.data.Value.NestedList) field.value()).blocks())
                    .findFirst().orElse(java.util.List.of());
            for (var layer : layers) {
                if (named(layer, name)) {
                    for (var field : layer.fields()) {
                        said.put(field.key(), switch (field.value()) {
                            case uz.duke.core.data.Value.Text text -> text.text();
                            case uz.duke.core.data.Value.Items items -> String.join(" ", items.items());
                            default -> fail("a layer's " + field.key() + " holds blocks");
                        });
                    }
                    return said;
                }
            }
        }
        return fail("no Layer " + name + " in Effect " + effect);
    }

    private static boolean named(uz.duke.core.data.Block block, String name) {
        return block.fields().stream().anyMatch(field -> field.key().equals("Name")
                && field.value().equals(new uz.duke.core.data.Value.Text(name)));
    }

    /** Smoke and dust cover; they are never drawn as light. */
    @Test
    void smokeAndDustAreNeverDrawnAsLight() {
        for (var art : SETTINGS.effectLayers()) {
            var layer = drawn(art);
            var texture = layer.texture();
            if (texture.contains("smoke_") || texture.contains("dirt_") || texture.contains("scorch_")) {
                assertFalse(layer.additive(), art.effect() + " " + art.name()
                        + " draws " + texture + " as light, which brightens what it should hide");
            }
        }
    }

    /**
     * Anything that is light and happens at once is over quickly.
     *
     * <p>A flash, a burst of fire, a ring opening: past most of a second each of
     * them is no longer an impact but a thing hanging in the air, and it is in
     * the way of the next one. What is allowed longer is stuff -- smoke rising,
     * dust settling, a scorch -- because that is what is LEFT, and it is drawn as
     * cover rather than as light.
     */
    @Test
    void anythingThatIsLightAndHappensAtOnceIsOverQuickly() {
        var slow = new ArrayList<String>();
        for (var art : SETTINGS.effectLayers()) {
            var layer = drawn(art);
            boolean atOnce = EffectLayer.BURST.equals(layer.type())
                    || EffectLayer.IMPACT.equals(layer.type())
                    || EffectLayer.RING.equals(layer.type());
            if (atOnce && layer.additive() && layer.delay() + layer.lifeMax() > 0.8f) {
                slow.add(art.effect() + " " + art.name() + ": "
                        + (layer.delay() + layer.lifeMax()) + " s");
            }
        }
        if (!slow.isEmpty()) {
            fail("light that outlasts its moment:\n" + String.join("\n", slow));
        }
    }

    // ---- how long ----

    /**
     * The meteor's warning lasts exactly as long as the meteor takes to fall.
     *
     * <p>They used to be two numbers — WindUpFrames on the skill and MarkSeconds on
     * the effect — with a comment asking whoever changed one to change the other.
     * Now the warning says nothing about how long it lasts and takes the skill's.
     */
    @Test
    void theWarningLastsExactlyAsLongAsTheFall() {
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        var meteor = SETTINGS.skills().stream()
                .filter(skill -> skill.look().equals("MeteorCall")).findFirst().orElseThrow();
        float falls = meteor.windUpFrames() / (float) GameConstants.LOGICFRAMES_PER_SECOND;

        assertEquals(falls, visuals.getEffectSeconds("MeteorCall"), 0.001f,
                "the ground is marked for the wind-up");
        assertEquals(falls, visuals.getEffectSeconds("MeteorWarning"), 0.001f,
                "and the rock takes the same wind-up to come down");
        for (var art : SETTINGS.effectLayers()) {
            if (art.effect().equals("MeteorCall") && EffectLayer.MARK.equals(drawn(art).type())) {
                assertEquals(0f, drawn(art).seconds(), 0.001f, art.name()
                        + " says how long it lasts, and the skill already does");
            }
        }
    }

    /** A skill that lasts gives its duration to its look, in seconds. */
    @Test
    void aLastingSkillsLookLastsAsLongAsTheSkill() {
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        int checked = 0;
        for (var skill : SETTINGS.skills()) {
            if (skill.hasLook() && skill.durationFrames() > 0) {
                assertEquals(skill.durationFrames() / (float) GameConstants.LOGICFRAMES_PER_SECOND,
                        visuals.getEffectSeconds(skill.look()), 0.001f, skill.look());
                checked++;
            }
        }
        assertTrue(checked > 0, "no lasting skill was checked at all");
    }

    /**
     * The frost on whoever the nova caught lasts exactly as long as they are slowed.
     *
     * <p>The client cannot see the slow -- nothing it is sent says who is dragging
     * his feet -- so the frost is laid for the skill's SlowFrames on everyone the
     * nova reached, which is who the simulation slowed.
     */
    @Test
    void theFrostOnTheCaughtLastsAsLongAsTheSlow() {
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        var nova = SETTINGS.skills().stream()
                .filter(skill -> skill.look().equals("FrostNova")).findFirst().orElseThrow();
        assertTrue(nova.slowFrames() > 0, "the nova slows what it catches");

        assertEquals(nova.slowFrames() / (float) GameConstants.LOGICFRAMES_PER_SECOND,
                visuals.getEffectSeconds("FrostNova"), 0.001f);
        var caught = SETTINGS.effectLayers().stream()
                .filter(art -> art.effect().equals("FrostNova"))
                .map(DungeonEffectLayerTest::drawn)
                .filter(layer -> EffectLayer.CAUGHT.equals(layer.at()))
                .toList();
        assertFalse(caught.isEmpty(), "something is drawn on whoever the nova caught");
        for (var layer : caught) {
            assertEquals(0f, layer.seconds(), 0.001f,
                    "a layer on the caught that says how long it lasts no longer follows the slow");
        }
    }

    /**
     * The meteor's warning is exactly as wide as the blast it warns of.
     *
     * <p>The one shape in the game a player bets his life on: step out of the
     * orange and live. So it is measured in the skill's reach rather than in units,
     * and the reach it is given is the skill's Radius.
     */
    @Test
    void theWarningIsAsWideAsTheBlast() {
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        var meteor = SETTINGS.skills().stream()
                .filter(skill -> skill.look().equals("MeteorCall")).findFirst().orElseThrow();

        assertEquals(meteor.radius(), visuals.getEffectReach("MeteorCall"), 0.001f);
        var warning = SETTINGS.effectLayers().stream()
                .filter(art -> art.effect().equals("MeteorCall") && art.name().equals("Warning"))
                .map(DungeonEffectLayerTest::drawn).findFirst().orElseThrow();
        assertEquals(EffectLayer.REACH, warning.measure());
    }

    /** A layer measured in reach belongs to something the game gives a reach. */
    @Test
    void everyLayerMeasuredInReachHasAReach() {
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        var unmeasured = new ArrayList<String>();
        for (var art : SETTINGS.effectLayers()) {
            if (EffectLayer.REACH.equals(drawn(art).measure())
                    && visuals.getEffectReach(art.effect()) <= 0f) {
                unmeasured.add(art.effect() + " " + art.name());
            }
        }
        if (!unmeasured.isEmpty()) {
            fail("measured in the reach of a skill that has none:\n"
                    + String.join("\n", unmeasured));
        }
    }

    /**
     * A hit flash is a flash: there, and gone before the next blow of an ordinary
     * fight lands. Much past a fifth of a second a creature is not flashing, it
     * is lit.
     */
    @Test
    void theHitFlashIsBrief() {
        assertTrue(SETTINGS.hitFeel().hitFlashStrength() > 0f, "the shipped file should flash");
        assertTrue(SETTINGS.hitFeel().hitFlashSeconds() > 0.05f && SETTINGS.hitFeel().hitFlashSeconds() <= 0.2f,
                "a flash of " + SETTINGS.hitFeel().hitFlashSeconds() + " s");
        assertTrue(SETTINGS.hitFeel().shakeScale() >= 0f);
    }

    // ---- a blast is exactly as wide as it hurts ----

    /**
     * Every effect a skill throws that bursts, with the Radius its blast hurts within.
     *
     * <p>A skill that hurts nothing has no blast, whatever its Radius says: a summoning's
     * Radius is how far out what it calls up rises, and its rift is no burst.
     */
    private static java.util.Map<String, Float> blastsAndTheirRadius() {
        var carries = new java.util.HashMap<String, String>();
        for (var projectile : SETTINGS.projectiles()) {
            carries.put(projectile.name(), projectile.effect());
        }
        var blasts = new java.util.HashMap<String, Float>();
        for (var skill : SETTINGS.skills()) {
            var thrown = skill.hasProjectile() ? carries.get(skill.projectile()) : null;
            if (thrown != null && skill.radius() > 0f && skill.damage() > 0f) {
                blasts.put(thrown, skill.radius());
            }
        }
        return blasts;
    }

    /**
     * Each blast's edge is drawn where its damage stops.
     *
     * <p>A burst smaller than the ground it hurts lies about where it is safe to
     * stand, and one bigger lies the other way. So the rings of a blast are
     * measured in the skill's reach, the reach the effect is given is the very
     * Radius the simulation hurts within, and each ring's brightest line -- found
     * in its own texture here, not assumed -- lies on that radius.
     */
    @Test
    void theEdgeOfEachBlastIsWhereItsDamageStops() throws java.io.IOException {
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        var blasts = blastsAndTheirRadius();
        assertTrue(blasts.containsKey("MageFireball") && blasts.containsKey("MeteorWarning"),
                "the fireball and the meteor both burst: " + blasts);

        for (var blast : blasts.entrySet()) {
            assertEquals(blast.getValue(), visuals.getEffectReach(blast.getKey()), 0.001f,
                    blast.getKey() + " is not given the Radius its blast hurts within");
            int rings = 0;
            for (var art : SETTINGS.effectLayers()) {
                var layer = drawn(art);
                if (!art.effect().equals(blast.getKey()) || !EffectLayer.RING.equals(layer.type())) {
                    continue;
                }
                assertEquals(EffectLayer.REACH, layer.measure(), art.name() + " is not measured in reach");
                float edge = layer.sizeEnd() * brightestRadius(layer.texture()) / 2f;
                assertEquals(1f, edge, 0.05f, blast.getKey() + " " + art.name()
                        + " ends at " + edge + " of the blast's radius");
                rings++;
            }
            assertTrue(rings > 0, blast.getKey() + " draws no ring at its edge");
        }
    }

    /**
     * Nothing a blast throws flies past where its damage stops.
     *
     * <p>Fire, smoke and sparks thrown past the edge would draw the blast wider than
     * it hurts. Worked out from the layer the way the shader moves a particle: born
     * anywhere in its radius, half its largest size either side, and carried as far
     * as its speed and the air let it in its longest life.
     */
    @Test
    void nothingABlastThrowsFliesPastWhereItsDamageStops() {
        for (var blast : blastsAndTheirRadius().entrySet()) {
            float reach = blast.getValue();
            for (var art : SETTINGS.effectLayers()) {
                var layer = drawn(art);
                if (!art.effect().equals(blast.getKey()) || !EffectLayer.BURST.equals(layer.type())) {
                    continue;
                }
                float unit = EffectLayer.REACH.equals(layer.measure()) ? reach : 1f;
                float extent = layer.radius() * unit
                        + Math.max(layer.sizeStart(), layer.sizeEnd()) * (1f + layer.sizeJitter())
                                * unit / 2f
                        + thrownAtMost(layer);
                assertTrue(extent <= reach * 1.05f, blast.getKey() + " " + art.name()
                        + " reaches " + extent + " past a blast of " + reach);
            }
        }
    }

    /** How far across the floor a particle of this layer can be carried. */
    private static float thrownAtMost(EffectLayer layer) {
        float speed = Math.max(Math.abs(layer.speedMin()), Math.abs(layer.speedMax()));
        float across = switch (layer.direction()) {
            case EffectLayer.UP, EffectLayer.DOWN ->
                    (float) Math.sin(Math.toRadians(Math.min(90f, layer.spread())));
            case EffectLayer.NONE -> 0f;
            default -> 1f;
        };
        float life = layer.lifeMax();
        float carried = layer.drag() > 0.0001f
                ? (float) ((1.0 - Math.exp(-layer.drag() * life)) / layer.drag()) : life;
        return speed * across * carried;
    }

    /** Where a ring texture is brightest, as a share of its half-width. */
    private static float brightestRadius(String texture) throws java.io.IOException {
        var url = DungeonEffectLayerTest.class.getClassLoader().getResource(texture);
        assertNotNull(url, texture);
        var image = javax.imageio.ImageIO.read(url);
        int bins = 200;
        double[] light = new double[bins];
        int[] counted = new int[bins];
        double middleX = (image.getWidth() - 1) / 2.0;
        double middleY = (image.getHeight() - 1) / 2.0;
        double half = image.getWidth() / 2.0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int bin = (int) (Math.hypot(x - middleX, y - middleY) / half * bins);
                if (bin >= bins) {
                    continue;
                }
                int argb = image.getRGB(x, y);
                double alpha = ((argb >>> 24) & 0xFF) / 255.0;
                double grey = (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 765.0;
                light[bin] += grey * alpha;
                counted[bin]++;
            }
        }
        int brightest = 0;
        for (int bin = 1; bin < bins; bin++) {
            if (counted[bin] > 0 && light[bin] / counted[bin]
                    > light[brightest] / Math.max(1, counted[brightest])) {
                brightest = bin;
            }
        }
        return (brightest + 0.5f) / bins;
    }

    /** And the budget the file sets reaches the client. */
    @Test
    void theParticleCeilingIsTheFiles() {
        assertTrue(SETTINGS.effectBudget().maxParticles() > 0, "a ceiling of nothing draws no layers");
    }

    // ---- columns of light, and the moments they are played on ----

    /** Every moment the file gives a look is one the client notices, and its look is drawn. */
    @Test
    void everyMomentIsOneTheClientPlaysAndItsLookIsLayered() {
        var layered = SETTINGS.effectLayers().stream()
                .map(DungeonSettings.EffectLayerArt::effect).collect(Collectors.toSet());
        assertFalse(SETTINGS.moments().isEmpty(), "the file gives no moment a look");
        for (var moment : SETTINGS.moments()) {
            assertTrue(Visuals.MOMENTS.contains(moment.name()),
                    "Moment " + moment.name() + " is no moment the client notices");
            assertTrue(layered.contains(moment.effect()), "Moment " + moment.name()
                    + " plays " + moment.effect() + ", which is drawn in no layers");
        }
    }

    /** A column goes up or down, stands some height, and arrives rather than appearing. */
    @Test
    void aColumnGoesUpOrDownAndArrives() {
        var wrong = new ArrayList<String>();
        for (var art : SETTINGS.effectLayers()) {
            var layer = drawn(art);
            if (!EffectLayer.PILLAR.equals(layer.type())) {
                continue;
            }
            var where = art.effect() + " " + art.name();
            if (!EffectLayer.UP.equals(layer.direction()) && !EffectLayer.DOWN.equals(layer.direction())) {
                wrong.add(where + " goes " + layer.direction());
            }
            if (layer.height() <= 0f) {
                wrong.add(where + " stands no height");
            }
            if (layer.rise() <= 0f) {
                wrong.add(where + " is there at once instead of arriving");
            }
        }
        if (!wrong.isEmpty()) {
            fail(String.join("\n", wrong));
        }
    }

    /**
     * A look with a column in it is over in half a second to eight tenths: long enough
     * to be seen arriving and going out, and too short to hang over the fight.
     */
    @Test
    void aColumnOfLightIsOverInHalfASecondToEightTenths() {
        var longest = new java.util.HashMap<String, Float>();
        var columns = new java.util.TreeSet<String>();
        for (var art : SETTINGS.effectLayers()) {
            var layer = drawn(art);
            if (EffectLayer.PILLAR.equals(layer.type())) {
                columns.add(art.effect());
            }
            float over = layer.delay()
                    + (EffectLayer.LIGHT.equals(layer.type()) ? layer.seconds() : layer.lifeMax());
            longest.merge(art.effect(), over, Math::max);
        }
        assertFalse(columns.isEmpty(), "no column of light in the file");
        for (var effect : columns) {
            float over = longest.get(effect);
            assertTrue(over >= 0.5f && over <= 0.8f, effect + " lasts " + over + " s");
        }
    }

    /** A level rises out of the floor, and so does the boss falling; arriving comes down. */
    @Test
    void aLevelRisesAndArrivingComesDown() {
        assertEquals(EffectLayer.UP, columnOf(Visuals.LEVEL_UP));
        assertEquals(EffectLayer.UP, columnOf(Visuals.BOSS_DOWN));
        assertEquals(EffectLayer.DOWN, columnOf(Visuals.ARRIVED));
    }

    /** Which way the column in a moment's look goes. */
    private static String columnOf(String momentName) {
        var moment = SETTINGS.moments().stream().filter(m -> m.name().equals(momentName))
                .findFirst().orElseThrow(() -> new AssertionError("no Moment " + momentName));
        return SETTINGS.effectLayers().stream()
                .filter(art -> art.effect().equals(moment.effect()))
                .map(DungeonEffectLayerTest::drawn)
                .filter(layer -> EffectLayer.PILLAR.equals(layer.type()))
                .map(EffectLayer::direction)
                .findFirst()
                .orElseThrow(() -> new AssertionError(moment.effect() + " has no column in it"));
    }

    /** The boss falling is drawn bigger than a level, which is what makes it the floor's. */
    @Test
    void theBossFallingIsBiggerThanALevel() {
        float level = SETTINGS.moments().stream().filter(m -> m.name().equals(Visuals.LEVEL_UP))
                .findFirst().orElseThrow().scale();
        float boss = SETTINGS.moments().stream().filter(m -> m.name().equals(Visuals.BOSS_DOWN))
                .findFirst().orElseThrow().scale();
        assertTrue(boss > level, "a boss at " + boss + " against a level at " + level);
    }

    /** A column's block becomes the column it describes -- the fields a column has and nothing else does. */
    @Test
    void aColumnBlockBecomesTheColumnItDescribes() throws java.io.IOException {
        var said = saidIn("GoldenPillar", "Core");
        var layer = drawn(SETTINGS.effectLayers().stream()
                .filter(art -> art.effect().equals("GoldenPillar") && art.name().equals("Core"))
                .findFirst().orElseThrow());

        assertEquals(said.get("Type"), layer.type());
        assertEquals(said.get("Direction"), layer.direction());
        assertEquals(Float.parseFloat(said.get("Height")), layer.height(), 0.001f);
        assertEquals(Float.parseFloat(said.get("Rise")), layer.rise(), 0.001f);
        assertEquals(Float.parseFloat(said.get("RiseEase")), layer.riseEase(), 0.001f);
        assertEquals("Yes".equalsIgnoreCase(said.get("Follows")), layer.follows());
    }
}
