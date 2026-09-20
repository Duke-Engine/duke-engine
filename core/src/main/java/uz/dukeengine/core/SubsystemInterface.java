package uz.dukeengine.core;

/**
 * Base class for every engine subsystem singleton, ported from SAGE's
 * {@code SubsystemInterface}.
 *
 * <p>The contract is deliberately strict so all subsystems behave
 * consistently:
 * <ul>
 *   <li>The constructor only brings the object to a valid (non-garbage)
 *       state. Default <em>values</em> belong in {@link #init()}.</li>
 *   <li>{@link #init()} assigns defaults and acquires resources held for
 *       the subsystem's lifetime.</li>
 *   <li>{@link #postProcessLoad()} runs after <em>all</em> subsystems are
 *       inited, so inter-subsystem dependencies can be wired up.</li>
 *   <li>{@link #reset()} returns the subsystem to an empty state ready for
 *       a new game, reusing resources rather than freeing and reallocating
 *       them where possible.</li>
 *   <li>{@link #update()} performs one frame of work.</li>
 * </ul>
 */
public abstract class SubsystemInterface {

    private String name = "";

    /** Assign defaults and acquire lifetime resources. */
    public abstract void init();

    /** Wire up cross-subsystem dependencies, after every subsystem is inited. */
    public void postProcessLoad() {
    }

    /** Return to an empty state ready to accept a new game's data. */
    public abstract void reset();

    /** Service the subsystem for one frame. */
    public abstract void update();

    /**
     * Per-frame drawing. Only client-side subsystems override this; logic
     * subsystems intentionally have nothing to draw.
     */
    public void draw() {
    }

    /** Release resources. Default is a no-op; override when needed. */
    public void shutdown() {
    }

    public final String getName() {
        return name;
    }

    public final void setName(String name) {
        this.name = name;
    }
}
