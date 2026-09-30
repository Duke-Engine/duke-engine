package uz.dukeengine.core;

import java.util.ServiceLoader;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.network.PacketCodec;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.core.view.ViewParts;

/**
 * A kind of game as the runtime that runs it sees it — an RTS, an RPG: the world it runs, the records its blocks are
 * read into, the wire its commands travel on, and its parts of the picture. The runtime is the same for every kind; each
 * kind is a flavour of it, and brings an API of its own beside it.
 *
 * <p>A library offers its flavour as a service ({@code META-INF/services/uz.dukeengine.core.Flavour}), so a game with
 * one kind on its classpath runs on it without naming it: {@link #found}.
 */
public interface Flavour {

    /** A new world of this kind as a match is set up: its modules, its players, and the rules it applies orders by. */
    GameLogic newWorld();

    /** The loader given the records this kind reads its blocks into. */
    void templates(ThingTemplateLoader loader);

    /** The wire its commands travel on, to every machine and into a replay. */
    PacketCodec codec();

    /** Whether its wire carries {@code command}: what may be sent to every machine. */
    boolean carries(Command command);

    /**
     * Told once its world is made, its templates read and its players in, before the match's own setup: on the thread
     * that sets the match up, what the kind itself sets up then.
     */
    default void began(GameLogic world) {
    }

    /** What it adds to the picture of its world. */
    default ViewParts views() {
        return ViewParts.NONE;
    }

    /**
     * The one flavour on the classpath, made anew: the kind of game this is. An error where there is none, or more than
     * one to choose from, which a game settles by naming its own.
     */
    static Flavour found() {
        var kinds = ServiceLoader.load(Flavour.class, Flavour.class.getClassLoader()).stream().toList();
        if (kinds.size() != 1) {
            throw new IllegalStateException(kinds.isEmpty()
                    ? "no kind of game on the classpath: depend on one, such as the rts library, or name a Flavour"
                    : "more than one kind of game on the classpath, " + kinds.stream().map(kind -> kind.type()
                            .getName()).toList() + ": name the one this game is");
        }
        return kinds.getFirst().get();
    }
}
