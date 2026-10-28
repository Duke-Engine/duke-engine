package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** A look kept from its owner's enemies: shown and picked for everyone else, neither for them (a GLA cache). */
class HiddenFromEnemiesTest {

    private static final Visuals VISUALS = Visuals.create()
            .unit("Cache", look -> look.model("models/cache.glb").hiddenFromEnemies())
            .unit("Tank", look -> look.model("models/tank.glb"))
            .unit("Birds", Visuals.UnitVisual::noShape);

    @Test
    void itsOwnersSideAndANeutralSeeAndPickItAndAnEnemyDoesNot() {
        var cache = VISUALS.of("Cache");

        assertTrue(cache.shownTo(false), "its owner, his ally or a neutral");
        assertTrue(cache.pickableBy(true, false));
        assertFalse(cache.shownTo(true), "an enemy: no model");
        assertFalse(cache.pickableBy(true, true), "and nothing there picks it");
    }

    @Test
    void aLookThatSaysNothingIsShownAndPickedToEveryone() {
        var tank = VISUALS.of("Tank");

        assertTrue(tank.shownTo(true));
        assertTrue(tank.pickableBy(true, true));
        assertFalse(tank.pickableBy(false, false), "unless the game made it unselectable");
    }

    @Test
    void aThingWithNoShapeIsNeverPickedByAClickOrABox() {
        var birds = VISUALS.of("Birds");
        assertFalse(birds.pickableBy(true, false));
        assertFalse(birds.pickableBy(true, true));

        var inBox = SelectionBox.inside(0f, 0f, 100f, 100f, List.of(
                new SelectionBox.Candidate(1, 50f, 50f, true, birds.pickableBy(true, false)),
                new SelectionBox.Candidate(2, 50f, 50f, true, VISUALS.of("Tank").pickableBy(true, false)),
                new SelectionBox.Candidate(3, 50f, 50f, false, VISUALS.of("Cache").pickableBy(true, true))));
        assertEquals(List.of(2), inBox.stream().map(SelectionBox.Candidate::id).toList(),
                "the box takes the tank, not the shapeless thing nor the cache hidden from this viewer");
    }
}
