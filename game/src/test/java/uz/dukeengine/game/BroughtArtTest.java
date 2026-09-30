package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A match's art follows what its things leave behind, a chain of it, and nothing a name does not answer to. */
class BroughtArtTest {

    @Test
    void whatATemplateBringsIsPlannedAChainIsFollowedAndAnUnknownNameIgnored() {
        var leaves = Map.of("Rifleman", List.of("PowerPlant", "RiflemanCorpse"), "PowerPlant", List.of("Barracks"));
        var game = DukeGame.create("wrecks").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(40, 40)
                .brings(name -> leaves.getOrDefault(name, List.of())).placesEverythingAtSetup();
        var you = game.addPlayer("You", Color.BLUE);
        game.localPlayer(you);
        game.spawn("Rifleman", you, 50f, 50f);
        game.boot();

        assertEquals(Set.of("Rifleman", "PowerPlant", "Barracks", "Tank"), game.templatesThisMatchCanDraw(),
                "its hulk, what the hulk leaves and what that builds, and no art for a name nothing answers to");
    }
}
