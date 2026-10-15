package uz.dukeengine.core;

import java.util.logging.Logger;

/**
 * The engine itself, ported from SAGE's {@code GameEngine}.
 *
 * <p>It is a subsystem that owns the other subsystems. Concrete builds (a
 * game, a tool, a test harness) subclass it and supply a {@link GameLogic} and
 * {@link GameClient} via the factory methods; the device-specific subclass is
 * what {@code CreateGameEngine()} returns in the original engine.
 *
 * <p>{@link #execute()} is the main loop. Each iteration runs one frame and
 * then throttles to {@link #getMaxFps()}. Within a frame the client always
 * updates, but the logic only steps when the simulation is allowed to advance —
 * i.e. it is not paused and the next frame's data is ready
 * ({@link #isLogicFrameReady()}, always true in single-player). This split is
 * what lets a slow machine keep the UI responsive while the deterministic
 * simulation stays in lock-step with its peers.
 */
public abstract class GameEngine extends SubsystemInterface {

    private static final Logger LOG = Logger.getLogger(GameEngine.class.getName());

    /** One logic frame's worth of wall time, in nanoseconds (33.33ms at 30Hz). */
    private static final long STEP_NANOS = 1_000_000_000L / GameConstants.LOGICFRAMES_PER_SECOND;

    /** Cap on banked time, so a long stall can't trigger a catch-up "spiral of death". */
    private static final long MAX_ACCUMULATED_NANOS = 250_000_000L;

    private final SubsystemList subsystems = new SubsystemList();

    private int maxFps = GameConstants.DEFAULT_MAX_FPS;
    private boolean quitting;
    private boolean active = true;

    private long accumulatedNanos;
    private long lastFrameMark;
    /** How many logic seconds pass in a wall second — see {@link #setLogicRate}; and a step asked while paused. */
    private volatile float logicRate = 1f;
    private volatile boolean stepAsked;

    /**
     * Run the simulation {@code multiplier} times as fast as the wall clock — 0.25 to 8, 0 a pause — each step still a
     * thirtieth of a second of the world, so its checksums and the order of its orders are what they were. In a match
     * of several machines it is the game's to agree on, as an order; the engine applies what it is told.
     */
    public final void setLogicRate(float multiplier) {
        this.logicRate = multiplier <= 0f ? 0f : Math.clamp(multiplier, 0.25f, 8f);
    }

    public final float getLogicRate() {
        return logicRate;
    }

    /** One frame of the world now, while it is paused or held at a rate of 0: stepping through to find a change. */
    public final void stepOnce() {
        stepAsked = true;
    }

    private GameLogic logic;
    private GameClient client;

    protected final SubsystemList subsystems() {
        return subsystems;
    }

    public final GameLogic getLogic() {
        return logic;
    }

    public final GameClient getClient() {
        return client;
    }

    @Override
    public void init() {
        logic = createGameLogic();
        client = createGameClient();

        // The client renders the logic, so it is registered (and later
        // shut down in reverse) after the logic it depends on.
        subsystems.initSubsystem(logic, "TheGameLogic");
        subsystems.initSubsystem(client, "TheGameClient");

        subsystems.postProcessLoadAll();
        subsystems.resetAll();
    }

    @Override
    public void reset() {
        subsystems.resetAll();
    }

    /**
     * Compute one engine frame. The logic is stepped at a fixed 30Hz, draining
     * the time banked since the last frame (zero, one, or several steps); the
     * client then renders exactly once. This is the decoupling SAGE's own
     * {@code update()} flagged as a {@code @todo} but never implemented: the
     * client is free to run fast while the simulation stays on its fixed clock,
     * so game time tracks wall time regardless of render rate.
     */
    @Override
    public void update() {
        if (stepAsked) {
            stepAsked = false;
            if ((logic.isGamePaused() || logicRate == 0f) && isLogicFrameReady()) {
                logic.update();
            }
        }
        while (accumulatedNanos >= STEP_NANOS) {
            if (logic.isGamePaused()) {
                accumulatedNanos -= STEP_NANOS; // sim frozen, but let real time pass
                continue;
            }
            if (!isLogicFrameReady()) {
                break; // can't advance without peer data — keep the time, stall
            }
            logic.update();
            accumulatedNanos -= STEP_NANOS;
        }
        client.update();
    }

    /**
     * Whether the next logic frame may be simulated. Single-player is always
     * ready; the network subsystem overrides this to gate on peer frame data.
     */
    protected boolean isLogicFrameReady() {
        return true;
    }

    /** The main loop. Returns only once {@link #setQuitting(boolean)} is set. */
    public void execute() {
        long prevTime = System.nanoTime();
        accumulatedNanos = 0;
        lastFrameMark = prevTime;
        while (!quitting) {
            long now = System.nanoTime();
            float rate = logicRate;
            accumulatedNanos = Math.min(accumulatedNanos + (long) ((now - prevTime) * (double) rate),
                    (long) (MAX_ACCUMULATED_NANOS * Math.max(1f, rate)));
            prevTime = now;
            try {
                update();
            } catch (RuntimeException e) {
                LOG.severe(() -> "Uncaught exception in GameEngine.update: " + e);
                throw e;
            }
            throttleRender();
        }
        subsystems.shutdownAll();
    }

    /** Busy-wait so the render loop runs no faster than {@link #getMaxFps()}. */
    private void throttleRender() {
        if (maxFps <= 0) {
            lastFrameMark = System.nanoTime();
            return;
        }
        long frameNanos = 1_000_000_000L / maxFps;
        long now = System.nanoTime();
        while ((now - lastFrameMark) < frameNanos) {
            Thread.onSpinWait();
            now = System.nanoTime();
        }
        lastFrameMark = now;
    }

    public final int getMaxFps() {
        return maxFps;
    }

    public final void setMaxFps(int maxFps) {
        this.maxFps = maxFps;
    }

    public final boolean isQuitting() {
        return quitting;
    }

    public final void setQuitting(boolean quitting) {
        this.quitting = quitting;
    }

    public final boolean isActive() {
        return active;
    }

    public final void setActive(boolean active) {
        this.active = active;
    }

    protected abstract GameLogic createGameLogic();

    protected abstract GameClient createGameClient();
}
