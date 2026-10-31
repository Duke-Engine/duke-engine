package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ColourTest {

    @Test
    void packsLikeAwtAndReadsBack() {
        var colour = Colour.rgba(1, 2, 3, 200);
        assertEquals(0xC8010203, colour.argb());
        assertEquals(1, colour.red());
        assertEquals(2, colour.green());
        assertEquals(3, colour.blue());
        assertEquals(200, colour.alpha());
        assertEquals(new java.awt.Color(255, 200, 0).getRGB(), Colour.ORANGE.argb());
    }

    @Test
    void aChannelOutOfRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Colour.rgb(256, 0, 0));
    }

    @Test
    void aPlayerKeepsTheColourItWasAddedWith() {
        var game = DukeGame.create("Colours");
        var player = game.addPlayer("One", Colour.CYAN);
        assertEquals(Colour.CYAN, player.getColour());
    }
}
