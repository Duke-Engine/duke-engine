package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Where a part's panel stands, told to a game's painter as the client has it, and nowhere with no client behind it. */
class BottomPanelTest {

    @Test
    void aPainterIsToldWhereTheClientSaysItStandsAndNowhereWithNoClientBehindIt() {
        var stands = new Canvas.Box(0f, 708f, 1600f, 192f);
        var client = new CanvasFrame.World() {
            @Override
            public Canvas.Point screenOf(float x, float y, float height) {
                return null;
            }

            @Override
            public Canvas.Box barOf(int id) {
                return null;
            }

            @Override
            public Canvas.Box bottomPanel() {
                return stands;
            }
        };
        var text = new CanvasText(_ -> null);

        assertEquals(stands, new CanvasFrame(1600, 900, text, Map.<String, int[]>of()::get, client).bottomPanel());
        assertNull(new CanvasFrame(1600, 900, text, Map.<String, int[]>of()::get).bottomPanel());
    }
}
