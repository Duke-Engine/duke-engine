package uz.dukeengine.game;

import uz.dukeengine.core.GameClient;
import uz.dukeengine.core.GameEngine;
import uz.dukeengine.core.GameLogic;

/**
 * The concrete engine build behind {@link DukeGame} — the role SAGE's
 * device-specific {@code CreateGameEngine()} plays. It simply hands the
 * pre-constructed RTS logic and client to the engine framework.
 */
final class RtsGameEngine extends GameEngine {

    private final RtsLogic rtsLogic;
    private final RtsClient rtsClient;
    private MultiplayerSession session;
    private uz.dukeengine.core.replay.Replay replay;

    RtsGameEngine(RtsLogic rtsLogic, RtsClient rtsClient) {
        this.rtsLogic = rtsLogic;
        this.rtsClient = rtsClient;
    }

    void setSession(MultiplayerSession session) {
        this.session = session;
    }

    void setReplay(uz.dukeengine.core.replay.Replay replay) {
        this.replay = replay;
    }

    /**
     * Where a frame's input comes from, and whether there is any yet.
     *
     * <p>Three cases, one question. On its own the game is always ready. In
     * multiplayer it waits until every player has reported. Replaying, it is ready
     * by definition — the input was decided long ago.
     */
    @Override
    protected boolean isLogicFrameReady() {
        if (replay != null) {
            return replay.beforeStep(rtsLogic);
        }
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
