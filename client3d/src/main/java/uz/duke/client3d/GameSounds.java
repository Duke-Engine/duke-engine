package uz.duke.client3d;

import com.jme3.math.Vector3f;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uz.duke.core.event.ObjectDied;
import uz.duke.core.math.Coord3D;
import uz.duke.game.view.UnitView;
import uz.duke.game.view.WorldSnapshot;
import uz.duke.rts.event.WeaponFired;

/**
 * Turns a frame of the world into moments, by name.
 *
 * <p>The engine announces two things — something fired, something died — and
 * everything else worth hearing has to be noticed rather than received: an arrow
 * that is no longer in the list landed, a creature that was not there a moment ago
 * has arrived, health that went down was a blow. All of that is a comparison
 * between this frame and the last, and all of it belongs here rather than in the
 * simulation, which would have to carry a channel per noise for a client that may
 * not even be listening.
 *
 * <p>Every moment is named after the template it happened to — {@code died.Boss},
 * {@code struck.Arrow}, {@code walking.Hero} — and the client knows none of those
 * words. It raises the moment; whether it makes a sound is the game's answer, in
 * its own file. That is what lets this serve a dungeon and three other games
 * without a line about arrows in it.
 */
final class GameSounds {

    private final Sounds sounds;
    /** How close a thing has to vanish to something else to have hit it. */
    private final float touching;

    private Map<Integer, UnitView> before = Map.of();
    private String lastDepth = "";
    private String lastNote = "";
    private boolean offerWasUp;
    private final Map<Character, Boolean> wasCooling = new HashMap<>();

    GameSounds(Sounds sounds, float touching) {
        this.sounds = sounds;
        this.touching = touching;
    }

    Sounds sounds() {
        return sounds;
    }

    /**
     * Everything this frame is worth hearing.
     *
     * <p>Reads the snapshot and writes nothing back, which is the whole of its
     * relationship with the simulation. Two players watching the same replay may
     * hear different footsteps and still see the same world.
     */
    void frame(WorldSnapshot snapshot, int localPlayer, float now) {
        for (var event : snapshot.events()) {
            if (event instanceof ObjectDied died) {
                sounds.play("died." + died.templateName(), at(died.position()), now);
                if (died.playerIndex() != localPlayer) {
                    sounds.play("vo.kill", now);
                }
            } else if (event instanceof WeaponFired fired) {
                sounds.play("arrow_fired", at(fired.from()), now);
            }
        }
        var after = index(snapshot.units());
        for (var view : snapshot.units()) {
            var was = before.get(view.id());
            if (was == null) {
                sounds.play("spawned." + view.templateName(), at(view), now);
            } else if (view.health() < was.health()) {
                sounds.play("hurt." + view.templateName(), at(view), now);
            }
            if (view.moving()) {
                // Every frame he is walking, which is what walking looks like from
                // out here. The stride is the cue's own business — see GapSeconds.
                sounds.play("walking." + view.templateName(), at(view), now);
            }
        }
        for (var was : before.values()) {
            if (after.containsKey(was.id())) {
                continue;
            }
            sounds.play(ending(was) + was.templateName(), at(was), now);
        }
        before = after;
    }

    /**
     * Whether this left because it hit something, or merely left.
     *
     * <p>An arrow in this game chases what it was loosed at, so it nearly always
     * lands — but not always: its mark can be killed by an earlier arrow while it
     * is still in the air, and then it stops somewhere in the middle of the room.
     * A thump there is a thump at nothing, which is worse than silence.
     *
     * <p>So: on top of somebody else's, at the moment it went. Measured against
     * the frame before rather than this one, because a killing blow takes the
     * victim out of the world in the same breath — and the shot that finishes
     * something is exactly the one worth hearing land.
     */
    private String ending(UnitView leaving) {
        for (var other : before.values()) {
            if (other.id() == leaving.id() || other.playerIndex() == leaving.playerIndex()) {
                continue;
            }
            float dx = other.x() - leaving.x();
            float dy = other.y() - leaving.y();
            if (dx * dx + dy * dy <= touching * touching) {
                return "struck.";
            }
        }
        return "gone.";
    }

    /**
     * What the game's own line says has changed since it last said anything.
     *
     * <p>The status line is how this game speaks about itself, and it is where the
     * moments live that the engine has no name for: a floor, something
     * picked up, a skill that has just gone on its cooldown. Read rather than
     * announced, for the same reason as the rest — it is already being sent.
     */
    void status(HeroPanel.Reading reading, float now) {
        if (reading == null) {
            return;
        }
        if (!reading.depth().equals(lastDepth)) {
            if (!lastDepth.isEmpty()) {
                sounds.play("depth", now);
                sounds.play("vo.depth", now);
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
                sounds.play("loot", now);
            }
            lastNote = note;
        }
        boolean offerUp = reading.offer() != null;
        if (offerUp && !offerWasUp) {
            sounds.play("power_offer", now);
        }
        offerWasUp = offerUp;
        for (var slot : reading.skills()) {
            boolean cooling = slot.state() == HeroPanel.Reading.State.COOLING;
            // Going onto a cooldown is the only outward sign that a skill went
            // off: the client asked, and the simulation is what decides.
            if (cooling && !wasCooling.getOrDefault(slot.key(), false)) {
                sounds.play("skill." + slot.key(), now);
            }
            wasCooling.put(slot.key(), cooling);
        }
    }

    /** A moment the player caused directly: an order, a click, a card taken. */
    void moment(String cue, float now) {
        sounds.play(cue, now);
    }

    /**
     * He gained a level: the fanfare, and his voice saying so.
     *
     * <p>Told, rather than read off the panel. The panel's rank is whoever is picked
     * out, so a level gained with a skeleton selected went unheard -- and was heard
     * late, the moment he was picked again.
     */
    void levelledUp(float now) {
        sounds.play("level_up", now);
        sounds.play("vo.level_up", now);
    }

    /** A new world; nothing carried over from the one before it. */
    void forget() {
        before = Map.of();
        lastNote = "";
        wasCooling.clear();
    }

    private static Map<Integer, UnitView> index(List<UnitView> units) {
        var byId = new HashMap<Integer, UnitView>(units.size() * 2);
        for (var unit : units) {
            byId.put(unit.id(), unit);
        }
        return byId;
    }

    private static Vector3f at(UnitView view) {
        return new Vector3f(view.x(), 0f, view.y());
    }

    private static Vector3f at(Coord3D where) {
        return new Vector3f(where.x(), 0f, where.y());
    }
}
