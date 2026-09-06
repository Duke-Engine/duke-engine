package uz.duke.game;

import uz.duke.core.GameClient;
import uz.duke.core.GameEngine;
import uz.duke.core.GameLogic;

/**
 * The concrete engine build behind {@link DukeGame} — the role SAGE's
 * device-specific {@code CreateGameEngine()} plays. It simply hands the
 * pre-constructed RTS logic and client to the engine framework.
 */
final class RtsGameEngine extends GameEngine {

    private final RtsLogic rtsLogic;
    private final RtsClient rtsClient;
    private MultiplayerSession session;

    RtsGameEngine(RtsLogic rtsLogic, RtsClient rtsClient) {
        this.rtsLogic = rtsLogic;
        this.rtsClient = rtsClient;
    }

    void setSession(MultiplayerSession session) {
        this.session = session;
    }

    /** In multiplayer a logic frame may only run once both players' input is in. */
    @Override
    protected boolean isLogicFrameReady() {
        return session == null || session.beforeStep(rtsLogic);
    }

    @Override
    protected GameLogic createGameLogic() {
        return rtsLogic;
    }

    @Override
    protected GameClient createGameClient() {
        return rtsClient;
    }
}
