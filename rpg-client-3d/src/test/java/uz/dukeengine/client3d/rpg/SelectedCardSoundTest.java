package uz.dukeengine.client3d.rpg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Clicking a skeleton does not sound like levelling up.
 *
 * <p>The bar describes whatever the player has picked out, so the line it reads
 * now says "a skeleton, 34 of 40" and a moment ago said "Erika, 7th level" — and
 * the thing that listens to that line for moments worth hearing was written when
 * only one card could ever be on it. A level was heard by the rank changing; a
 * card with no rank on it changed the rank twice per click, and both changes rang
 * the bell. Now the card's rank is not listened to at all: a level is noticed on
 * his own field, whatever is picked out, and told -- see the client's {@code RunMoments}.
 *
 * <p>The kind of fault that never shows up in a screenshot and is deafening in
 * the game, which is why it is worth a test of its own.
 */
class SelectedCardSoundTest {

    /** The cues it was asked to play, written down. */
    private static final class Heard {
        private final List<String> played = new ArrayList<>();
    }

    private static final String HERO =
            "name=Erika|title=O'q ustasi|rank=7-daraja|hp=128/200|xp=38/100"
                    + "|depth=III|depthWord=CHUQURLIK";

    private static final String SKELETON =
            "name=Skeleton|hp=34/40|depth=III|depthWord=CHUQURLIK|face=skull|stat=Zarba,7";

    private static CardSounds listening(Heard heard) {
        return new CardSounds(heard.played::add);
    }

    @Test
    void pickingOutACreatureAndLettingGoRingsNothing() {
        var heard = new Heard();
        var sounds = listening(heard);
        sounds.status(HeroPanel.Reading.parse(HERO));
        heard.played.clear();

        sounds.status(HeroPanel.Reading.parse(SKELETON));
        sounds.status(HeroPanel.Reading.parse(HERO));

        assertEquals(List.of(), heard.played,
                "looking at something is not a thing that happened to him");
    }

    /**
     * A rank changing on the card rings nothing by itself. The card is whoever is
     * picked out, and a level gained with a skeleton selected used to go unheard and
     * then ring late, the moment he was picked again.
     */
    @Test
    void aRankChangingOnTheCardIsNotALevel() {
        var heard = new Heard();
        var sounds = listening(heard);
        sounds.status(HeroPanel.Reading.parse(HERO));
        heard.played.clear();

        sounds.status(HeroPanel.Reading.parse(HERO.replace("7-daraja", "8-daraja")));

        assertEquals(List.of(), heard.played, "a level is told, never read off the card");
    }

    /**
     * The floor is still the floor while he is looking at something.
     *
     * <p>Depth is on a creature's card on purpose — it belongs to the dungeon, not
     * to whatever is selected — so going down a floor with a skeleton picked out
     * is heard like any other.
     */
    @Test
    void goingDownIsHeardEvenWhileLookingAtSomething() {
        var heard = new Heard();
        var sounds = listening(heard);
        sounds.status(HeroPanel.Reading.parse(SKELETON));
        heard.played.clear();

        sounds.status(HeroPanel.Reading.parse(SKELETON.replace("depth=III", "depth=IV")));

        assertTrue(heard.played.contains("depth"), heard.played.toString());
    }
}
