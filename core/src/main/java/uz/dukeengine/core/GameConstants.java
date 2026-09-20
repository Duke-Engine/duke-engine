package uz.dukeengine.core;

/**
 * Engine-wide timing constants, ported faithfully from SAGE's
 * {@code GameCommon.h}.
 *
 * <p>The simulation ("logic") advances in discrete frames. One logic frame
 * represents a fixed slice of game time ({@link #SECONDS_PER_LOGICFRAME}),
 * which is what makes the simulation deterministic and replayable: the same
 * sequence of commands applied at the same frame numbers always yields the
 * same world state, regardless of the machine's render speed. Lock-step
 * networking synchronises on these frame numbers.
 */
public final class GameConstants {

    private GameConstants() {
    }

    public static final int MSEC_PER_SECOND = 1000;

    /** The fixed simulation rate. Game time is measured in 1/30s ticks. */
    public static final int LOGICFRAMES_PER_SECOND = 30;

    public static final float MSEC_PER_LOGICFRAME =
            (float) MSEC_PER_SECOND / LOGICFRAMES_PER_SECOND;

    public static final float SECONDS_PER_LOGICFRAME =
            1.0f / LOGICFRAMES_PER_SECOND;

    /** Default cap on how many engine loops/render frames run per second. */
    public static final int DEFAULT_MAX_FPS = 45;
}
