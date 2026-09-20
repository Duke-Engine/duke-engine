package uz.dukeengine.core.client;

import uz.dukeengine.core.GameClient;
import uz.dukeengine.core.GameLogic;

/**
 * A {@link GameClient} that renders the simulation each frame through a
 * {@link Renderer}, from one player's point of view.
 *
 * <p>This is a concrete presentation backend: every loop it produces a frame of
 * the world (respecting fog of war) and keeps the latest for display or
 * inspection. It only reads {@link GameLogic} — it never mutates simulation
 * state — preserving the logic/presentation separation. Swap the {@link Renderer}
 * (ASCII, 3D, …) without touching the engine.
 */
public final class RenderingGameClient extends GameClient {

    private final GameLogic logic;
    private final Renderer renderer;
    private final int viewerPlayer;
    private String lastFrame = "";

    public RenderingGameClient(GameLogic logic, Renderer renderer, int viewerPlayer) {
        this.logic = logic;
        this.renderer = renderer;
        this.viewerPlayer = viewerPlayer;
    }

    /** The most recently rendered frame. */
    public String getLastFrame() {
        return lastFrame;
    }

    @Override
    protected void render() {
        lastFrame = renderer.render(logic, viewerPlayer);
    }
}
