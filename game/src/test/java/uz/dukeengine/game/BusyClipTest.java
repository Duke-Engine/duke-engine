package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.DamageType;

/** A thing busy at its work shows the clip it loops for those frames and not after, nor once a blow lands (E67). */
class BusyClipTest {

    private static String busyOf(DukeGame game, int id) {
        return game.getSnapshot().units().stream().filter(one -> one.id() == id).findFirst().orElseThrow()
                .busyClip();
    }

    @Test
    void busyForThirtyFramesSaysItsClipForThoseFramesAndNotAfter() {
        var game = DukeGame.create("busy").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(40, 40);
        var me = game.addPlayer("Me", Color.BLUE);
        game.localPlayer(me).spawn("Rifleman", me, 100f, 100f);
        game.runHeadless(1);
        var worker = game.getLogic().getObjects().getFirst();
        int id = worker.getId().value();

        worker.busy("Chop", 30);
        game.runHeadless(1);
        assertEquals("Chop", busyOf(game, id));
        game.runHeadless(28);
        assertEquals("Chop", busyOf(game, id), "still at it");
        game.runHeadless(2);
        assertNull(busyOf(game, id), "its frames run out");

        worker.busy("Chop", 30);
        game.runHeadless(1);
        worker.getBody().damage(10f, DamageType.NORMAL,
                new uz.dukeengine.core.module.Death(uz.dukeengine.core.module.DeathType.NORMAL, null, -1));
        game.runHeadless(1);
        assertNull(busyOf(game, id), "a blow breaks it off");
    }
}
