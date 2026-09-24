package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.Drawn;

/**
 * A template that names more than one model, and the client choosing between them.
 *
 * <p>The choosing is a pure function of a snapshot and the world's conditions, which is what makes it
 * checkable without a window: what {@code DukeRtsApp} does with the answer is swap a child node, and what
 * decides the answer is all here.
 */
class ConditionalModelTest {

    private static final String PLAIN = "models/barracks.glb";
    private static final String HURT = "models/barracks_d.glb";
    private static final String WRECK = "models/barracks_r.glb";
    private static final String SNOW = "models/barracks_s.glb";
    private static final String HURT_IN_SNOW = "models/barracks_ds.glb";

    /** A building that looks different hurt, wrecked, in snow, and hurt in snow. */
    private static Visuals.UnitVisual barracks() {
        var visuals = Visuals.create().unit("Barracks", look -> look
                .model(PLAIN)
                .model(Set.of("DAMAGED"), HURT)
                // Two words, because rubble is also damaged and the engine counts words rather than
                // knowing that. A game that wrote RUBBLE alone would be asking for a coin toss with
                // DAMAGED, and would get the alphabet — see the tie-break below.
                .model(Set.of("DAMAGED", "RUBBLE"), WRECK)
                .model(Set.of("SNOW"), SNOW)
                .model(Set.of("DAMAGED", "SNOW"), HURT_IN_SNOW)
                .whenHurt("DAMAGED", 0.5f)
                .whenHurt("RUBBLE", 0.1f));
        return visuals.of("Barracks");
    }

    @Test
    void aThingIsDrawnWithTheModelForTheStateItIsIn() {
        var look = barracks();

        assertEquals(PLAIN, look.modelFor(1f, Set.of()), "whole, in fair weather");
        assertEquals(HURT, look.modelFor(0.4f, Set.of()), "under half health");
        assertEquals(PLAIN, look.modelFor(0.5f, Set.of()), "'below' is below, not at");
        assertEquals(WRECK, look.modelFor(0.05f, Set.of()),
                "under a tenth: DAMAGED and RUBBLE both hold, and the two-word look says more about it");
    }

    /**
     * Deeper thresholds are not a rule the engine has. It counts words, and a game that wants a wreck to
     * beat a dent says so by naming both words — which is the same rule the world's conditions use, rather
     * than a second one about health.
     */
    @Test
    void howDeepAThresholdIsCountsForNothing() {
        var naive = Visuals.create().unit("Hut", look -> look.model(PLAIN)
                .model(Set.of("DAMAGED"), HURT)
                .model(Set.of("RUBBLE"), WRECK)
                .whenHurt("DAMAGED", 0.5f)
                .whenHurt("RUBBLE", 0.1f)).of("Hut");

        assertEquals(HURT, naive.modelFor(0.05f, Set.of()),
                "one word each, so the alphabet settles it and the wreck never shows");
        assertEquals(WRECK, barracks().modelFor(0.05f, Set.of()), "saying both words is how it is meant");
    }

    @Test
    void theWorldsOwnConditionsCountTheSameWayTheThingsDo() {
        var look = barracks();

        assertEquals(SNOW, look.modelFor(1f, Set.of("SNOW")));
        assertEquals(PLAIN, look.modelFor(1f, Set.of("NIGHT")), "a word nothing was drawn for changes nothing");
        assertEquals(HURT_IN_SNOW, look.modelFor(0.4f, Set.of("SNOW")),
                "two words beat one: DAMAGED SNOW over SNOW and over DAMAGED");
        assertEquals(HURT_IN_SNOW, look.modelFor(0.4f, Set.of("SNOW", "NIGHT")),
                "and an extra world condition nobody asked about does not spoil the match");
    }

    /**
     * The one thing here that would be invisible until two players disagreed about what they were looking
     * at. Two candidates fitting equally well have to be settled by something both machines compute the
     * same way, which an iteration order is not.
     */
    @Test
    void twoCandidatesThatFitEquallyWellAreSettledTheSameWayOnEveryMachine() {
        // AURORA and NIGHT both hold, both are one word, and each names a different model.
        var oneWay = Visuals.create().unit("Tower", look -> look.model(PLAIN)
                .model(Set.of("NIGHT"), "models/night.glb")
                .model(Set.of("AURORA"), "models/aurora.glb")).of("Tower");
        var theOther = Visuals.create().unit("Tower", look -> look.model(PLAIN)
                .model(Set.of("AURORA"), "models/aurora.glb")
                .model(Set.of("NIGHT"), "models/night.glb")).of("Tower");

        var world = Set.of("NIGHT", "AURORA");
        assertEquals(oneWay.modelFor(1f, world), theOther.modelFor(1f, world),
                "written in either order, the same model is chosen");
        assertEquals("models/aurora.glb", oneWay.modelFor(1f, world), "the sorted words decide it");
    }

    /** A template that names no extra models is exactly what it was, and pays nothing for the feature. */
    @Test
    void aTemplateThatNamesNoExtraModelsIsUnchanged() {
        var plain = Visuals.create().unit("Rifleman", look -> look.model("models/soldier.glb"))
                .of("Rifleman");

        assertEquals("models/soldier.glb", plain.modelFor(1f, Set.of()));
        assertEquals("models/soldier.glb", plain.modelFor(0f, Set.of("SNOW", "NIGHT", "DAMAGED")));
        assertNull(Visuals.create().of("Nobody").modelFor(1f, Set.of()), "and one with no model at all");
    }

    /** The world says its conditions once, when the map is loaded. */
    @Test
    void theWorldIsToldItsConditionsOnce() {
        var visuals = Visuals.create();
        assertTrue(visuals.getWorldConditions().isEmpty(), "a game that never said anything");

        visuals.world("SNOW", "NIGHT");
        assertEquals(Set.of("SNOW", "NIGHT"), visuals.getWorldConditions());

        visuals.world();
        assertTrue(visuals.getWorldConditions().isEmpty(), "and it may take them back");
    }

    /** A template's own words reach the client through {@code Visuals.draw}, like the rest of its look. */
    @Test
    void aTemplateBlockCarriesItsModelsThroughTheSameSeamAsTheRestOfItsLook() {
        record Building(String name, String model, Map<String, String> models,
                Map<String, Float> whenHurt) implements Drawn {

            @Override
            public List<uz.dukeengine.core.module.ModuleData> modules() {
                return List.of();
            }
        }
        var visuals = Visuals.create();
        visuals.draw(new Building("Barracks", PLAIN,
                Map.of("DAMAGED", HURT, "DAMAGED SNOW", HURT_IN_SNOW),
                Map.of("DAMAGED", 0.5f)), null);

        var look = visuals.of("Barracks");
        assertEquals(PLAIN, look.modelFor(1f, Set.of()));
        assertEquals(HURT, look.modelFor(0.3f, Set.of()));
        assertEquals(HURT_IN_SNOW, look.modelFor(0.3f, Set.of("SNOW")),
                "the two words of a key, as a block writes them: \"DAMAGED SNOW\"");
    }

    /** The words of a key are a set, however they were spelt: order and spacing say nothing. */
    @Test
    void theWordsOfAConditionAreASetHoweverTheyWereWritten() {
        record Building(String name, String model, Map<String, String> models) implements Drawn {

            @Override
            public List<uz.dukeengine.core.module.ModuleData> modules() {
                return List.of();
            }
        }
        var visuals = Visuals.create();
        visuals.draw(new Building("Wall", PLAIN, Map.of("SNOW   NIGHT", "models/wall_sn.glb")), null);

        assertEquals("models/wall_sn.glb", visuals.of("Wall").modelFor(1f, Set.of("NIGHT", "SNOW")));
    }

    /** A thing's own words — an upgrade's weapon set — choose among its models beside the world's and its health's. */
    @Test
    void aThingsOwnWordsChooseItsModelToo() {
        var look = Visuals.create().unit("Humvee", humvee -> humvee.model(PLAIN)
                .model(Set.of("WEAPONSET_PLAYER_UPGRADE"), "models/humvee_tow.glb")
                .model(Set.of("WEAPONSET_PLAYER_UPGRADE", "DAMAGED"), "models/humvee_tow_d.glb")
                .whenHurt("DAMAGED", 0.5f)).of("Humvee");

        assertEquals("models/humvee_tow.glb",
                look.modelFor(look.holding(1f, Set.of(), java.util.List.of("WEAPONSET_PLAYER_UPGRADE"))));
        assertEquals("models/humvee_tow_d.glb",
                look.modelFor(look.holding(0.3f, Set.of(), java.util.List.of("WEAPONSET_PLAYER_UPGRADE"))),
                "its own word and the one its health decides, together");
        assertEquals(PLAIN, look.modelFor(look.holding(1f, Set.of(), java.util.List.of())));
    }
}
