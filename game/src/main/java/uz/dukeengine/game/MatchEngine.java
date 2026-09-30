package uz.dukeengine.game;

import uz.dukeengine.core.GameClient;
import uz.dukeengine.core.GameEngine;
import uz.dukeengine.core.GameLogic;

/**
 * The concrete engine build behind {@link DukeGame} — the role SAGE's
 * device-specific {@code CreateGameEngine()} plays. It simply hands the
 * pre-constructed logic, whatever kind of game made it, and client to the engine
 * framework.
 */
final class MatchEngine extends GameEngine {

    private final GameLogic logic;
    private final GameClient client;
    private MultiplayerSession session;
    private uz.dukeengine.core.replay.Replay replay;

    MatchEngine(GameLogic logic, GameClient client) {
        this.logic = logic;
        this.client = client;
    }

    void setSession(MultiplayerSession session) {
        this.session = session;
    }

    void setReplay(uz.dukeengine.core.replay.Replay replay) {
        this.replay = replay;
    }

    /** What runs every turn of the loop, frames or not: the players' talk. */
    private Runnable everyTurn = () -> { };

    void everyTurn(Runnable task) {
        this.everyTurn = task;
    }

    @Override
    public void update() {
        everyTurn.run();
        super.update();
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
            return replay.beforeStep(logic);
        }
        return session == null || session.beforeStep(logic);
    }

    @Override
    protected GameLogic createGameLogic() {
        return logic;
    }

    @Override
    protected GameClient createGameClient() {
        return client;
    }
}
