package uz.dukeengine.client3d;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import uz.dukeengine.game.DukeGame;

/**
 * The match behind a game's front end — the reference's shell map. Made fresh from the game's recipe each time the
 * front end is shown, so a match seeded the same plays the same way every time; watched, so nobody's input reaches it
 * and nobody's fog hides it; built and read as any match is, while the front end is drawn over nothing; and put away
 * — stopped, its last frame waited for — when a real match starts.
 */
final class Backdrop {

    private static final Logger LOG = Logger.getLogger(Backdrop.class.getName());

    private final Supplier<DukeGame> recipe;
    private final Function<DukeGame, MatchLoad.Art> reader;
    private DukeGame game;
    private MatchLoad load;
    private Thread thread;
    private boolean failed;

    /**
     * @param recipe makes the backdrop's match, not yet started, each time one is wanted
     * @param reader reads the art of a built match
     */
    Backdrop(Supplier<DukeGame> recipe, Function<DukeGame, MatchLoad.Art> reader) {
        this.recipe = recipe;
        this.reader = reader;
    }

    /**
     * One frame of the front end: the backdrop made, read and started as far as it has got. The match running behind
     * the front end, or null while there is none yet — or none to be had, which is said once.
     */
    DukeGame frame() {
        if (thread != null || failed) {
            return running();
        }
        if (load == null) {
            game = recipe.get();
            if (game == null) {
                failed = true;
                LOG.warning("the backdrop recipe made no match; the front end is drawn over nothing");
                return null;
            }
            game.observe();
            load = new MatchLoad(game, reader, percent -> {
            }, false);
        }
        load.frame();
        if (load.failure() != null) {
            failed = true;
            LOG.log(Level.SEVERE, "the backdrop could not be built", load.failure());
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
}
