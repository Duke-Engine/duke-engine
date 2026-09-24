package uz.dukeengine.client3d;

import java.util.function.Function;
import java.util.function.IntConsumer;
import uz.dukeengine.game.DukeGame;

/**
 * A match being made ready: built on a thread of its own while the window goes on drawing — the game's load screen
 * with it — its art read and shown to the card, the game told how far it has got, and handed back to be started
 * only once it is ready and, where the game holds the start, once the game lets it go.
 *
 * <p>How far is one number, 0 to 100: building the match is the first {@value #BUILT}, the engine's own steps as
 * {@link DukeGame#boot(IntConsumer)} says them; reading its art is the rest; 100 is ready and nothing less is. The
 * game hears each new figure once, rising, on the window's thread — and the window draws the game's canvas after
 * every frame, so after every figure. In a network game each figure from the build on is said to the other machines
 * too, and theirs are taken in.
 */
final class MatchLoad {

    /** What reads a match's art, once the match is built. */
    interface Art {
        /** This frame's share of the work; true once there is none left. */
        boolean step();

        /** How far along, 0 to 1. */
        float done();
    }

    /** The part of the bar that building the match takes. */
    static final int BUILT = 40;

    private final DukeGame match;
    private final Function<DukeGame, Art> reader;
    private final IntConsumer told;
    private final Thread builder;
    private volatile int built;
    private volatile RuntimeException failed;
    private Art art;
    private boolean read;
    private int reported = -1;
    private boolean held;

    /**
     * @param reader what reads the art of a built match
     * @param told   the game's ear for how far it has got
     * @param hold   whether the game keeps the start until it lets go — see {@link #release}
     */
    MatchLoad(DukeGame match, Function<DukeGame, Art> reader, IntConsumer told, boolean hold) {
        this.match = match;
        this.reader = reader;
        this.told = told;
        this.held = hold;
        this.builder = new Thread(() -> {
            try {
                match.boot(percent -> built = percent);
            } catch (RuntimeException | Error e) {
                // Kept rather than let loose on a thread nobody watches: a match half built must not be read.
                failed = e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
            }
        }, "duke-load");
        builder.setDaemon(true); // a window closed mid-load must still close
        builder.start();
    }

    /** One frame of loading, on the window's thread. */
    void frame() {
        if (failed != null) {
            return;
        }
        int percent;
        // A finished thread is one whose every write this thread sees: the world it built is safe to read from here.
        if (builder.isAlive()) {
            percent = Math.min(BUILT - 1, built * BUILT / 100);
        } else {
            if (art == null) {
                art = reader.apply(match);
            }
            if (!read) {
                read = art.step();
            }
            percent = read ? 100 : BUILT + Math.min(99 - BUILT, (int) (art.done() * (100 - BUILT)));
            match.loadProgress(Math.max(percent, reported));
        }
        if (percent > reported) {
            reported = percent;
            told.accept(percent);
        }
    }

    /** Whether it is loaded and nothing holds it: time to start it. */
    boolean ready() {
        return reported == 100 && !held && failed == null;
    }

    /** The game lets the start go: at 100, or at once if it is there already. */
    void release() {
        held = false;
    }

    /** How far it has got, as last told. */
    int percent() {
        return Math.max(0, reported);
    }

    /** What went wrong building it, or null. */
    RuntimeException failure() {
        return failed;
    }
}
