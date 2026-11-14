package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.DukeGame;

/** A room's probe asked of the client: refused without a volume, and nothing at all without the physically based look. */
class RoomProbeTest {

    @Test
    void aRoomWithoutVolumeIsRefusedWhereItIsAsked() {
        var client = Duke3D.of(DukeGame.create("room-probe-test"), Visuals.create());
        assertThrows(IllegalArgumentException.class,
                () -> client.roomProbe(Vector3f.ZERO, new Vector3f(2f, 0f, 2f), true));
    }

    @Test
    void withoutThePhysicallyBasedLookARoomIsNothing() {
        var client = Duke3D.of(DukeGame.create("room-probe-test"), Visuals.create());
        var app = new DukeRtsApp(DukeGame.create("room-probe-test"), Visuals.create(), Shell.standard(), null, null, null,
                null, null, null, null);
        var probe = client.roomProbe(Vector3f.ZERO, new Vector3f(2f, 1f, 2f), true);
        app.roomProbe(probe);
        assertNull(probe.room, "no probe placed");
        app.relightRoomProbes();
        probe.rebake();
        probe.remove();
    }
}
