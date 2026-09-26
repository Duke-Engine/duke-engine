package uz.dukeengine.client3d;

import com.jme3.math.Vector3f;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.view.UnitView;
import uz.dukeengine.game.view.WorldSnapshot;
import uz.dukeengine.rts.event.WeaponFired;

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
 *
 * <p><b>What an RTS speaks at.</b> Counted over the objects of the one this was measured in: a line when
 * selected on 680, when ordered to move on 409 and to attack on 403, a sound on becoming damaged and really
 * damaged on 420, on starting to move on 288, a loop while it stands there on 308. So, named after the
 * template like the rest:
 * <ul>
 *   <li>{@code fired.<weapon>} — after the weapon, so a unit with two sounds two ways; {@code fired} for a
 *       weapon with no name of its own.
 *   <li>{@code selected.<template>} and {@code ordered.<order>.<template>} — the local player's own clicks,
 *       for the first thing selected or ordered, once a click; see {@link #selected} and {@link #ordered}.
 *   <li>{@code moving.<template>} — the frame it starts to move, not every frame it is moving (that is
 *       {@code walking}).
 *   <li>{@code <word>.<template>} for each word of the template's {@code WhenHurt}, lower-cased — the frame
 *       its health first falls below that word's share: {@code WhenHurt = [DAMAGED = 0.7, REALLY_DAMAGED =
 *       0.35]} sounds {@code damaged.Tank}, then {@code really_damaged.Tank}. The thresholds are the ones its
 *       look already changes at, and the words are the game's.
 *   <li>{@code ambient.<template>} — a loop that follows it while it is there, and stops when it is gone or
 *       dead; {@code ambient.<template>.<word>} while a hurt word holds, the deepest one, so a damaged
 *       building can crackle — falling back, like every name, to the plain loop where the game wrote none.
 * </ul>
 * A cue may keep itself for the thing's owner, and may cut off the last of itself — see {@link SoundBank.Cue}.
 */
final class GameSounds {

    /**
     * What a weapon with no name of its own sounded as before shots said which weapon fired: the dungeon's
     * bow. Heard only where a game names nothing under {@code fired}, so a game written before still sounds
     * as it did; once its file says {@code fired}, this is never reached and can go.
     */
    private static final String UNNAMED_SHOT_BEFORE = "arrow_fired";

    private final Sounds sounds;
    /** How close a thing has to vanish to something else to have hit it. */
    private final float touching;
    /** A template's hurt words and the share of health each holds below — its look's {@code WhenHurt}. */
    private final java.util.function.Function<String, Map<String, Float>> hurtWords;
    /** Whether the game named any loop at all — asked once, since the bank never changes. */
    private final boolean anyLoops;

    /** A loop that is going, and the cue it is — so a loop is only restarted when the cue it wants changes. */
    private record Loop(String cue, SoundSink.Playing playing) {
    }

    /** Which of each thing's hurt words held last frame, so a word sounds on the frame it comes to hold. */
    private final Map<Integer, java.util.Set<String>> hurt = new HashMap<>();
    private final Map<Integer, Loop> loops = new HashMap<>();

    /** A cue the simulation played riding a thing, and the thing — moved with it while it stands and it lasts. */
    private record Riding(int thing, SoundSink.Playing playing) {
    }

    private final java.util.List<Riding> riding = new java.util.ArrayList<>();

    /** A cue the simulation holds going, where, riding what (-1 none), until when — see {@link #sound}. */
    private static final class Held {
        final int thing;
        final SoundSink.Playing playing;
        int until;

        Held(int thing, SoundSink.Playing playing, int until) {
            this.thing = thing;
            this.playing = playing;
            this.until = until;
        }
    }

    /** The cues held going, by the cue and what it rides or where it is. */
    private final java.util.Map<String, Held> held = new java.util.LinkedHashMap<>();

    private Map<Integer, UnitView> before = Map.of();
    private String lastDepth = "";
    private String lastNote = "";
    private final Map<Character, Boolean> wasCooling = new HashMap<>();

    GameSounds(Sounds sounds, float touching) {
        this(sounds, touching, template -> Map.of());
    }

    GameSounds(Sounds sounds, float touching, java.util.function.Function<String, Map<String, Float>> hurtWords) {
        this.sounds = sounds;
        this.touching = touching;
        this.hurtWords = hurtWords;
        this.anyLoops = sounds.names("ambient");
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
    /**
     * What a death is called, for its sound and its look alike: {@code died.<template>.<death type>}, the type
     * lower-cased — {@code died.Soldier.exploded} — which finds {@code died.Soldier} where the game named nothing
     * for that death, and {@code died} where it named nothing for that template.
     */
    static String diedMoment(ObjectDied died) {
        return "died." + died.templateName() + "." + died.deathType().name().toLowerCase(java.util.Locale.ROOT);
    }

    void frame(WorldSnapshot snapshot, int localPlayer, float now) {
        var after = index(snapshot.units());
        for (var event : snapshot.events()) {
            if (event instanceof ObjectDied died) {
                sounds.play(diedMoment(died), at(died.position()), now,
                        died.playerIndex() == localPlayer);
                if (died.playerIndex() != localPlayer) {
                    sounds.play("vo.kill", now);
                }
                stopLoop(died.object().value());
            } else if (event instanceof WeaponFired fired) {
                var shooter = after.get(fired.shooter().value());
                fired(fired.weapon(), at(fired.from()), now,
                        shooter != null && shooter.playerIndex() == localPlayer);
            }
        }
        for (var view : snapshot.units()) {
            boolean owned = view.playerIndex() == localPlayer;
            var was = before.get(view.id());
            if (was == null) {
                sounds.play("spawned." + view.templateName(), at(view), now, owned);
            } else if (view.health() < was.health()) {
                sounds.play("hurt." + view.templateName(), at(view), now, owned);
            }
            if (view.moving() && was != null && !was.moving()) {
                sounds.play("moving." + view.templateName(), at(view), now, owned);
            }
            if (view.moving()) {
                // Every frame he is walking, which is what walking looks like from
                // out here. The stride is the cue's own business — see GapSeconds.
                sounds.play("walking." + view.templateName(), at(view), now, owned);
            }
            var deepest = hurtWordsNow(view, was != null, now, owned);
            if (anyLoops) {
                keepLooping(view, deepest, owned);
            }
        }
        for (var was : before.values()) {
            if (after.containsKey(was.id())) {
                continue;
            }
            sounds.play(ending(was) + was.templateName(), at(was), now, was.playerIndex() == localPlayer);
            stopLoop(was.id());
            hurt.remove(was.id());
        }
        riding.removeIf(one -> {
            var view = after.get(one.thing());
            if (view == null || one.playing().ended()) {
                return true; // its thing gone, or the sound played out: it stops where it was
            }
            one.playing().moveTo(at(view));
            return false;
        });
        held.values().removeIf(one -> {
            var view = one.thing < 0 ? null : after.get(one.thing);
            if (snapshot.frame() > one.until || one.thing >= 0 && view == null) {
                one.playing.stop(); // not asked again within its hold, or its thing gone
                return true;
            }
            if (view != null) {
                one.playing.moveTo(at(view));
            }
            return false;
        });
        before = after;
    }

    /**
     * A cue the simulation played by name that is nothing else of the game's — the reference plays a saboteur's
     * feedback and a battle plan's announcement straight from its logic, by the sound's own name: heard at its place
     * as the cue's own rules say, who hears it and how far, as a list's sound entry there is; riding a thing ({@code
     * thing} its id, -1 for none), following it while it stands and the sound lasts. Whether anything played.
     */
    boolean played(String cue, Vector3f at, int thing, boolean owned, float now) {
        if (thing < 0) {
            return sounds.play(cue, at, now, owned);
        }
        var playing = sounds.held(cue, at, now, owned);
        if (playing != null && playing != SoundSink.Playing.NONE) {
            riding.add(new Riding(thing, playing));
        }
        return playing != null;
    }

    /**
     * A cue the simulation played ({@link uz.dukeengine.core.event.SoundPlayed}): once, where it holds none; else kept
     * going, following the thing it rides, until a frame comes {@code hold} frames after it was last asked for — asked
     * again before then, the one sound plays on rather than another starting over it.
     */
    void sound(uz.dukeengine.core.event.SoundPlayed sound, Vector3f at, boolean owned, float now) {
        int thing = sound.riding() == null ? -1 : sound.riding().value();
        if (sound.hold() <= 0) {
            played(sound.cue(), at, thing, owned, now);
            return;
        }
        var key = sound.cue() + (thing >= 0 ? "@" + thing
                : "@" + Math.round(sound.where().x()) + "," + Math.round(sound.where().y()));
        var going = held.get(key);
        if (going != null) {
            going.until = sound.frame() + sound.hold();
            return;
        }
        var playing = sounds.loop(sound.cue(), at, owned);
        if (playing != null && playing != SoundSink.Playing.NONE) {
            held.put(key, new Held(thing, playing, sound.frame() + sound.hold()));
        }
    }

    /**
     * A shot: {@code fired.<weapon>}, or {@code fired} for a weapon with no name — and, for that one only, the
     * word shots used to have where the game names nothing under {@code fired} at all.
     */
    private void fired(String weapon, Vector3f at, float now, boolean owned) {
        if (weapon != null) {
            sounds.play("fired." + weapon, at, now, owned);
        } else if (sounds.resolved("fired") != null) {
            sounds.play("fired", at, now, owned);
        } else {
            sounds.play(UNNAMED_SHOT_BEFORE, at, now, owned);
        }
    }

    /**
     * Sound each of its hurt words on the frame it comes to hold, and say which is the deepest holding now —
     * the one with the least share of health. A thing seen for the first time sounds none: it did not fall
     * below anything in front of anybody, and a building going up from a tenth of its health is not a
     * building being shot to pieces.
     */
    private String hurtWordsNow(UnitView view, boolean seenBefore, float now, boolean owned) {
        var words = hurtWords.apply(view.templateName());
        if (words == null || words.isEmpty()) {
            return null;
        }
        float share = view.healthFraction();
        var held = hurt.getOrDefault(view.id(), java.util.Set.of());
        var holding = new java.util.TreeSet<String>();
        String deepest = null;
        float lowest = Float.MAX_VALUE;
        for (var word : words.entrySet()) {
            if (share >= word.getValue()) {
                continue;
            }
            holding.add(word.getKey());
            if (seenBefore && !held.contains(word.getKey())) {
                sounds.play(word.getKey().toLowerCase(java.util.Locale.ROOT) + "." + view.templateName(),
                        at(view), now, owned);
            }
            if (word.getValue() < lowest) {
                lowest = word.getValue();
                deepest = word.getKey();
            }
        }
        hurt.put(view.id(), holding);
        return deepest;
    }

    /** Its loop, following it: started, swapped for another where the cue it wants has changed, or moved. */
    private void keepLooping(UnitView view, String deepest, boolean owned) {
        var name = "ambient." + view.templateName()
                + (deepest == null ? "" : "." + deepest.toLowerCase(java.util.Locale.ROOT));
        var wanted = sounds.resolved(name);
        var going = loops.get(view.id());
        if (going != null && java.util.Objects.equals(going.cue(), wanted)) {
            going.playing().moveTo(at(view));
            return;
        }
        stopLoop(view.id());
        if (wanted == null) {
            return;
        }
        var playing = sounds.loop(wanted, at(view), owned);
        if (playing != null) {
            loops.put(view.id(), new Loop(wanted, playing));
        }
    }

    private void stopLoop(int id) {
        var going = loops.remove(id);
        if (going != null) {
            going.playing().stop();
        }
    }

    /**
     * The local player selected something, and this is the first of it: {@code selected.<template>}, once a
     * click. Whether he hears it is the cue's — a voice kept for its owner says nothing for an enemy picked
     * out to be looked at.
     */
    void selected(UnitView first, int localPlayer, float now) {
        if (first != null) {
            sounds.play("selected." + first.templateName(), at(first), now, first.playerIndex() == localPlayer);
        }
    }

    /**
     * The local player gave an order, and this is the first thing given it: {@code ordered.<order>.<template>},
     * once an order — {@code move}, {@code attack}, or the word of the button that gave it.
     */
    void ordered(String order, UnitView first, int localPlayer, float now) {
        if (first != null && order != null) {
            sounds.play("ordered." + order + "." + first.templateName(), at(first), now,
                    first.playerIndex() == localPlayer);
        }
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

    /** A moment the player caused directly: an order, a click, a point spent. */
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
        for (var going : loops.values()) {
            going.playing().stop();
        }
        loops.clear();
        hurt.clear();
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
