package uz.duke.dungeon.power;

import java.util.List;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.dungeon.level.Levelling;

/**
 * The offer standing between a level and the hero getting stronger.
 *
 * <p>Levelling already hands out health, damage and armour on its own. This is
 * the part the player decides: three cards, one of which he keeps for the rest of
 * the run. It is what turns a level from a number going up into a choice about
 * what kind of hero this run is going to be.
 *
 * <p>Session state rather than a module, beside {@link uz.duke.dungeon.level.HeroProgress}
 * and for the same reasons: a floor replaces the hero object and a chosen power
 * has to survive that, while a death is meant to take everything. Both are told
 * which is happening rather than left to infer it.
 *
 * <p>Offers are owed rather than dropped. Two levels earned in one frame — a boss
 * dying to a heavy shot will do it — owe two cards, and the second appears the
 * moment the first is answered. Nothing about a level is silently lost.
 *
 * <p>Deterministic throughout: the cards come from the run's seed by way of
 * {@link PowerDraft}, and being offered one costs nothing until the player's
 * command arrives on a frame boundary like any other.
 */
public final class PowerChoice {

    private final PowerBook book;
    private final List<Power> catalogue;
    private final long seed;
    private final int offerCount;

    private int lastLevel = Levelling.FIRST_LEVEL;
    /** Levels reached whose card has not been chosen yet. */
    private int owed;
    private List<Power> offered = List.of();
    /**
     * Which offer this is, counted up through the session — and what the client
     * answers by.
     *
     * <p>Not the level it was earned at, and deliberately not reset with the run:
     * a death puts the hero back at level one, so two runs would each have a
     * "level 2" offer, and a client that remembered answering the first would
     * silently refuse to show the second.
     */
    private int offerId;
    /** The level this offer was earned at, which is what the card says. */
    private int offerLevel;

    public PowerChoice(PowerBook book, List<Power> catalogue, long seed, int offerCount) {
        this.book = book;
        this.catalogue = List.copyOf(catalogue);
        this.seed = seed;
        this.offerCount = offerCount;
    }

    public PowerBook getBook() {
        return book;
    }

    /**
     * Called every logic frame with the hero's level, after progression has had
     * its say.
     *
     * <p>Watching the level rather than being told about it keeps this off
     * {@link uz.duke.dungeon.level.HeroProgress}, which has enough to do; and the
     * reset that a death performs already puts the level back to the first, so a
     * new run cannot inherit an offer.
     */
    public void tick(int level) {
        if (level > lastLevel) {
            owed += level - lastLevel;
        }
        lastLevel = level;
        if (offered.isEmpty() && owed > 0) {
            offered = PowerDraft.offer(seed, level, catalogue, book, offerCount);
            if (offered.isEmpty()) {
                owed = 0; // nothing left to give; stop asking
            } else {
                offerLevel = level;
                offerId++;
            }
        }
    }

    public boolean hasOffer() {
        return !offered.isEmpty();
    }

    /** The cards on the table, in the order they are drawn on screen. */
    public List<Power> getOffer() {
        return offered;
    }

    /** Which offer this is — the number a choice names it by. */
    public int getOfferId() {
        return offerId;
    }

    /** Which level these cards were earned at, which is what the card says. */
    public int getOfferLevel() {
        return offerLevel;
    }

    /**
     * Take the card at {@code index}. Returns whether anything happened.
     *
     * <p>An index outside the offer is refused rather than clamped: a command for
     * a card that is not there is a command about some other offer, and honouring
     * it would spend this one on something the player never saw.
     */
    public boolean choose(int index, int forOffer, GameObject hero) {
        if (offered.isEmpty() || index < 0 || index >= offered.size() || forOffer != offerId) {
            return false;
        }
        var chosen = offered.get(index);
        offered = List.of();
        owed = Math.max(0, owed - 1);
        book.add(chosen);
        applyTo(hero);
        return true;
    }

    /** Everything back to nothing: a run has ended. */
    public void reset() {
        book.clear();
        offered = List.of();
        owed = 0;
        offerLevel = 0;
        lastLevel = Levelling.FIRST_LEVEL;
        // offerId is not put back: it is what tells one run's offers from the
        // next's, and two offers with the same name is exactly the bug it exists
        // to prevent.
    }

    /**
     * A new body on a deeper floor, who is the same hero: put back whatever his
     * powers changed about him.
     *
     * <p>Only the ones that live on the object need this. Damage, cooldowns and
     * charges are read out of {@link PowerBook} as they are used, so they follow
     * him without being re-applied to anything.
     */
    public void carryOver(GameObject hero) {
        applyTo(hero);
    }

    /**
     * Make the hero's own body match what he has been given.
     *
     * <p>Speed is the only one, and it is done by building a fresh locomotor from
     * the speed his template was authored with. The engine's {@code MoveUpdate}
     * fixes its step when it is built — rightly, for an RTS where a rifleman is a
     * rifleman — so the game replaces it through the seam the engine offers,
     * exactly as {@link uz.duke.dungeon.level.GrowableBody} replaces the body.
     * Reading the base speed off the template rather than remembering it keeps
     * {@code creatures.ini} the one place his pace is written.
     *
     * <p>He stops walking when it is swapped, because a route belongs to the
     * locomotor that planned it. That is invisible in practice: the only two
     * moments this happens are the frame he chooses a card — with the world held
     * still behind the offer — and the frame he arrives on a new floor.
     */
    private void applyTo(GameObject hero) {
        if (hero == null) {
            return;
        }
        float multiplier = book.moveSpeedMultiplier();
        var move = hero.findModule(MoveUpdate.class);
        var authored = authoredMovement(hero.getTemplate());
        if (move == null || authored == null || multiplier == 1f) {
            return;
        }
        hero.replaceModule(move, new MoveUpdate(hero, new MoveUpdate.Data(
                authored.speedPerSecond() * multiplier, authored.turnRateDegreesPerSecond())));
    }

    /** The speed the creature file gave him, before any of this. */
    private static MoveUpdate.Data authoredMovement(ThingTemplate template) {
        for (var entry : template.getModules()) {
            if (entry.data() instanceof MoveUpdate.Data data) {
                return data;
            }
        }
        return null;
    }
}
