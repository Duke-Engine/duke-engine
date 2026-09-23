package uz.dukeengine.core.thing;

/**
 * A template that says what the thing looks like.
 *
 * <p>One more of the interfaces a template may have — beside {@link Solid}, {@link Sighted},
 * {@link Classified} and {@link Titled} — and the one that was missing. Two games written
 * independently on this engine, a dungeon crawl and an RTS skirmish, each had to invent their own
 * template record for one reason: the engine had nowhere to put a model. {@code RtsTemplate} carries
 * a name, a size, a price and its modules and not one field about how the thing is drawn, so every
 * game wrote the same twenty lines handing its own record's fields to the client. This is that
 * conversation, declared once.
 *
 * <p><b>Everything but the model has a default</b>, so a record implements this by having whatever
 * components it has and no more: a block with no {@code Tint} is drawn untinted, one with no
 * {@code Walk} clip stands still while it moves. A record satisfies each of these with an ordinary
 * component of the same name — nothing is written by hand.
 *
 * <p>The four moments are the four that both games needed. A game with more of them — the dungeon
 * has {@code Hurt} — keeps them as components of its own record and binds them itself; the engine
 * reads what every game has.
 */
public interface Drawn extends ThingTemplate {

    /** Its model, as a whole path from the resource root: {@code models/units/soldier.glb}. */
    String model();

    /**
     * Models for particular conditions, keyed by the words that must <em>all</em> hold — one word, or
     * several with a space between them:
     *
     * <pre>
     * Models = [DAMAGED = models/barracks_d.glb, "DAMAGED SNOW" = models/barracks_ds.glb]
     * </pre>
     *
     * <p>{@link #model()} is what is drawn when none of them fits, so a template that names none of these
     * is drawn exactly as it was before there were any.
     *
     * <p><b>A condition is only a word.</b> The engine defines none of them and knows what none of them
     * mean: a dungeon may say {@code TORCHLIT}, a farming game {@code HARVESTED}, and the matching is the
     * same either way. Two words come from two places — the world says some of them once when it is loaded
     * (weather, the hour, the season), and the thing's own state says the rest, which is what
     * {@link #whenHurt()} is for.
     *
     * <p><b>And it is drawing only.</b> Which model is chosen never reaches the simulation. It is decided
     * downstream of the snapshot, in the client, so that a machine drawing a building in snow and one
     * drawing it in summer are still playing the same game.
     */
    default java.util.Map<String, String> models() {
        return java.util.Map.of();
    }

    /**
     * Below what share of its health each condition holds: {@code WhenHurt = [DAMAGED = 0.5]} means the
     * word {@code DAMAGED} holds once it is under half health.
     *
     * <p>The conditions a thing decides for itself, as against the ones its world does. A client reads
     * health out of the snapshot already, so this costs it nothing it was not doing.
     */
    default java.util.Map<String, Float> whenHurt() {
        return java.util.Map.of();
    }

    /** How much bigger than the file it is drawn. */
    default float modelScale() {
        return 1f;
    }

    /** A colour its material is washed with, {@code 0xFFFFFF} for none. */
    default int tint() {
        return 0xFFFFFF;
    }

    /** Which way the model faces in its own file, in degrees, so the game can turn it to face the world's. */
    default float facing() {
        return 0f;
    }

    /**
     * The name of an animation set it moves by, for the clips it does not name itself, or null.
     *
     * <p>A record marks its own component {@code @Link(AnimationSet.class)} so the editor completes it and opens
     * it; the interface only says the value is a name.
     */
    default String animations() {
        return null;
    }

    default String idle() {
        return null;
    }

    default String walk() {
        return null;
    }

    default String attack() {
        return null;
    }

    default String death() {
        return null;
    }

    /**
     * The name of an {@code Effect} block it wears — a glow, an aura, eyes in the dark — or null.
     *
     * <p>Here because the kit ships effects every game may draw from, and until a template could name one there
     * was no way for a game to wear one without inventing a record of its own. A record marks its own component
     * {@code @Link(Effect.class)} so the editor completes it from the blocks there are.
     */
    default String effect() {
        return null;
    }

    /** Whether it is worth asking a client to draw a model at all: a template with none gets its {@code Geometry}. */
    default boolean hasModel() {
        return model() != null && !model().isBlank();
    }
}
