package uz.dukeengine.client3d;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import uz.dukeengine.combat.event.ShotLanded;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * What happened in the world this frame, as opposed to what merely is: deaths, shots, blows, casts, effects and sounds
 * the simulation played — drawn and heard where they happened — and the numbers, flashes and moments a blow throws off.
 *
 * <p>A unit leaving the snapshot used to be all the client had to go on, so it guessed: gone while badly hurt meant
 * dead, gone while healthy meant fog. That was wrong at both ends. The simulation now says outright what died and what
 * fired, already filtered through fog of war.
 */
final class WorldEvents {

    private final Visuals visuals;
    private final UnitScene unitScene;
    private final Looks looks;
    private final LayeredEffects layered;
    private final EffectLists lists;
    private final SkillEffects skillEffects;
    private final Controls controls;
    private final List<HudPart> parts;
    private final GameSounds noises;
    private final SoundNodes soundNodes;
    private final WorldGround ground;
    private final FogOfWar fog;
    /** The numbers that come off a creature as it is hurt or healed. */
    private final FloatingNumbers hitNumbers;
    /** Texts floated up from points of the world — see {@link FloatingTexts}. */
    private final FloatingTexts floatingTexts;
    private final PictureStrips pictureStrips;
    /** What a blow shows, by name — see {@link HurtMoments} and {@link Visuals#hurt}. */
    private final HurtMoments hurtMoments;
    private final Camera cam;
    private final com.jme3.system.Timer timer;
    private final Supplier<DukeGame> game;
    private final Supplier<WorldSnapshot> snapshot;
    /** The floor's half of this frame's status line, for the run's moments. */
    private final Supplier<UnitBarReading> barReading;
    private final WorldMoments.Floor floor;
    /**
     * Shots that ended this frame, waiting for its blows to say whether they struck.
     */
    private final List<Landing.Gone> endedShots = new ArrayList<>();
    /** A level, the boss down, a new floor -- see RunMoments. */
    private final RunMoments runMoments = new RunMoments();
    /**
     * The frame of the last cast this client drew, so one cast is drawn once.
     */
    private int lastCastFrameDrawn = Integer.MIN_VALUE;
    private WorldSnapshot lastEventedSnapshot = WorldSnapshot.EMPTY;
    private final HealthWatch healthWatch = new HealthWatch();

    /**
     * Who was killed this frame, gathered as the events are read.
     *
     * <p>The one thing the snapshot cannot say. A creature that dies is taken out
     * of the world on the frame the blow lands, so the finishing blow never shows
     * up as health going down -- the creature is simply not there any more, which
     * from the outside looks exactly like one that walked into the dark.
     */
    private final List<HealthWatch.Death> killedThisFrame = new ArrayList<>();
    /** The creatures a critical blow struck this frame, their numbers drawn as critical. */
    private final java.util.Set<Integer> criticalThisFrame = new java.util.HashSet<>();
    private final HitStops hitStops = new HitStops();

    WorldEvents(Visuals visuals, UnitScene unitScene, Looks looks, LayeredEffects layered, EffectLists lists,
            SkillEffects skillEffects, Controls controls, List<HudPart> parts, GameSounds noises, SoundNodes soundNodes,
            WorldGround ground, FogOfWar fog, FloatingNumbers hitNumbers, FloatingTexts floatingTexts,
            PictureStrips pictureStrips, HurtMoments hurtMoments, Camera cam, com.jme3.system.Timer timer,
            Supplier<DukeGame> game, Supplier<WorldSnapshot> snapshot, Supplier<UnitBarReading> barReading,
            WorldMoments.Floor floor) {
        this.visuals = visuals;
        this.unitScene = unitScene;
        this.looks = looks;
        this.layered = layered;
        this.lists = lists;
        this.skillEffects = skillEffects;
        this.controls = controls;
        this.parts = parts;
        this.noises = noises;
        this.soundNodes = soundNodes;
        this.ground = ground;
        this.fog = fog;
        this.hitNumbers = hitNumbers;
        this.floatingTexts = floatingTexts;
        this.pictureStrips = pictureStrips;
        this.hurtMoments = hurtMoments;
        this.cam = cam;
        this.timer = timer;
        this.game = game;
        this.snapshot = snapshot;
        this.barReading = barReading;
        this.floor = floor;
    }

    /** Whether a critical blow struck {@code id} this frame. */
    boolean criticalThisFrame(int id) {
        return criticalThisFrame.contains(id);
    }

    /** A shot that ended this frame, to burst where it struck once the frame's blows are read. */
    void shotEnded(Landing.Gone shot) {
        endedShots.add(shot);
    }

    /** A match over: nothing of it is replayed. */
    void forgetTheWorld() {
        lastEventedSnapshot = WorldSnapshot.EMPTY;
    }

    /** A new world: whoever is in it has just arrived, and nobody here was just hit. */
    void newWorld() {
        runMoments.forget();
        hitNumbers.clear();
        hitStops.clear();
        floatingTexts.clear();
        pictureStrips.clear();
        healthWatch.forget(); // new creatures, new ids; nobody here was just hit
    }

    /**
     * Throw a number off anything whose health has moved, and keep the ones
     * already in the air moving.
     *
     * <p>Off the snapshot rather than off an event -- see {@link HealthWatch} for
     * why that is the complete answer rather than the lazy one. And the texts and strips the simulation floated up.
     *
     * @param playing whether a match is played, not a menu shown over it
     */
    void drawTheBlows(float now, boolean playing) {
        var snapshot = this.snapshot.get();
        var game = this.game.get();
        var look = visuals.getHitNumbers();
        List<HealthWatch.Change> changes = List.of();
        if (playing) {
            changes = healthWatch.since(snapshot.units(), game.getLocalPlayerIndex(),
                    look.leastWorth(), killedThisFrame);
            if (game.alertsAreTheGames()) {
                controls.gamesAlert(game.latestAlert());
            }
            for (var change : changes) {
                hitNumbers.add(change, now, look.height(), criticalThisFrame.contains(change.unitId()));
                if (!change.healed() && !change.his()) {
                    unitScene.struck(change.unitId());
                }
                if (!change.healed() && change.his()) {
                    controls.alert(change.x(), change.y()); // his, under attack: the place Space goes to
                }
            }
            playTheRunsMoments(now);
        }
        burstWhatStruck(changes);
        // Read once. read runs earlier in the frame and fills this; a
        // second reading would throw the finishing blow twice.
        killedThisFrame.clear();
        criticalThisFrame.clear();
        hitNumbers.update(now, cam, floor::at);
        hitStops.apply(now, (id, speed) -> {
            var node = unitScene.node(id);
            if (node != null && node.composer != null) {
                node.composer.setGlobalSpeed(speed);
            }
        });
        // Where the player's view of the point is clear, and nowhere else — the reference's fog test for its texts.
        floatingTexts.update(snapshot.frame(), cam, fog::canSee);
        pictureStrips.update(snapshot.frame(), cam, fog::canSee);
    }

    /**
     * A level gained, the boss down, a new floor: each played on him in the look the
     * game gave it, and a level heard as well.
     *
     * <p>Off the line's own field for the hero rather than off the panel's card,
     * which is whoever happens to be selected -- see {@link RunMoments}. One look on
     * him at a time, the biggest: the blow that fells a boss is usually the one that
     * gives him his level too.
     */
    private void playTheRunsMoments(float now) {
        var died = new ArrayList<Integer>(killedThisFrame.size());
        for (var death : killedThisFrame) {
            died.add(death.unitId());
        }
        var moments = runMoments.since(barReading.get(), died);
        for (var moment : moments) {
            if (Visuals.LEVEL_UP.equals(moment.name())) {
                noises.levelledUp(now);
            }
        }
        var drawn = RunMoments.biggestOnEach(moments, name -> {
            var look = visuals.getMoment(name);
            return look == null ? 0.0 : look.scale();
        });
        for (var moment : drawn) {
            var look = visuals.getMoment(moment.name());
            var node = unitScene.node(moment.unitId());
            if (look == null || node == null || layered == null) {
                continue;
            }
            var at = node.root.getLocalTranslation();
            var foot = new Vector3f(at.x, floor.at(at.x, at.z), at.z);
            layered.cast(look.effect(), new LayeredEffects.Moment(foot, null, null, 0f, 1f, 0f,
                    moment.unitId(), moment.unitId()), cam, look.scale());
        }
    }

    /**
     * The bursts of the shots that ended this frame, for the ones that struck.
     *
     * <p>The simulation's blast hurts only when a shot reaches a body, and round that
     * body; a shot that meets a wall or runs out of flight hurts nobody, and bursts
     * nowhere. The blows are this frame's, deaths included -- a finishing blow on
     * something with less left than a number is worth is still a blow.
     */
    private void burstWhatStruck(List<HealthWatch.Change> changes) {
        if (endedShots.isEmpty()) {
            return;
        }
        var blows = new ArrayList<Landing.Blow>();
        for (var change : changes) {
            if (!change.healed()) {
                blows.add(new Landing.Blow(change.x(), change.y(), change.his()));
            }
        }
        for (var death : killedThisFrame) {
            blows.add(new Landing.Blow(death.x(), death.y(),
                    death.playerIndex() == game.get().getLocalPlayerIndex()));
        }
        for (var shot : endedShots) {
            var at = Landing.burstAt(shot, blows, visuals.getStrikeWithin());
            if (at != null && layered != null) {
                layered.landed(shot.id(), shot.effect(), at, cam);
            }
        }
        endedShots.clear();
    }


    /**
     * An effect the simulation played: the moment of that name, or else the game's effect list, effect or particle
     * system of that name — riding the thing it names where it names one it can draw — or else its sound cue of that
     * name, heard there and following the thing it rides. A name the game has none of draws nothing.
     */
    private void played(uz.dukeengine.core.event.EffectPlayed played) {
        var rider = played.riding() == null ? null : unitScene.node(played.riding().value());
        var riding = rider == null ? null : rider.root;
        var cue = WorldMoments.played(played, riding, floor::at);
        int on = rider == null ? LayeredEffects.NOBODY : played.riding().value();
        if (visuals.getMoment(played.name()) != null) {
            moment(played.name(), cue, on);
            return;
        }
        if (lists != null && lists.play(played.name(), cue)) {
            return;
        }
        if (visuals.effectNamed(played.name()) == null && visuals.particleSystemNamed(played.name()) == null) {
            if (noises != null && cue != null) {
                boolean owned = rider == null || rider.view == null
                        || rider.view.playerIndex() == game.get().getLocalPlayerIndex();
                noises.played(played.name(), cue.at(), rider == null ? -1 : played.riding().value(), owned,
                        timer.getTimeInSeconds());
            }
            return;
        }
        if (layered != null && !layered.rides(played.name(), riding)) {
            var moment = new LayeredEffects.Moment(cue.at(), null, null, 0f, 1f, 0f, on, on);
            if (played.toward() == null) {
                layered.cast(played.name(), moment, cam);
            } else {
                layered.cast(played.name(), moment, cam, played.toward().value());
            }
        }
    }


    /** A sound cue the simulation played, heard where it is and following the thing it rides. */
    void heard(uz.dukeengine.core.event.SoundPlayed sound) {
        if (noises == null) {
            return;
        }
        var rider = sound.riding() == null ? null : unitScene.node(sound.riding().value());
        boolean owned = rider == null || rider.view == null
                || rider.view.playerIndex() == game.get().getLocalPlayerIndex();
        var at = new Vector3f(sound.where().x(), sound.where().z(), sound.where().y());
        noises.sound(sound, at, owned, timer.getTimeInSeconds());
    }

    /**
     * A moment of the world's that the game gave a look to by name — {@code died.<template>.<type>},
     * {@code fired.<weapon>}, {@code landed.<weapon>} — played where {@link WorldMoments} says it happened: an
     * effect list, an effect, or a particle system of that name.
     */
    private void moment(String name, EffectLists.Cue cue, int on) {
        var look = visuals.getMoment(name);
        if (look == null || cue == null) {
            return;
        }
        if (lists != null && lists.play(look.effect(), cue)) {
            return;
        }
        if (layered != null) {
            layered.cast(look.effect(), new LayeredEffects.Moment(cue.at(), null, null, 0f, 1f, cue.radius(), on,
                    on), cam, look.scale());
        }
    }

    /** A thing gone into or out of a hold: {@code entered.<hold template>} or {@code left.<hold template>}, at the hold. */
    private void passage(String moment, int hold, uz.dukeengine.core.math.Coord3D where) {
        var node = unitScene.node(hold);
        var name = node == null || node.view == null ? moment : moment + "." + node.view.templateName();
        moment(name, WorldMoments.passage(where, node == null ? null : node.root, floor::at), hold);
    }

    /** This frame's events, read once: the world's moments drawn and heard where they happened. */
    void read() {
        var snapshot = this.snapshot.get();
        var game = this.game.get();
        if (snapshot == lastEventedSnapshot) {
            return; // the sim has not produced a new frame; do not replay this one
        }
        lastEventedSnapshot = snapshot;
        killedThisFrame.clear();
        skillsCastThisFrame(snapshot);
        for (var event : snapshot.events()) {
            if (event instanceof ObjectDied died) {
                killedThisFrame.add(new HealthWatch.Death(died.object().value(),
                        died.position().x(), died.position().y(), died.playerIndex()));
                // Told outright, and it has to be: a dead creature is gone from
                // the next snapshot, so by the time the frame could notice there
                // would be nothing left to play a death on.
                for (var part : parts) {
                    part.died(died.object().value());
                }
                var where = new Vector3f(died.position().x(), 0f, died.position().y());
                var dying = unitScene.node(died.object().value());
                var look = dying != null ? looks.visualFor(dying.view) : looks.visualFor(died.templateName()); // as it was drawn
                soundNodes.play(look.dieSound, where);
                // "Destroyed" is what this event means, so it is the arrow landing
                // as much as the monster falling. What it draws where it struck is
                // its layers' -- see Landing; what is left here is the knock, if the
                // thing that just stopped existing asked for one. Nothing does but a
                // meteor arriving, so the biggest thump in the game belongs to the
                // thing that ARRIVED rather than to the man who called for it a
                // second and a half ago and may well be dead.
                skillEffects.cast(look.effect,
                        new uz.dukeengine.core.math.Coord3D(died.position().x(),
                                died.position().y(), 0f),
                        cam.getLocation());
                controls.died(died.object().value());
                hurtMoments.forget(died.object().value());
                unitScene.layOut(died.object().value(), died.deathType(), died.frame());
                unitScene.died(died.object().value());
                moment(GameSounds.diedMoment(died),
                        WorldMoments.died(died, dying == null ? null : dying.root, floor::at),
                        died.object().value());
            } else if (event instanceof WeaponFired fired) {
                var node = unitScene.node(fired.shooter().value());
                // Its barrel flashes and kicks whether or not the shot was given a look of its own.
                var bone = node == null ? null : unitScene.fire(fired.shooter().value(), fired.slot());
                if (fired.weapon() != null) {
                    // By the shooter's rank first, a veteran's flash: fired.<weapon>.<rank>, falling back by its dots.
                    moment("fired." + fired.weapon() + (fired.rank() > 0 ? "." + fired.rank() : ""),
                            WorldMoments.fired(fired, bone, node == null ? null : node.root, floor::at),
                            fired.shooter().value());
                }
                if (node != null) {
                    soundNodes.play(looks.visualFor(node.view).fireSound,
                            node.root.getLocalTranslation());
                    // The swing, on the frame the weapon let go — unless it began with the wind-up this shot ends.
                    if (!node.swungFromWindUp) {
                        unitScene.playOnce(node, looks.visualFor(node.view).attackAnim);
                    }
                    node.swungFromWindUp = false;
                }
            } else if (event instanceof uz.dukeengine.combat.event.WeaponWoundUp wound) {
                var node = unitScene.node(wound.shooter().value());
                if (node != null) {
                    // The whole wind-up drawn: the swing begins now and its blow meets the shot.
                    unitScene.playOnce(node, looks.visualFor(node.view).attackAnim);
                    node.swungFromWindUp = true;
                }
            } else if (event instanceof uz.dukeengine.core.event.ObjectHurt hurt) {
                if (hurt.critical()) {
                    criticalThisFrame.add(hurt.object().value());
                }
                hitStops.heard(hurt, visuals.getHitStop(), visuals.getHitNumbers().bigAt(), timer.getTimeInSeconds());
                var name = hurtMoments.nameFor(hurt, visuals::namesMoment);
                if (name != null) {
                    var victim = unitScene.node(hurt.object().value());
                    var cue = WorldMoments.hurt(hurt, victim == null ? null : victim.root, floor::at);
                    moment(name, cue, hurt.object().value());
                    if (noises != null) {
                        noises.sounds().play(name, cue.at(), timer.getTimeInSeconds(),
                                hurt.playerIndex() == game.getLocalPlayerIndex());
                    }
                }
            } else if (event instanceof uz.dukeengine.core.event.SceneryChanged changed) {
                ground.showScenery(changed.index(), !changed.removed());
            } else if (event instanceof uz.dukeengine.core.event.TextFloated text) {
                floatingTexts.add(text, text.frame());
            } else if (event instanceof uz.dukeengine.core.event.StripPlayed strip) {
                pictureStrips.add(strip, strip.frame());
            } else if (event instanceof uz.dukeengine.core.event.ObjectEntered entered) {
                passage("entered", entered.hold().value(), entered.where());
            } else if (event instanceof uz.dukeengine.core.event.ObjectLeft left) {
                passage("left", left.hold().value(), left.where());
            } else if (event instanceof uz.dukeengine.core.event.SoundPlayed sound) {
                heard(sound);
            } else if (event instanceof uz.dukeengine.core.event.EffectPlayed played) {
                played(played);
            } else if (event instanceof ShotLanded landed && landed.weapon() != null) {
                var victim = landed.victim() == null ? null : unitScene.node(landed.victim().value());
                moment("landed." + landed.weapon(),
                        WorldMoments.landed(landed, victim == null ? null : victim.root, floor::at),
                        landed.victim() == null ? LayeredEffects.NOBODY : landed.victim().value());
            }
        }
    }

    /**
     * What was cast this frame, out of the status line.
     *
     * <p><b>Why the status line and not an event.</b> The engine's event channel
     * carries {@code WeaponFired}, and every cast already posts one -- but it says
     * only WHO fired and where, which is all a muzzle flash needs and not nearly
     * enough to tell a nova from a blink. The event lives in {@code rts}, which is
     * shared with games that have never heard of a skill, so widening it there to
     * carry a key would be the dungeon's idea put somewhere it does not belong.
     *
     * <p>The status line is the game talking to its own client and is exactly the
     * seam for this: a string the engine carries and never reads. It already
     * carries the things a dungeon has that an RTS does not -- the note, which
     * stone the floor is built from -- and this is one more.
     *
     * <p>Read from the snapshot rather than from the panel, because the panel is
     * about what is SELECTED: the moment the player clicks a skeleton his own
     * card is gone and so would his effects be. The frame number is what keeps
     * one cast drawn once, since the same line is sent again until something
     * changes.
     *
     * <p>Format is {@code cast=<recipe>,<frame>,<x>,<y>,<radius>,<whose>}. A line
     * that does not parse is dropped in silence: a missing ring is the cheapest
     * possible failure, and an effect that threw would take the frame with it.
     */
    private int skillsCastThisFrame(WorldSnapshot snapshot) {
        if (skillEffects == null || !snapshot.hasStatus()) {
            return 0;
        }
        var casts = SkillEffects.castsIn(snapshot.status(), lastCastFrameDrawn);
        for (var cast : casts) {
            lastCastFrameDrawn = Math.max(lastCastFrameDrawn, cast.frame());
            skillEffects.cast(cast.look(), cast.at(), cam.getLocation());
            // And the caster is seen doing it, if the game named a gesture for
            // this recipe. Nothing did until a mage needed both hands.
            unitScene.castGesture(unitScene.node(cast.by()), visuals.getCastAnim(cast.look()));
        }
        drawLayers(casts);
        return casts.size();
    }

    /**
     * The layered half of this frame's casts.
     *
     * <p>A run and a blink arrive as two marks of one look in one frame -- the spot
     * he left, which belongs to the floor, and the spot he arrived at, which is his
     * -- and a layer laid along the run or flashed at both ends needs them as ONE
     * moment with two ends. Everything else is one mark and one moment, facing the
     * way from whoever cast it to where it went off.
     */
    private void drawLayers(List<SkillEffects.Cast> casts) {
        if (layered == null) {
            return;
        }
        for (int i = 0; i < casts.size(); i++) {
            var cast = casts.get(i);
            var next = i + 1 < casts.size() ? casts.get(i + 1) : null;
            var spot = new Vector3f(cast.at().x(), 0f, cast.at().y());
            boolean run = next != null && next.look().equals(cast.look())
                    && next.frame() == cast.frame() && cast.on() == SkillEffects.NOBODY
                    && next.on() != SkillEffects.NOBODY;
            if (run) {
                var to = new Vector3f(next.at().x(), 0f, next.at().y());
                layered.cast(cast.look(), new LayeredEffects.Moment(to, spot, to,
                        to.x - spot.x, to.z - spot.z, next.radius(), next.on(), next.by()), cam);
                i++;
                continue;
            }
            float facingX = 0f;
            float facingZ = 0f;
            var caster = unitScene.node(cast.by());
            if (caster != null) {
                var from = caster.root.getLocalTranslation();
                facingX = spot.x - from.x;
                facingZ = spot.z - from.z;
            }
            layered.cast(cast.look(), new LayeredEffects.Moment(spot, null, null, facingX,
                    facingZ, cast.radius(), cast.on(), cast.by()), cam);
        }
    }
}
