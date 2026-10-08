package uz.dukeengine.client3d.rts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.HudPart;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.rts.message.GameMessage;

/** The RTS's part is found on the classpath, and rallies a building where the ground was clicked. */
class RtsHudTest {

    private static final UnitView FACTORY = new UnitView(7, "WarFactory", 1, 100f, 100f, 0f, 1000f, 1000f,
            true, true, false, false, 0);

    @Test
    void itIsFoundAndRalliesTheBuildingWhereTheClickWas() {
        var parts = HudPart.found();
        assertTrue(parts.stream().anyMatch(RtsHud.class::isInstance), "listed in META-INF/services");

        var place = new Coord3D(300f, 200f, 0f);
        assertEquals(new GameMessage.SetRallyPoint(1, new ObjectId(7), place),
                new RtsHud().rally(1, FACTORY, place));
    }
}
