package uz.duke.game;

import java.util.ArrayList;
import uz.duke.core.GameClient;
import uz.duke.rts.thing.RtsKinds;
import uz.duke.game.view.UnitView;
import uz.duke.game.view.WorldSnapshot;
import uz.duke.rts.module.PowerGrid;

/**
 * The presentation client behind {@link DukeGame}: once per client frame it
 * copies what the local player can see into an immutable {@link WorldSnapshot}
 * for the Swing layer to draw.
 *
 * <p>This is the only place simulation state crosses threads, and it crosses as
 * a deep copy — the window never touches live {@code GameObject}s, so the
 * deterministic logic stays single-threaded.
 */
final class RtsClient extends GameClient {

    private final RtsLogic logic;
    private volatile int viewerPlayer = -1; // bound once players exist, at game start
    private volatile WorldSnapshot snapshot = WorldSnapshot.EMPTY;
    private volatile String banner = "";

    /** Big centered message ("VICTORY") shown by the display layer. */
    void setBanner(String banner) {
        this.banner = banner == null ? "" : banner;
    }

    RtsClient(RtsLogic logic) {
        this.logic = logic;
    }

    void setViewerPlayer(int viewerPlayer) {
        this.viewerPlayer = viewerPlayer;
    }

    /** The latest frame of the world; safe to call from any thread. */
    WorldSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    protected void render() {
        if (viewerPlayer < 0) {
            return; // no viewer bound yet — nothing to show
        }
        var units = new ArrayList<UnitView>();
        for (var object : logic.getVisibleObjects(viewerPlayer)) {
            if (object.isContained()) {
                continue; // riding inside a transport — not on the map
            }
            var template = object.getTemplate();
            var position = object.getPosition();
            var body = object.getBody();
            var ai = object.findModule(uz.duke.core.module.MoveUpdate.class);
            var weapon = object.findModule(uz.duke.rts.module.WeaponUpdate.class);
            var production = object.findModule(uz.duke.rts.module.ProductionUpdate.class);
            units.add(new UnitView(
                    object.getId().value(),
                    template.getName(),
                    object.getPlayerIndex(),
                    position.x(),
                    position.y(),
                    object.getOrientation(),
                    body == null ? 0f : body.getHealth(),
                    body == null ? 0f : body.getMaxHealth(),
                    template.isKindOf(RtsKinds.STRUCTURE),
                    template.isKindOf(RtsKinds.SELECTABLE),
                    ai != null && ai.isMoving(),
                    weapon != null && weapon.isAttacking(),
                    production == null ? -1 : production.getQueueSize()));
        }
        var player = logic.getRtsPlayer(viewerPlayer);
        snapshot = new WorldSnapshot(
                logic.getFrame(),
                logic.getGameTimeSeconds(),
                logic.isGamePaused(),
                player == null ? 0 : player.getMoney(),
                PowerGrid.surplus(logic, viewerPlayer),
                units,
                banner);
    }
}
