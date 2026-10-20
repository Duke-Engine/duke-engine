package uz.dukeengine.client3d.rpg;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The hero card's moments heard: a new floor, something picked up, a skill gone onto its cooldown — read off the card
 * as it changes, since the card is already being sent. His level is not among them: the client hears that off the
 * bars' line, whoever is picked out.
 */
final class CardSounds {

    private final Consumer<String> heard;
    private String lastDepth = "";
    private String lastNote = "";
    private final Map<Character, Boolean> wasCooling = new HashMap<>();

    /** @param heard plays a cue of the game's sound bank */
    CardSounds(Consumer<String> heard) {
        this.heard = heard;
    }

    /**
     * What the game's own line says has changed since it last said anything.
     *
     * <p>The status line is how this game speaks about itself, and it is where the
     * moments live that the engine has no name for: a floor, something
     * picked up, a skill that has just gone on its cooldown. Read rather than
     * announced, for the same reason as the rest — it is already being sent.
     */
    void status(PanelReading reading) {
        if (reading == null) {
            return;
        }
        if (!reading.depth().equals(lastDepth)) {
            if (!lastDepth.isEmpty()) {
                heard.accept("depth");
                heard.accept("vo.depth");
            }
            lastDepth = reading.depth();
        }
        // A card with no level on it is not his — the player has picked out
        // something else and the bar is describing that. The floor above is still
        // the floor; nothing below this line is. His level is not read here at all:
        // see levelledUp.
        if (reading.rank().isEmpty()) {
            return;
        }
        // A note stays up for a while, so it is its arrival that is the moment.
        var note = reading.note() == null ? "" : reading.note();
        if (!note.equals(lastNote)) {
            if (!note.isEmpty()) {
                heard.accept("loot");
            }
            lastNote = note;
        }
        for (var slot : reading.skills()) {
            boolean cooling = slot.state() == PanelReading.State.COOLING;
            // Going onto a cooldown is the only outward sign that a skill went
            // off: the client asked, and the simulation is what decides.
            if (cooling && !wasCooling.getOrDefault(slot.key(), false)) {
                heard.accept("skill." + slot.key());
            }
            wasCooling.put(slot.key(), cooling);
        }
    }

    /** A new world; nothing carried over from the one before it. */
    void forget() {
        lastNote = "";
        wasCooling.clear();
    }
}
