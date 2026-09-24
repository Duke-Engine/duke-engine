package uz.dukeengine.client3d;

import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import uz.dukeengine.game.DukeGame;

/**
 * The match behind a game's front end — the reference's shell map. Made fresh from the game's recipe each time the
 * front end is shown, so a match seeded the same plays the same way every time; watched, so nobody's input reaches it
 * and nobody's fog hides it; built and read as any match is, while the front end is drawn over nothing and the game is
 * told how far it has got; and put away — stopped, its last frame waited for — when a real match starts.
 *
 * <p>The game may hold it back. The reference plays its logo and its trailer first and loads its shell map only after
 * them, under a load screen with a bar; while held, nothing of the backdrop is made or read, so a movie has the
 * machine to itself.
 */
final class Backdrop {

    private static final Logger LOG = Logger.getLogger(Backdrop.class.getName());

    private final Supplier<DukeGame> recipe;
    private final Function<DukeGame, MatchLoad.Art> reader;
    private final IntConsumer told;
    private DukeGame game;
    private MatchLoad load;
    private Thread thread;
    private boolean failed;
    private boolean held;
    private int reported = -1;

    /**
     * @param recipe makes the backdrop's match, not yet started, each time one is wanted
     * @param reader reads the art of a built match
     * @param told   the game's ear for how far the one being made has got, 0 to 100
     */
    Backdrop(Supplier<DukeGame> recipe, Function<DukeGame, MatchLoad.Art> reader, IntConsumer told) {
        this.recipe = recipe;
        this.reader = reader;
        this.told = told;
    }

    /**
     * Whether the game keeps it from being made — see {@link Duke3D#holdBackdrop}. A backdrop already begun goes on;
     * the hold keeps the next from being begun.
     */
    void hold(boolean held) {
        this.held = held;
    }

    /**
     * One frame of the front end: the backdrop made, read and started as far as it has got. The match running behind
     * the front end, or null while there is none yet — or none to be had, which is said once.
     */
    DukeGame frame() {
        if (thread != null) {
            // 100 once a frame of it has run, and not when it is merely started: the world the game fades its menu
            // in over is there.
            if (reported < 100 && game.getSnapshot().frame() > 0) {
                tell(100);
            }
            return game;
        }
        if (failed) {
            return null;
        }
        if (load == null) {
            if (held) {
                return null;
            }
            reported = -1;
            game = recipe.get();
            if (game == null) {
                giveUp("the backdrop recipe made no match; the front end is drawn over nothing", null);
                return null;
            }
            game.observe();
            load = new MatchLoad(game, reader, percent -> tell(Math.min(99, percent)), false);
        }
        load.frame();
        if (load.failure() != null) {
            giveUp("the backdrop could not be built", load.failure());
            load = null;
            return null;
        }
        if (!load.ready()) {
            return null;
        }
        load = null;
        thread = game.startEngineOnly();
        return game;
    }

    /** The match running behind the front end, or null. */
    DukeGame running() {
        return thread == null ? null : game;
    }

    /** Put it away: stopped, its last frame waited for, and forgotten — the next front end makes a fresh one. */
    void stop() {
        if (game != null) {
            game.stop();
        }
        if (thread != null) {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        game = null;
        thread = null;
        load = null;
    }

    /** None to be had, said once — and 100 told, so a load screen waiting for it goes on over nothing. */
    private void giveUp(String why, Throwable cause) {
        failed = true;
        LOG.log(cause == null ? Level.WARNING : Level.SEVERE, why, cause);
        tell(100);
    }

    /** Each new figure once, rising. */
    private void tell(int percent) {
        if (percent > reported) {
            reported = percent;
            told.accept(percent);
        }
    }
}
