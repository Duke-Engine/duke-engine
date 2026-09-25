package uz.dukeengine.client3d;

import java.util.HashMap;
import uz.dukeengine.core.content.AnimationSet;
import uz.dukeengine.core.thing.Drawn;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Unity-style asset binding: attach models, animations and sounds to unit
 * templates by name — no engine code, just configuration.
 *
 * <pre>{@code
 * var visuals = Visuals.create()
 *         .unit("Tank", u -> u
 *                 .model("Models/tank.gltf").scale(1.5f).facing(90)
 *                 .idle("Idle").walk("Drive").attack("Fire")
 *                 .fireSound("Sounds/cannon.ogg").dieSound("Sounds/boom.ogg"))
 *         .unit("Rifleman", u -> u.model("Models/soldier.gltf").walk("Walk"));
 * }</pre>
 *
 * <p>Every setting is optional. A unit with no model gets a clean primitive
 * (box for structures, capsule for units) in its player's colour, so a game is
 * playable before a single asset exists — add art when you have it.
 */
public final class Visuals {

    /**
     * One file animations are taken from.
     *
     * <p>{@code clipName} is what to call the file's single animation, or
     * {@code null} to take every clip in it under its own name.
     */
    record AnimationSource(String assetPath, String clipName) {
    }

    /** Per-template visual/audio configuration. All fields optional. */
    public static final class UnitVisual {
        String modelPath;
        String modelPart;
        String texturePath;
        /**
         * One thing hung on one of the unit's own bones.
         *
         * <p>A list rather than a field apiece, because a hand is not the only
         * place a character carries something and a character is not limited to
         * one hand. A knight is a sword AND a shield; an archer is a bow and the
         * arrows to go in it. Written as one field each, the second of every pair
         * was simply impossible.
         *
         * <p>Mutable and package-private on purpose: it is filled in by the
         * fluent calls below, where {@code holds} starts a new one and everything
         * after it describes the one just started.
         */
        static final class Carried {
            String path;
            String bone;
            float scale = 1f;
            float pitch;
            float yaw;
            float roll;
            float x;
            float y;
            float z;
        }

        final java.util.List<Carried> carried = new java.util.ArrayList<>();
        /** The name of the flight effect this unit wears, or null for a plain one. */
        String effect;
        /** How far forward of its middle the effect sits; see effectAt. */
        float effectForward;
        /** Where this unit's animations come from, in the order they were named. */
        final java.util.List<AnimationSource> animations = new java.util.ArrayList<>();
        float scale = 1f;
        float yOffset;
        float facingDegrees; // extra yaw if the model's authored "forward" isn't +X
        String idleAnim;
        String walkAnim;
        String attackAnim;
        String hurtAnim;
        String dieAnim;
        /** Its weapon slots' named points, by slot; see {@link #fireBone}. */
        final java.util.Map<Integer, WeaponBones> weaponBones = new java.util.TreeMap<>();
        /** Weapon slots' bones for sets of conditions, by the words that must all hold; see {@link #weaponBonesFor}. */
        final java.util.Map<String, java.util.Map<Integer, WeaponBones>> conditionalWeaponBones =
                new java.util.LinkedHashMap<>();
        /** Its pieces' states, in the order the game gave them; see {@link #pieces}. */
        final java.util.List<PieceState> pieceStates = new java.util.ArrayList<>();
        /** How its barrels kick back; see {@link #recoil}. */
        Barrels.Recoil recoil = Barrels.Recoil.REFERENCE;
        /** Its clips chosen by the words it holds, in the order the game gave them; see {@link #clip}. */
        final java.util.List<ClipState> clipStates = new java.util.ArrayList<>();
        /** Its other models, by name, in the order the game named them; see {@link #layer}. */
        final java.util.Map<String, UnitVisual> layers = new java.util.LinkedHashMap<>();
        /** Whether it is drawn rising out of the ground as it is built; see {@link #risesAsBuilt}. */
        boolean risesAsBuilt;
        /** How far it rises: the height the game named, or -1 for its model's own top. */
        float riseHeight = -1f;
        /** Its treads, or null for none; see {@link #treads}. */
        Treads treads;
        /** Its wheels, or null for none; see {@link #wheels}. */
        Wheels wheels;
        /** Its clips for particular deaths, by the death type's name; see {@link #die(String, String)}. */
        final java.util.Map<String, String> dieAnims = new java.util.LinkedHashMap<>();
        /** Clips it needs for something other than standing, walking and dying. */
        final java.util.List<String> otherAnims = new java.util.ArrayList<>();
        String fireSound;
        String dieSound;
        java.awt.Color colour; // null = the owning player's colour
        java.awt.Color tint;   // multiplied over the model's own texture

        /** Models for conditions, by the words that must all hold; see {@link #modelFor}. */
        final java.util.Map<String, String> conditionalModels = new java.util.LinkedHashMap<>();
        /** Below what share of its health each condition holds — the ones a thing decides for itself. */
        final java.util.Map<String, Float> whenHurt = new java.util.LinkedHashMap<>();

        /**
         * Pieces of its model hidden and shown while its words best fit {@code conditions} — the reference's {@code
         * HideSubObject} and {@code ShowSubObject} per condition state, chosen by the rule the model is chosen by. A
         * piece is a node of the model's tree, named bare ({@code TURRETUP01}) or with its container ({@code
         * AVHUMMER.TURRETUP01}), ignoring case. Sticky, as the reference's are: a state changes only what it names,
         * and a piece no state names keeps the file's own visibility. A barrel's muzzle flash stays its barrel's.
         */
        public UnitVisual pieces(java.util.Set<String> conditions, java.util.List<String> hide,
                java.util.List<String> show) {
            chooseAgain();
            pieceStates.add(new PieceState(new java.util.TreeSet<>(conditions), hide, show));
            return this;
        }

        /**
         * Where weapon slot {@code slot}'s shots come out while its words best fit {@code conditions}: an upgraded
         * Humvee firing from {@code MuzzleUp} — see {@link #fireBone(int, String)}. The best-fitting set's bones are
         * used, and the plain ones where none fits.
         */
        public UnitVisual fireBone(java.util.Set<String> conditions, int slot, String bone) {
            chooseAgain();
            var bones = bonesFor(conditions);
            var was = bones.getOrDefault(slot, new WeaponBones(null, null, null));
            bones.put(slot, new WeaponBones(bone, was.flash(), was.recoil()));
            return this;
        }

        /** Slot {@code slot}'s muzzle flash piece while its words best fit {@code conditions}. */
        public UnitVisual muzzleFlash(java.util.Set<String> conditions, int slot, String piece) {
            chooseAgain();
            var bones = bonesFor(conditions);
            var was = bones.getOrDefault(slot, new WeaponBones(null, null, null));
            bones.put(slot, new WeaponBones(was.fire(), piece, was.recoil()));
            return this;
        }

        /** Slot {@code slot}'s recoil bone while its words best fit {@code conditions}. */
        public UnitVisual recoilBone(java.util.Set<String> conditions, int slot, String bone) {
            chooseAgain();
            var bones = bonesFor(conditions);
            var was = bones.getOrDefault(slot, new WeaponBones(null, null, null));
            bones.put(slot, new WeaponBones(was.fire(), was.flash(), bone));
            return this;
        }

        private java.util.Map<Integer, WeaponBones> bonesFor(java.util.Set<String> conditions) {
            return conditionalWeaponBones.computeIfAbsent(String.join(" ", new java.util.TreeSet<>(conditions)),
                    key -> new java.util.TreeMap<>());
        }

        /**
         * Another model drawn with it, named {@code name}: the reference's second draw modules — a war factory's door,
         * its crane, its scaffold. The layer is a look of its own with the same means as this one — {@link #model} and
         * models by words, {@code model(words, null)} drawing nothing while those words fit; {@link #pieces}; {@link
         * #clip} and its idle; {@link #risesAsBuilt}; its size and facing — drawn at the thing's place and facing,
         * painted in its owner's colour, and chosen by the words the thing holds, each layer on its own. Asked again by the
         * same name, the same layer. What is picked, ringed and barred is still the thing's own look.
         */
        public UnitVisual layer(String name) {
            return layers.computeIfAbsent(name, named -> new UnitVisual());
        }

        /**
         * A clip played on the way from one of its looks to another — see {@link #transition}.
         *
         * @param keepGroup where the look it leaves names the same keep group, its clip starts at the share of its
         *                  length the look it leaves had played — the reference's {@code
         *                  MAINTAIN_FRAME_ACROSS_STATES}: a crane finishes the swing it is in; null starts it at its
         *                  first frame, or its last
         * @param particles particle systems at bones of its model, run while it plays: {@link #transitionParticles}
         */
        record Transition(java.util.SortedSet<String> from, java.util.SortedSet<String> to, String model, String clip,
                ClipMode mode, float speed, String keepGroup, java.util.List<BoneParticles> particles) {
        }

        final java.util.List<Transition> transitions = new java.util.ArrayList<>();

        /**
         * A clip played once on the way from the look {@code fromWords} choose to the one {@code toWords} choose — the
         * reference's {@code TransitionState}: a construction fence rising out of the ground as a site appears, and
         * folding back at twice the speed when it is finished; a crane finishing its swing before it rests. While it
         * plays the layer wears {@code model}, played {@code ONCE} forward or {@code ONCE_BACKWARDS} from its end at
         * {@code speed} times its own pace, by the game's frames; then the new look's own. A layer that draws nothing in
         * the new look is drawn until its way out has played.
         */
        public UnitVisual transition(java.util.Set<String> fromWords, java.util.Set<String> toWords, String model,
                String clip, ClipMode mode, float speed) {
            return transition(fromWords, toWords, model, clip, mode, speed, null);
        }

        /** The same, its clip started where the look it leaves stood where both name {@code keepGroup}. */
        public UnitVisual transition(java.util.Set<String> fromWords, java.util.Set<String> toWords, String model,
                String clip, ClipMode mode, float speed, String keepGroup) {
            transitions.add(new Transition(new java.util.TreeSet<>(fromWords), new java.util.TreeSet<>(toWords), model,
                    clip, mode, speed <= 0f ? 1f : speed, keepGroup, java.util.List.of()));
            return this;
        }

        /**
         * A particle system at a bone of the model of the transition last named, run while it plays — the reference's
         * {@code ParticleSysBone} on a transition state: a command centre's fence rising over burning pits.
         */
        public UnitVisual transitionParticles(String bone, String system) {
            if (transitions.isEmpty()) {
                return this;
            }
            var last = transitions.removeLast();
            var particles = new java.util.ArrayList<>(last.particles());
            particles.add(new BoneParticles(new java.util.TreeSet<>(), bone, system));
            transitions.add(new Transition(last.from(), last.to(), last.model(), last.clip(), last.mode(),
                    last.speed(), last.keepGroup(), java.util.List.copyOf(particles)));
            return this;
        }

        /** Looks one waits to leave for another — see {@link #waitFor}. */
        private record Wait(java.util.SortedSet<String> from, java.util.SortedSet<String> to) {
        }

        private final java.util.List<Wait> waits = new java.util.ArrayList<>();

        /**
         * Going from the look {@code fromWords} choose to the one {@code toWords} choose, the second is worn only once
         * the first's {@code ONCE} or {@code ONCE_BACKWARDS} clip has played to its end — the reference's {@code
         * WaitForStateToFinishIfPossible}: a Chinook's crates lifted before they are carried, and set down before they
         * are gone.
         */
        public UnitVisual waitFor(java.util.Set<String> fromWords, java.util.Set<String> toWords) {
            waits.add(new Wait(new java.util.TreeSet<>(fromWords), new java.util.TreeSet<>(toWords)));
            return this;
        }

        /** Whether going from look {@code from} to look {@code to} waits for the first's clip to end. */
        boolean waitsFor(java.util.SortedSet<String> from, java.util.SortedSet<String> to) {
            for (var one : waits) {
                if (lookFor(one.from()).equals(from) && lookFor(one.to()).equals(to)) {
                    return true;
                }
            }
            return false;
        }

        boolean waits() {
            return !waits.isEmpty();
        }

        /** Which of its looks a set of words chooses: the words of the model they choose, none for its plain one. */
        java.util.SortedSet<String> lookFor(java.util.Set<String> holding) {
            var words = new java.util.ArrayList<java.util.SortedSet<String>>();
            for (var key : conditionalModels.keySet()) {
                words.add(wordsOf(key));
            }
            int best = uz.dukeengine.core.thing.Conditions.bestFit(words, holding);
            return best < 0 ? new java.util.TreeSet<>() : words.get(best);
        }

        /** The clip played going from look {@code from} to look {@code to}, or null for none. */
        Transition transitionFor(java.util.SortedSet<String> from, java.util.SortedSet<String> to) {
            for (var one : transitions) {
                if (lookFor(one.from()).equals(from) && lookFor(one.to()).equals(to)) {
                    return one;
                }
            }
            return null;
        }

        /** A particle system at a bone of its model while its words choose a look — see {@link #particles}. */
        record BoneParticles(java.util.SortedSet<String> words, String bone, String system) {
        }

        final java.util.List<BoneParticles> boneParticles = new java.util.ArrayList<>();

        /**
         * A particle system from the game's list at a bone of its model — or of this layer's — while its words best fit
         * {@code conditions}: the reference's {@code ParticleSysBone} per condition state, a scaffold's sparks while it
         * is built, a factory's steam, a burning building's fires, each chosen with its look and gone when the look
         * changes. Systems given the same words are one look's and run together; of the looks the words best fit, that
         * one's run.
         */
        public UnitVisual particles(java.util.Set<String> conditions, String bone, String system) {
            boneParticles.add(new BoneParticles(new java.util.TreeSet<>(conditions), bone, system));
            return this;
        }

        /** The systems its words choose now: those of the best-fitting words its systems were given, or none. */
        java.util.List<BoneParticles> particlesFor(java.util.Set<String> holding) {
            if (boneParticles.isEmpty()) {
                return java.util.List.of();
            }
            var looks = new java.util.ArrayList<java.util.SortedSet<String>>();
            for (var one : boneParticles) {
                if (!looks.contains(one.words())) {
                    looks.add(one.words());
                }
            }
            int best = uz.dukeengine.core.thing.Conditions.bestFit(looks, holding);
            if (best < 0) {
                return java.util.List.of();
            }
            var chosen = looks.get(best);
            return boneParticles.stream().filter(one -> one.words().equals(chosen)).toList();
        }

        /** The bone of the thing's body, or of another of its layers, this layer is drawn at; null for its place. */
        String hungOn;

        /**
         * A layer drawn at a bone of the thing's body, or of another of its layers, following it as that model turns and
         * animates — the reference's {@code AttachToBoneInAnotherModule}: a pickup's gun on its turret bone, a car's roof
         * light. A bone its models do not have leaves it at the thing's place.
         */
        public UnitVisual hungOn(String bone) {
            this.hungOn = bone;
            return this;
        }

        /**
         * Drawn rising out of the ground as it is built — the reference's {@code ADJUST_HEIGHT_BY_CONSTRUCTION_PERCENT}:
         * sunk by what is left to build times its model's height, so at nothing built its top is at the ground, at
         * half it stands half out, and finished it stands where it is; and sinking again as it is sold. From the
         * simulation's own progress ({@code UnitView.built}), so the same on every machine.
         */
        public UnitVisual risesAsBuilt() {
            this.risesAsBuilt = true;
            return this;
        }

        /**
         * The same, sunk by what is left to build times {@code height} — the thing's own height, as the reference's
         * {@code W3DModelDraw::adjustTransformMtx} sinks by its geometry's height whatever its model: at nothing built a
         * model taller than the thing stands out of the ground by the difference (a factory's 48.1 over its 40).
         */
        public UnitVisual risesAsBuilt(float height) {
            this.risesAsBuilt = true;
            this.riseHeight = Math.max(0f, height);
            return this;
        }

        /**
         * A clip it plays while its words best fit {@code conditions}, in place of its idle and its walk: the
         * reference's condition state's {@code Animation} and {@code AnimationMode}. {@code ONCE} plays to the end and
         * stays there, {@code ONCE_BACKWARDS} back to the start, a loop either way, or {@code HOLD} on one frame,
         * starting at {@code start} — {@code null} for the first frame, or the last for one played backwards. Chosen
         * by the rule its model is chosen by, it starts again whenever the choice changes, unless the state before and
         * this one name the same {@code keepGroup} and this one says nothing of where to start: then it carries on
         * from as far through as the other was ({@code MAINTAIN_FRAME_ACROSS_STATES}). Stepped by the game's frames,
         * so every machine shows the same frame, and held while the game is paused. Words given no clip play its roles
         * as before. A door: {@code clip(Set.of("DOOR_1_OPENING"), "ABWarFact_A8", ONCE, FIRST, null)}, {@code
         * clip(Set.of("DOOR_1_WAITING_OPEN"), "ABWarFact_A8", HOLD, LAST, null)}, {@code
         * clip(Set.of("DOOR_1_CLOSING"), "ABWarFact_A8", ONCE_BACKWARDS, LAST, null)}.
         */
        public UnitVisual clip(java.util.Set<String> conditions, String clip, ClipMode mode, ClipStart start,
                String keepGroup) {
            return clip(conditions, clip, mode, start, keepGroup, 1f, 1f);
        }

        /**
         * The same, played at a speed between {@code slowest} and {@code fastest} times its own pace, drawn afresh each
         * time it starts — the reference's {@code AnimationSpeedFactorRange}: each soldier's idle at its own pace
         * between 0.9 and 1.1, a Chinook's crates lifted at 2.75. By the client alone: nothing the simulation reads.
         */
        public UnitVisual clip(java.util.Set<String> conditions, String clip, ClipMode mode, ClipStart start,
                String keepGroup, float slowest, float fastest) {
            chooseAgain();
            clipStates.add(new ClipState(new java.util.TreeSet<>(conditions), clip, mode, start, keepGroup, slowest,
                    fastest));
            return this;
        }

        /**
         * What a set of words chooses of it — its model, its pieces' state, its clip state and its barrels — worked out once
         * for each set of words it is asked about and kept: a factory drawn with six layers weighed 122 sets of words a
         * frame, every candidate's words split again and copied, for the same answer as the frame before.
         */
        record Chosen(String model, int pieceState, int clipState, java.util.Map<Integer, WeaponBones> weaponBones) {
        }

        /** The choices made so far, by the words they were made for; cleared whenever what it chooses from changes. */
        private final java.util.Map<java.util.Set<String>, Chosen> chosen = new java.util.concurrent.ConcurrentHashMap<>();
        /** How many choices have been worked out rather than found, for a test. */
        int choicesMade;

        /** Most sets of words kept before the kept choices are let go and made again as asked. */
        private static final int MOST_CHOICES = 256;

        Chosen chooseFor(java.util.Set<String> holding) {
            var known = chosen.get(holding);
            if (known != null) {
                return known;
            }
            choicesMade++;
            var made = new Chosen(modelForNow(holding), pieceStateForNow(holding), clipStateForNow(holding),
                    weaponBonesForNow(holding));
            if (chosen.size() >= MOST_CHOICES) {
                chosen.clear(); // ponytail: a look asked about more sets of words than this chooses them again
            }
            chosen.put(java.util.Set.copyOf(holding), made);
            return made;
        }

        /** What it chooses from changed: every choice is made again. */
        private void chooseAgain() {
            chosen.clear();
        }

        /** Which of its clip states its words best fit, or -1 for none: then its roles play. */
        int clipStateFor(java.util.Set<String> holding) {
            return clipStates.isEmpty() ? -1 : chooseFor(holding).clipState();
        }

        private int clipStateForNow(java.util.Set<String> holding) {
            if (clipStates.isEmpty()) {
                return -1;
            }
            var words = new java.util.ArrayList<java.util.SortedSet<String>>();
            for (var state : clipStates) {
                words.add(state.words());
            }
            return uz.dukeengine.core.thing.Conditions.bestFit(words, holding);
        }

        /** Whether anything of its look is chosen by words, so a client need not work out its words otherwise. */
        boolean choosesByWords() {
            return !conditionalModels.isEmpty() || !pieceStates.isEmpty() || !conditionalWeaponBones.isEmpty();
        }

        /**
         * The words it holds, to choose by: its own — a rank, an upgrade's weapon set — the world's, and those its
         * health decides ({@link #whenHurt}).
         */
        java.util.Set<String> holding(float healthFraction, java.util.Set<String> world,
                java.util.Collection<String> own) {
            var holding = new java.util.HashSet<>(world == null ? java.util.Set.<String>of() : world);
            if (own != null) {
                holding.addAll(own);
            }
            whenHurt.forEach((word, below) -> {
                if (healthFraction < below) {
                    holding.add(word);
                }
            });
            return holding;
        }

        /**
         * A picture on the ground under it, chosen by its words — see {@link #groundPicture}.
         *
         * @param width             along the way it faces; 0, as long as its thing's model
         * @param depth             across it; 0, as wide as its thing's model
         * @param hiddenFromEnemies drawn only to viewers allied or neutral to its owner
         */
        record GroundPicture(java.util.SortedSet<String> words, String picture, float width, float depth,
                int fadeFrames, boolean hiddenFromEnemies) {
        }

        final java.util.List<GroundPicture> groundPictures = new java.util.ArrayList<>();

        /**
         * A picture laid on the ground under it while its words best fit {@code conditions} — the reference's horde and
         * shadow decals: {@code width} along the way it faces and {@code depth} across, following it and its facing over
         * the ground's rise and fall, fading in over {@code fadeFrames} of the game's frames when its words come and out
         * when they go. One of no words is always there, under whichever its words choose — the reference's shadow
         * decal, which a soldier lies on while a horde's picture is laid over it. A whole path from the resource root;
         * with the thing out of sight, gone.
         */
        public UnitVisual groundPicture(java.util.Set<String> conditions, String picture, float width, float depth,
                int fadeFrames) {
            return groundPicture(conditions, picture, width, depth, fadeFrames, false);
        }

        /**
         * The same, drawn only to viewers allied or neutral to its owner where {@code hiddenFromEnemies} — the
         * reference's fake building, whose picture on the ground tells its own side and the neutral it is a fake and
         * shows its enemies nothing ({@code Drawable::changedTeam}, {@code KINDOF_FS_FAKE}).
         */
        public UnitVisual groundPicture(java.util.Set<String> conditions, String picture, float width, float depth,
                int fadeFrames, boolean hiddenFromEnemies) {
            groundPictures.add(new GroundPicture(new java.util.TreeSet<>(conditions), picture, width, depth,
                    Math.max(0, fadeFrames), hiddenFromEnemies));
            return this;
        }

        /**
         * A mark by its health bar, chosen by its words — see {@link #mark}.
         *
         * @param frames      the strip's pictures in order, whole paths from the resource root
         * @param frameMillis how long each is shown, in the window's milliseconds
         * @param pingPong    played back from the last to the first rather than round again
         * @param scale       how big, times each picture's own size in pixels
         * @param place       where by the bar it is drawn
         * @param group       the marks it takes the place of by its words; those of another group are shown with it
         * @param randomStart its strip started on a picture drawn at random each time its words come, rather than on
         *                    its first
         */
        record Mark(java.util.SortedSet<String> words, java.util.List<String> frames, int frameMillis,
                boolean pingPong, float scale, MarkPlace place, String group, boolean randomStart) {

            /** Where in its strip it starts as its words come, in seconds: its first picture, or one at random. */
            float startSeconds(java.util.random.RandomGenerator random) {
                return randomStart && frames.size() > 1
                        ? random.nextInt(frames.size()) * Math.max(1, frameMillis) / 1000f : 0f;
            }

            /** The picture shown {@code seconds} into the strip. */
            String frameAt(float seconds) {
                int count = frames.size();
                if (count <= 1) {
                    return count == 0 ? null : frames.getFirst();
                }
                int cycle = pingPong ? 2 * count - 2 : count;
                int step = Math.floorMod((int) Math.floor(seconds * 1000f / Math.max(1, frameMillis)), cycle);
                return frames.get(step < count ? step : cycle - step);
            }
        }

        final java.util.List<Mark> marks = new java.util.ArrayList<>();

        /**
         * A mark drawn just below its health bar while its words best fit {@code conditions} — the reference's
         * Enthusiastic and Subliminal icons: a strip of pictures played {@code frameMillis} apiece, round again or back
         * and forth, {@code along} of the bar's width from its left end, at {@code scale} of the pictures' own size — the
         * reference's 1 for buildings and huge vehicles, 0.75 for vehicles and 0.5 otherwise, each template its own.
         */
        public UnitVisual mark(java.util.Set<String> conditions, java.util.List<String> frames, int frameMillis,
                boolean pingPong, float along, float scale) {
            return mark(conditions, frames, frameMillis, pingPong, scale, MarkPlace.under(along), "", false);
        }

        /**
         * The same, drawn where {@code place} says, one of {@code group} — the marks of a group taking each other's
         * place by their words, and those of different groups shown at once, as the reference draws a rank's chevron
         * beside the enthusiastic mark ({@code Drawable::drawIconUI}) — its strip starting on a picture drawn at random
         * each time its words come where {@code randomStart} says so.
         */
        public UnitVisual mark(java.util.Set<String> conditions, java.util.List<String> frames, int frameMillis,
                boolean pingPong, float scale, MarkPlace place, String group, boolean randomStart) {
            marks.add(new Mark(new java.util.TreeSet<>(conditions), java.util.List.copyOf(frames), frameMillis,
                    pingPong, scale, place == null ? MarkPlace.under(0.25f) : place, group == null ? "" : group,
                    randomStart));
            return this;
        }

        /** Which of its marks its words show: of each group, the one they best fit, groups in the order first named. */
        java.util.List<Integer> marksFor(java.util.Set<String> holding) {
            var groups = new java.util.LinkedHashMap<String, java.util.List<Integer>>();
            for (int index = 0; index < marks.size(); index++) {
                groups.computeIfAbsent(marks.get(index).group(), group -> new java.util.ArrayList<>()).add(index);
            }
            var shown = new java.util.ArrayList<Integer>();
            for (var members : groups.values()) {
                int best = uz.dukeengine.core.thing.Conditions.bestFit(
                        members.stream().map(index -> marks.get(index).words()).toList(), holding);
                if (best >= 0) {
                    shown.add(members.get(best));
                }
            }
            return shown;
        }

        /** A colour added to its own, chosen by its words — see {@link #tint(java.util.Set, float, float, float, int)}. */
        record WordTint(java.util.SortedSet<String> words, float red, float green, float blue, int easeFrames) {
        }

        final java.util.List<WordTint> wordTints = new java.util.ArrayList<>();

        /**
         * A colour added to its own while its words best fit {@code conditions} — the reference's Frenzy tints, (0.2,
         * -0.2, -0.2) on a vehicle and (0, -0.7, -0.7) on a soldier — eased in over {@code easeFrames} of the game's
         * frames when the words come and back out over them when they go. A component below nothing takes that much
         * away.
         */
        public UnitVisual tint(java.util.Set<String> conditions, float red, float green, float blue, int easeFrames) {
            wordTints.add(new WordTint(new java.util.TreeSet<>(conditions), red, green, blue, Math.max(0, easeFrames)));
            return this;
        }

        /** Which of its tints its words best fit, or -1 for none. */
        int tintFor(java.util.Set<String> holding) {
            var words = new java.util.ArrayList<java.util.SortedSet<String>>();
            for (var tint : wordTints) {
                words.add(tint.words());
            }
            return uz.dukeengine.core.thing.Conditions.bestFit(words, holding);
        }

        /**
         * How it is drawn along a line — see {@link #alongALine}.
         *
         * @param across its scale across the line
         * @param up     its scale up from it
         */
        record LineLook(String first, String middle, String last, float across, float up) {
        }

        /** Drawn along its line rather than at its place, or null — see {@link #alongALine}. */
        LineLook line;

        /**
         * Drawn along its line ({@code UnitView.span}) rather than at its place — the reference's bridges: its model's
         * pieces {@code first}, {@code middle} repeated as often as fits best and {@code last}, end to end along the
         * model's x from one end of the line to the other, stretched along it to end exactly at the far end, rising with
         * it, and scaled {@code across} and {@code up} — the reference's 0.67 to 2. A model whose pieces are not there, or
         * do not meet within 5% of its length, is drawn once, stretched. Its model is chosen by its words as any is — a
         * bridge's damaged and ruined ones — its texture, named, is every piece's, and it is fogged as the ground is.
         */
        public UnitVisual alongALine(String first, String middle, String last, float across, float up) {
            this.line = new LineLook(first, middle, last, across, up);
            return this;
        }

        /** How seen it is at its faintest, drawn see-through to its own side — see {@link #seeThrough(float)}. */
        float seeThroughFaintest = 0.5f;
        /** Whether it may be drawn as a glow — see {@link #neverGlows()}. */
        boolean glows = true;

        /**
         * How seen it is at its faintest while it holds the game's see-through word ({@link Visuals#seeThrough}) — the
         * reference's {@code FriendlyOpacityMin}: 0.5 where nothing is said, 0.3 for a sniper, 1 for a trap that should
         * not flicker at all, 0 for a mine that vanishes between pulses.
         */
        public UnitVisual seeThrough(float faintest) {
            this.seeThroughFaintest = Math.clamp(faintest, 0f, 1f);
            return this;
        }

        /** Never drawn as a glow, whatever it holds — the reference's mines. */
        public UnitVisual neverGlows() {
            this.glows = false;
            return this;
        }

        /**
         * How it sways in the wind — see {@link #sway}.
         *
         * @param bearing the way it leans, the simulation's radians as a thing's facing is
         */
        public record Sway(float least, float most, float bearing, float periodSeconds, int groups) {

            public Sway {
                periodSeconds = Math.max(0.01f, periodSeconds);
                groups = Math.max(1, groups);
            }

            /**
             * How far a thing leans {@code seconds} into the game: from its most, back to its least and round again
             * once a period of its group's — its things spread over the groups, each group's period up to a tenth
             * either side of the look's, so a forest does not move as one ({@code W3DTreeBuffer::updateSway}).
             */
            float angleAt(int thing, float seconds) {
                int group = Math.floorMod(thing, groups);
                float spread = groups == 1 ? 0f : -0.1f + 0.2f * group / (groups - 1);
                float period = periodSeconds * (1f + spread);
                float swing = (1f + (float) Math.cos(2.0 * Math.PI * seconds / period)) / 2f;
                return least + (most - least) * swing;
            }

            /** The lean {@code seconds} into the game, as a turn laid over the thing's own. */
            com.jme3.math.Quaternion tiltAt(int thing, float seconds) {
                // Toward the bearing in the client's frame, where the map's y is the scene's z.
                var toward = new com.jme3.math.Vector3f((float) Math.cos(bearing), 0f, (float) Math.sin(bearing));
                var axis = com.jme3.math.Vector3f.UNIT_Y.cross(toward).normalizeLocal();
                return new com.jme3.math.Quaternion().fromAngleNormalAxis(angleAt(thing, seconds), axis);
            }
        }

        Sway sway;

        /**
         * It sways in the wind, as the reference's trees do ({@code W3DTreeBuffer::updateSway}, {@code BreezeInfo}):
         * leaning toward {@code bearing} — the simulation's radians, as a thing's facing is — between {@code least} and
         * {@code most} radians and back once a {@code periodSeconds} of the game's time, its things in {@code groups}
         * whose periods lie up to a tenth either side of it. The reference's: 0 to 0.11 toward 60 degrees from its
         * north, over 5 seconds, in 10. Drawing only; a thing leaned by the simulation — toppling — sways no more.
         */
        public UnitVisual sway(float least, float most, float bearing, float periodSeconds, int groups) {
            this.sway = new Sway(least, most, bearing, periodSeconds, groups);
            return this;
        }

        /** Its picture of no words — a shadow — kept under whichever its words choose; null for none. */
        GroundPicture plainGroundPicture() {
            for (var picture : groundPictures) {
                if (picture.words().isEmpty()) {
                    return picture;
                }
            }
            return null;
        }

        /** Which of its ground pictures its words best fit, or -1 for none. */
        int groundPictureFor(java.util.Set<String> holding) {
            var words = new java.util.ArrayList<java.util.SortedSet<String>>();
            for (var picture : groundPictures) {
                words.add(picture.words());
            }
            return uz.dukeengine.core.thing.Conditions.bestFit(words, holding);
        }

        /** Which of its pieces' states its words best fit, or -1 for none. */
        int pieceStateFor(java.util.Set<String> holding) {
            return pieceStates.isEmpty() ? -1 : chooseFor(holding).pieceState();
        }

        private int pieceStateForNow(java.util.Set<String> holding) {
            var words = new java.util.ArrayList<java.util.SortedSet<String>>();
            for (var state : pieceStates) {
                words.add(state.words());
            }
            return uz.dukeengine.core.thing.Conditions.bestFit(words, holding);
        }

        /** Its weapon slots' bones for the words it holds: the best-fitting set's, or the plain ones. */
        java.util.Map<Integer, WeaponBones> weaponBonesFor(java.util.Set<String> holding) {
            return conditionalWeaponBones.isEmpty() ? weaponBones : chooseFor(holding).weaponBones();
        }

        private java.util.Map<Integer, WeaponBones> weaponBonesForNow(java.util.Set<String> holding) {
            if (conditionalWeaponBones.isEmpty()) {
                return weaponBones;
            }
            var sets = new java.util.ArrayList<java.util.Map<Integer, WeaponBones>>();
            var words = new java.util.ArrayList<java.util.SortedSet<String>>();
            for (var candidate : conditionalWeaponBones.entrySet()) {
                sets.add(candidate.getValue());
                words.add(wordsOf(candidate.getKey()));
            }
            int best = uz.dukeengine.core.thing.Conditions.bestFit(words, holding);
            return best < 0 ? weaponBones : sets.get(best);
        }

        /** Whether it has no shape — see {@link #noShape}. */
        boolean shapeless;

        /**
         * It has no shape: nothing is built or drawn for it — no placeholder, no shadow — and nothing picks it: the
         * reference's draw module with no model, an ambient sound's or a shroud clearer's, which stood on the ground
         * as grey boxes. Its sounds, its effects, its bars and its rings stay as any thing's.
         */
        public UnitVisual noShape() {
            shapeless = true;
            return this;
        }

        public UnitVisual model(String assetPath) {
            chooseAgain();
            this.modelPath = assetPath;
            return this;
        }

        /** The model for a set of conditions, drawn instead of the plain one when every word of it holds. */
        public UnitVisual model(java.util.Set<String> conditions, String assetPath) {
            chooseAgain();
            conditionalModels.put(String.join(" ", new java.util.TreeSet<>(conditions)), assetPath);
            return this;
        }

        /** The share of its health below which {@code condition} holds: {@code whenHurt("DAMAGED", 0.5f)}. */
        public UnitVisual whenHurt(String condition, float belowShareOfHealth) {
            whenHurt.put(condition, belowShareOfHealth);
            return this;
        }

        /**
         * Which model to draw: the best-fitting of the conditional ones, or the plain one.
         *
         * <p>A candidate fits when every word of it holds. Among those that fit the one with the most words
         * wins, so {@code DAMAGED SNOW} beats {@code SNOW} beats the plain model — the more a look says
         * about the moment, the better it describes it.
         *
         * <p><b>A tie is broken by the words themselves, sorted.</b> Not by whichever the map happened to
         * hand over first: two machines with the same snapshot and the same world must draw the same
         * building, and an iteration order is not a promise. It is the one thing here that would be
         * invisible until two players disagreed about what they were looking at.
         *
         * @param healthFraction what share of its health it has left, for {@link #whenHurt}
         * @param world          the conditions the world is in, which it was given once when it was loaded
         */
        public String modelFor(float healthFraction, java.util.Set<String> world) {
            if (conditionalModels.isEmpty()) {
                return modelPath; // a template that names none pays nothing, not even the set below
            }
            return modelFor(holding(healthFraction, world, java.util.List.of()));
        }

        /** The model its health and the world choose now, and its own words where any of its looks chooses by them. */
        String modelFor(float healthFraction, java.util.Set<String> world, java.util.Collection<String> own) {
            return choosesByWords() ? modelFor(holding(healthFraction, world, own)) : modelFor(healthFraction, world);
        }

        /** The same, for the words it holds as {@link #holding} worked them out — its own among them. */
        String modelFor(java.util.Set<String> holding) {
            return conditionalModels.isEmpty() ? modelPath : chooseFor(holding).model();
        }

        private String modelForNow(java.util.Set<String> holding) {
            if (conditionalModels.isEmpty()) {
                return modelPath;
            }
            // The engine's one rule for choosing by condition — the simulation picks weapon sets by it too.
            var paths = new java.util.ArrayList<String>();
            var words = new java.util.ArrayList<java.util.SortedSet<String>>();
            for (var candidate : conditionalModels.entrySet()) {
                var said = wordsOf(candidate.getKey());
                if (!said.isEmpty()) {
                    paths.add(candidate.getValue());
                    words.add(said);
                }
            }
            int best = uz.dukeengine.core.thing.Conditions.bestFit(words, holding);
            return best < 0 ? modelPath : paths.get(best);
        }

        /** The words of a condition key, sorted and without repeats, so two spellings of one set are one. */
        private static java.util.SortedSet<String> wordsOf(String key) {
            var words = new java.util.TreeSet<String>();
            for (var word : key.trim().split("\\s+")) {
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
            return words;
        }

        /**
         * One named piece out of a model file, rather than the whole thing.
         *
         * <p>Kits bundle props with the character carrying them — a bow, a shield,
         * the arrow on the string — and those are the props the game needs when it
         * comes to draw one on its own. Without this the only way to use the arrow
         * a character is holding is to load the character.
         *
         * <p>The name is the one inside the file, which is not always the name the
         * thing deserves: exporters mislabel, and a mesh called "Eyes" can turn out
         * to be an arrow. Naming it here rather than in code keeps that where
         * somebody can see it.
         */
        public UnitVisual modelPart(String assetPath, String partName) {
            chooseAgain();
            this.modelPath = assetPath;
            this.modelPart = partName;
            return this;
        }

        /**
         * A second model carried on one of this one's bones — a bow, a sword, a
         * lantern.
         *
         * <p>Character kits ship weapons as separate files rather than as part of
         * the body, and they do it on purpose: one ranger and a rack of weapons is
         * every armed ranger there is, where a ranger-with-a-bow is one of them.
         * The rig has a bone for it — KayKit's is {@code handslot.l} — authored so
         * that a weapon hung there lands in the hand and stays in it through every
         * clip, because the hand is what the clip moves.
         *
         * @param scale what to multiply the held model by, when it and the body
         *     were not authored at the same size; 1 for a kit that ships both
         * @see #heldTurn
         */
        public UnitVisual holds(String assetPath, String boneName, float scale) {
            var one = new Carried();
            one.path = assetPath;
            one.bone = boneName;
            one.scale = scale;
            carried.add(one);
            return this;
        }

        /** The one being described, so heldTurn and heldAt settle the last holds. */
        private Carried last() {
            if (carried.isEmpty()) {
                carried.add(new Carried()); // turned before it was given anything
            }
            return carried.get(carried.size() - 1);
        }

        /**
         * How far to turn what he carries, in degrees, before it goes on the bone.
         *
         * <p>The bone gets a weapon into the hand and does not settle which way
         * round it is, because that is between the bone and the <em>model</em> and
         * a kit does not always lay every model out the same way. KayKit's bow is
         * the case in point: every other weapon in the pack runs along its own
         * {@code +Y} and the bow runs along {@code +Z}, so the bone that points a
         * sword's blade out of the fist points the bow's length straight up, which
         * is right — and then hands it over with the string facing away from the
         * archer and the grip against his knuckles, which is not.
         *
         * <p>So this is the same kind of number as a wall's shift or a unit's
         * facing: a fact about the art, measured once and written down where it
         * can be seen.
         */
        public UnitVisual heldTurn(float pitchDegrees, float yawDegrees, float rollDegrees) {
            var one = last();
            one.pitch = pitchDegrees;
            one.yaw = yawDegrees;
            one.roll = rollDegrees;
            return this;
        }

        /**
         * How far to shift what he carries off the bone it hangs on.
         *
         * <p>The same kind of number as {@link #heldTurn}: a fact about the art,
         * measured once and written down. The bone puts a weapon in the hand and
         * a hand is where a weapon goes, so most things want none of this — but
         * this rig has exactly two attachment points, both of them hands, and a
         * quiver goes on the BACK. Hung on the chest bone with no shift it sits
         * inside the man; shifted back and up it sits over his shoulder.
         */
        public UnitVisual heldAt(float x, float y, float z) {
            var one = last();
            one.x = x;
            one.y = y;
            one.z = z;
            return this;
        }

        /**
         * What this thing looks like in flight, by the name of a recipe declared
         * with {@link Visuals#effect}.
         *
         * <p>For projectiles. A creature may name one too and nothing stops it,
         * but a burning skeleton is a longer conversation than a burning arrow.
         */
        public UnitVisual effect(String recipeName) {
            this.effect = recipeName;
            return this;
        }

        /**
         * Where on this thing the effect sits: how far forward of its middle, and
         * the height is {@link #yOffset} because that is where the thing itself is
         * drawn.
         *
         * <p>Both halves were wrong before either was set. A trail hung on a unit's
         * root comes out of the <em>ground under it</em>, because the root is where
         * the unit stands and the model is lifted off it — so a burning arrow flew
         * at chest height with its fire dragging along the floor. And a trail at
         * the middle of a twelve-unit shaft is fire coming out of the middle of an
         * arrow, where it belongs at the head.
         */
        public UnitVisual effectAt(float forward) {
            this.effectForward = forward;
            return this;
        }

        /**
         * The colour map to draw this model with, overriding whatever the file
         * came with.
         *
         * <p>Two reasons, and the first is not optional. Model kits routinely ship
         * a base colour texture that jME's glTF loader does not bind, so the
         * creature arrives untextured and nobody finds out until they look at it.
         * The second is that a kit with two creatures and three colourways for
         * each has six creatures in it, if you can say which colourway you want —
         * and a dungeon needs more kinds of monster than a free kit ships models.
         */
        public UnitVisual texture(String assetPath) {
            this.texturePath = assetPath;
            return this;
        }

        /**
         * Take this unit's animations from another file — a library built on the
         * same skeleton as the model.
         *
         * <p>Creature kits and animation libraries are sold separately and meet on
         * the standard humanoid rig, so one library moves every creature in a kit.
         * Only the clips named by {@link #idle}, {@link #walk} and {@link #attack}
         * are taken: a library holds dozens, and a unit needs three.
         */
        public UnitVisual animationsFrom(String assetPath) {
            animations.add(new AnimationSource(assetPath, null));
            return this;
        }

        /**
         * The same, for a file that holds exactly one animation: call it
         * {@code clipName} here, and name it with {@link #idle} and friends.
         *
         * <p>Animation sites hand their work out one movement per file, and every
         * one of those files carries the same exporter-generated clip name. The
         * file is then the only thing that says which is the run and which is the
         * punch, so the name has to be given on the way in. Call this once per
         * animation.
         */
        public UnitVisual animationFrom(String assetPath, String clipName) {
            animations.add(new AnimationSource(assetPath, clipName));
            return this;
        }

        /**
         * Draw this unit type in a colour of its own instead of its player's.
         *
         * <p>Player colour answers "whose is it?", which is the only question an
         * RTS asks of a shape. A game whose sides each field several kinds of
         * thing has a second question — "what is it?" — and with no models yet
         * there is nothing left to answer it with: every enemy is the same
         * capsule in the same colour, on screen and on the minimap alike.
         *
         * <p>Left unset, the player's colour is used exactly as before.
         */
        public UnitVisual colour(java.awt.Color colour) {
            this.colour = colour;
            return this;
        }

        /**
         * A wash of colour over the model's own texture.
         *
         * <p>Distinct from {@link #colour}, which replaces a shape's colour and is
         * what the minimap dot is drawn in. A tint multiplies whatever the skin
         * already is, so it separates two monsters sharing one texture without
         * flattening either into a single colour — and without changing what the
         * player reads on the minimap.
         */
        public UnitVisual tint(java.awt.Color tint) {
            this.tint = tint;
            return this;
        }

        public UnitVisual scale(float scale) {
            this.scale = scale;
            return this;
        }

        /** Raise (or sink) the model relative to the ground. */
        public UnitVisual yOffset(float yOffset) {
            this.yOffset = yOffset;
            return this;
        }

        /** Extra rotation when the model file doesn't face the engine's +X. */
        public UnitVisual facing(float degrees) {
            this.facingDegrees = degrees;
            return this;
        }

        public UnitVisual idle(String animName) {
            this.idleAnim = animName;
            return this;
        }

        public UnitVisual walk(String animName) {
            this.walkAnim = animName;
            return this;
        }

        /**
         * What it plays for one blow — <em>once</em> per blow, not on a loop.
         *
         * <p>A unit that has a target is "attacking" for the whole engagement,
         * reload and all, so a clip chosen from that state runs at its own tempo
         * and has nothing to do with when the weapon actually lets go. The client
         * plays this on the shot instead, which is a moment the game already
         * announces.
         */
        public UnitVisual attack(String animName) {
            this.attackAnim = animName;
            return this;
        }

        /**
         * What it plays when something takes health off it — a short flinch, over
         * whatever else it was doing.
         *
         * <p>Driven by the health in the snapshot rather than by whatever fired,
         * so a blow, an arrow arriving a moment after it was loosed, and a spell
         * with no shooter at all all read the same: it flinches when it is hurt.
         */
        public UnitVisual hurt(String animName) {
            this.hurtAnim = animName;
            return this;
        }

        /**
         * Where weapon slot {@code slot}'s shots come out: the bone its {@code fired.<weapon>} effect plays at,
         * turned with it — the reference's {@code WeaponFireFXBone}. Numbered per barrel: {@code Muzzle} is {@code
         * MUZZLE01}, {@code MUZZLE02} … taken in turn, a barrel a shot, or {@code MUZZLE} alone where the model
         * numbers none. The first slot is 0. With none, a shot's effect plays at the middle of the thing.
         */
        public UnitVisual fireBone(int slot, String bone) {
            var was = weaponBones.getOrDefault(slot, new WeaponBones(null, null, null));
            weaponBones.put(slot, new WeaponBones(bone, was.flash(), was.recoil()));
            return this;
        }

        /**
         * The piece of the model slot {@code slot}'s muzzle flash is drawn with, numbered per barrel as {@link
         * #fireBone} is: hidden but on the frames its barrel fires — the reference's {@code WeaponMuzzleFlash}.
         */
        public UnitVisual muzzleFlash(int slot, String piece) {
            var was = weaponBones.getOrDefault(slot, new WeaponBones(null, null, null));
            weaponBones.put(slot, new WeaponBones(was.fire(), piece, was.recoil()));
            return this;
        }

        /**
         * The bone that kicks back along its own x when slot {@code slot}'s barrel fires, numbered per barrel as
         * {@link #fireBone} is — the reference's {@code WeaponRecoilBone}. How it kicks is {@link #recoil}.
         */
        public UnitVisual recoilBone(int slot, String bone) {
            var was = weaponBones.getOrDefault(slot, new WeaponBones(null, null, null));
            weaponBones.put(slot, new WeaponBones(was.fire(), was.flash(), bone));
            return this;
        }

        /**
         * How its barrels kick: {@code initial} back the first frame, that times {@code damping} each frame after,
         * as far as {@code most}; then back again at {@code settle} a frame. The reference's {@code
         * InitialRecoilSpeed}, {@code MaxRecoilDistance}, {@code RecoilDamping} and {@code RecoilSettleSpeed}, in
         * frames; left alone, its defaults: 2, 3, 0.4 and 0.065.
         */
        public UnitVisual recoil(float initial, float most, float damping, float settle) {
            this.recoil = new Barrels.Recoil(initial, most, damping, settle);
            return this;
        }

        /**
         * Its treads, whose picture runs as it moves — the reference's {@code W3DTankDraw}. Every piece whose name
         * begins {@code left} is a left tread and {@code right} a right one — {@code TREADSL} is {@code TREADSL01},
         * {@code TREADSL02} … — and one both begin is neither side's: it runs as the vehicle drives and stands as it
         * pivots. {@code rate} is how many lengths of the tread's picture run by in a second ({@code
         * TreadAnimationRate}); turning slower than {@code pivotFraction} of its speed, the sides run opposite ways
         * ({@code TreadPivotSpeedFraction}); faster than {@code driveFraction} of it, all run with it ({@code
         * TreadDriveSpeedFraction}); otherwise they stand. See {@link RunningGear}.
         */
        public UnitVisual treads(String left, String right, float rate, float driveFraction, float pivotFraction) {
            this.treads = new Treads(left, right, rate, driveFraction, pivotFraction);
            return this;
        }

        /**
         * Its wheels, rolling as it moves — the reference's {@code W3DTruckDraw}. Each of {@code bones} and of
         * {@code front} rolls {@code multiplier} radians a unit it travels ({@code TireRotationMultiplier}; one over
         * the tyre's radius rolls it without skidding), and the {@code front} ones steer as far as {@code
         * steerDegrees} toward a turn (its locomotor's {@code FrontWheelTurnAngle}). See {@link RunningGear}.
         */
        public UnitVisual wheels(java.util.List<String> bones, float multiplier, java.util.List<String> front,
                float steerDegrees) {
            this.wheels = new Wheels(bones, multiplier, front, (float) Math.toRadians(steerDegrees));
            return this;
        }

        /** What it plays as it dies, before the body is taken away. */
        public UnitVisual die(String animName) {
            this.dieAnim = animName;
            return this;
        }

        /**
         * What it plays as it dies this way — {@code die("EXPLODED", "Die_Thrown")} — instead of the plain
         * {@link #die(String)} clip, which is what every other death still plays. The reference's soldiers fall
         * over when shot, are thrown when shelled and burn when burned, and all three are one model.
         */
        public UnitVisual die(String deathType, String animName) {
            if (deathType != null && animName != null) {
                dieAnims.put(deathType.toUpperCase(java.util.Locale.ROOT), animName);
            }
            return this;
        }

        /** The clip it dies by for this death: the one given for it, or the plain one. */
        String dieAnimFor(uz.dukeengine.core.module.DeathType deathType) {
            return deathType == null ? dieAnim : dieAnims.getOrDefault(deathType.name(), dieAnim);
        }

        /**
         * One more clip this creature wants loaded, beyond the five it is drawn
         * standing, walking, striking, flinching and falling with.
         *
         * <p>A clip has to be copied onto the model before anything can play it,
         * and what gets copied is what was asked for by name -- so a gesture
         * nobody has listed is simply not there when the moment comes. This is
         * where a game lists the ones its own rules will call for: a spell it
         * casts two-handed, a bow it shoulders, a door it opens.
         */
        public UnitVisual alsoAnimation(String animName) {
            if (animName != null && !animName.isBlank() && !otherAnims.contains(animName)) {
                otherAnims.add(animName);
            }
            return this;
        }

        public UnitVisual fireSound(String assetPath) {
            this.fireSound = assetPath;
            return this;
        }

        public UnitVisual dieSound(String assetPath) {
            this.dieSound = assetPath;
            return this;
        }
    }

    // Linked, so the order a game declares its units in is the order anything
    // walking them sees -- which is what a loading bar advances through.
    private final Map<String, UnitVisual> units = new java.util.LinkedHashMap<>();
    private final Map<String, EffectVisual> effects = new java.util.LinkedHashMap<>();
    private final UnitVisual defaults = new UnitVisual();
    private String assetRoot;
    private String discoveryTemplate;
    private Tileset tileset;

    private Visuals() {
    }

    public static Visuals create() {
        return new Visuals();
    }

    /**
     * A directory to load assets from (in addition to the classpath). This is
     * how the Studio points the game at a project's assets folder; exported
     * games instead ship assets on the classpath and don't need it.
     */
    public Visuals assetRoot(String directory) {
        this.assetRoot = directory;
        return this;
    }

    public String getAssetRoot() {
        return assetRoot;
    }

    /**
     * A weapon slot's named points on a model — see {@link UnitVisual#fireBone}. Any of them may be missing.
     *
     * @param fire   the bone its shots' effect plays at
     * @param flash  the piece its muzzle flash is drawn with
     * @param recoil the bone that kicks back
     */
    public record WeaponBones(String fire, String flash, String recoil) {
    }

    /**
     * One state of a model's pieces — see {@link UnitVisual#pieces}.
     *
     * @param words the condition words it is for, all of which must hold
     * @param hide  the pieces it hides
     * @param show  the pieces it shows
     */
    public record PieceState(java.util.SortedSet<String> words, java.util.List<String> hide,
            java.util.List<String> show) {
        public PieceState {
            words = java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(words));
            hide = hide == null ? java.util.List.of() : java.util.List.copyOf(hide);
            show = show == null ? java.util.List.of() : java.util.List.copyOf(show);
        }
    }

    /** How a clip chosen by words plays — see {@link UnitVisual#clip}. */
    public enum ClipMode {
        /** To its end, and it stays there. */
        ONCE,
        /** Back to its start, and it stays there. */
        ONCE_BACKWARDS,
        LOOP,
        LOOP_BACKWARDS,
        /** Held on the frame it starts at: the reference's {@code MANUAL}. */
        HOLD
    }

    /** Where a clip chosen by words starts — see {@link UnitVisual#clip}. */
    public enum ClipStart {
        FIRST,
        LAST,
        /** Anywhere in it, the same on every machine for the same thing and frame. */
        RANDOM
    }

    /**
     * A clip for a set of words — see {@link UnitVisual#clip}.
     *
     * @param words     the condition words it is for, all of which must hold
     * @param clip      the clip's name on the model
     * @param mode      how it plays
     * @param start     where it starts, or {@code null} for where its mode starts
     * @param keepGroup the group it keeps the frame across, or {@code null} for none
     */
    public record ClipState(java.util.SortedSet<String> words, String clip, ClipMode mode, ClipStart start,
            String keepGroup, float slowest, float fastest) {
        public ClipState {
            words = java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(words));
            mode = mode == null ? ClipMode.LOOP : mode;
            slowest = slowest > 0f ? slowest : 1f;
            fastest = Math.max(slowest, fastest > 0f ? fastest : slowest);
        }

        /** A clip played at its own pace, as every one was before one could name a speed. */
        public ClipState(java.util.SortedSet<String> words, String clip, ClipMode mode, ClipStart start,
                String keepGroup) {
            this(words, clip, mode, start, keepGroup, 1f, 1f);
        }
    }

    /** A vehicle's treads — see {@link UnitVisual#treads}. */
    record Treads(String left, String right, float rate, float driveFraction, float pivotFraction) {
    }

    /** A vehicle's wheels — see {@link UnitVisual#wheels}; how far the front ones steer, in radians. */
    record Wheels(java.util.List<String> bones, float multiplier, java.util.List<String> front, float steer) {
        Wheels {
            bones = bones == null ? java.util.List.of() : java.util.List.copyOf(bones);
            front = front == null ? java.util.List.of() : java.util.List.copyOf(front);
        }
    }

    /** Configure the look and sound of one unit template. */
    /**
     * A template drawn as its own block says: its model, size, tint and facing, the clips it names, and the
     * library files of the animation set it links.
     *
     * <p>Here so that no game writes it again. Every game on this engine used to end up with the same twenty
     * lines handing its own record's look fields to this class one at a time — see {@link Drawn}, which is the
     * other half of the same seam. A clip the template leaves out falls back to the set's; a template with no
     * model at all is left to its {@code Geometry}, which is the client's own fallback.
     *
     * @param set the animation set {@link Drawn#animations()} names, or null when it names none
     */
    public Visuals draw(Drawn template, AnimationSet set) {
        if (!template.hasModel()) {
            return this;
        }
        return unit(template.name(), drawn -> {
            drawn.model(template.model()).scale(template.modelScale())
                    .tint(new java.awt.Color(template.tint())).facing(template.facing());
            template.models().forEach(drawn.conditionalModels::put);
            template.whenHurt().forEach(drawn::whenHurt);
            if (set != null) {
                for (var library : set.libraries()) {
                    drawn.animationsFrom(library);
                }
            }
            drawn.idle(clipOf(template.idle(), set == null ? null : set.idle()));
            drawn.walk(clipOf(template.walk(), set == null ? null : set.walk()));
            drawn.attack(clipOf(template.attack(), set == null ? null : set.attack()));
            drawn.die(clipOf(template.death(), set == null ? null : set.death()));
            if (template.effect() != null && !template.effect().isBlank()) {
                drawn.effect(template.effect());
            }
        });
    }

    /** What the template said, or what the set it links says for that moment. */
    private static String clipOf(String own, String fromSet) {
        return own == null || own.isBlank() ? fromSet : own;
    }

    private String houseColour;

    /**
     * Which meshes of a model take their owner's colour: those whose name begins with {@code prefix}.
     *
     * <p>Game art marks the parts of a model meant to be painted in the owner's colour by naming them —
     * one RTS measured had it on 2600 of its 7672 models, as meshes called {@code HOUSECOLOR01},
     * {@code HOUSECOLOR02}, which survive into the files the client loads. Without it two armies drawn
     * from the same models are told apart only by the rings under their feet, which nobody reads in a
     * battle.
     *
     * <p>The name is the game's; the engine picks none. The colour is the owner's as the game gives it,
     * laid <b>over</b> the mesh's own picture rather than instead of it — house-colour parts are painted
     * in greys precisely so their shading survives being multiplied. A game that names no prefix is drawn
     * exactly as before, and none of it reaches the simulation.
     */
    public Visuals houseColour(String prefix) {
        this.houseColour = prefix == null || prefix.isBlank() ? null : prefix;
        return this;
    }

    /** The prefix of the meshes that take their owner's colour, or null for none. */
    public String getHouseColour() {
        return houseColour;
    }

    private java.util.Set<String> worldConditions = java.util.Set.of();

    /**
     * What is true of the world everything in it is drawn in: the weather, the hour, the season — whatever
     * words this game uses for them.
     *
     * <p>Said once, when the map is loaded, because these are the conditions nothing standing in the world
     * changes by itself. What a thing decides for itself is {@code Drawn.whenHurt}, read every frame off
     * the snapshot.
     *
     * <p>The engine invents none of these words and knows what none of them mean — see
     * {@link uz.dukeengine.core.thing.Drawn#models()}. It only matches them.
     */
    public Visuals world(String... conditions) {
        worldConditions = conditions == null ? java.util.Set.of()
                : java.util.Set.copyOf(java.util.Arrays.asList(conditions));
        return this;
    }

    /** The conditions the world is in; empty for a game that never named any. */
    public java.util.Set<String> getWorldConditions() {
        return worldConditions;
    }

    public Visuals unit(String templateName, Consumer<UnitVisual> config) {
        var visual = units.computeIfAbsent(templateName, n -> new UnitVisual());
        config.accept(visual);
        return this;
    }

    /**
     * One named recipe for what a thing in flight looks like.
     *
     * <p>Named rather than written on the unit, because a recipe is shared: an
     * arrow and the drawn shot the hero looses are the same fire at two sizes, and
     * a game with six kinds of burning thing has two or three kinds of burning.
     * Units point at one by name with {@link UnitVisual#effect}.
     *
     * <p>The client owns the <em>types</em> of layer — a trail, an aura, a burst on
     * landing — and the game owns every number in them. That division is
     * the same one the rest of this class keeps, and it is what lets a new burning
     * thing be a block of settings rather than a class.
     */
    public Visuals effect(String name, Consumer<EffectVisual> config) {
        config.accept(effects.computeIfAbsent(name, n -> new EffectVisual()));
        return this;
    }

    private final Map<String, Float> effectSeconds = new HashMap<>();
    private final Map<String, Float> effectReach = new HashMap<>();

    /**
     * How long the thing a recipe draws actually lasts, for a layer that says 0.
     *
     * <p>Set by the game from the skill rather than written into the effect, so
     * that a shield that lasts four seconds is drawn for four seconds and the two
     * cannot be tuned apart. Before this, the knight's guard said 4.0 in its effect
     * block and 120 frames in its skill block, and nothing connected them.
     */
    public Visuals effectSeconds(String recipeName, float seconds) {
        if (recipeName != null && seconds > 0f) {
            effectSeconds.put(recipeName, seconds);
        }
        return this;
    }

    /** How long the named recipe's skill lasts, or 0 if nothing said. */
    public float getEffectSeconds(String recipeName) {
        var seconds = recipeName == null ? null : effectSeconds.get(recipeName);
        return seconds == null ? 0f : seconds;
    }

    /**
     * How far the thing a recipe draws reaches, for a layer measured in reach.
     *
     * <p>Set by the game from the skill, for the same reason as the seconds: the
     * meteor's warning has to be exactly as wide as the blast, and a number written
     * twice is two numbers the moment somebody tunes one of them.
     */
    public Visuals effectReach(String recipeName, float radius) {
        if (recipeName != null && radius > 0f) {
            effectReach.put(recipeName, radius);
        }
        return this;
    }

    /** How far the named recipe's skill reaches, or 0 if nothing said. */
    public float getEffectReach(String recipeName) {
        var radius = recipeName == null ? null : effectReach.get(recipeName);
        return radius == null ? 0f : radius;
    }

    private int particleBudget;

    /**
     * How many particles may be burning at once, across every effect.
     *
     * <p>A ceiling rather than a target, like the lights: past it a new layer is
     * drawn thinner, and past that it is not drawn. 0 draws no layers at all.
     */
    public Visuals particleBudget(int particles) {
        this.particleBudget = Math.max(0, particles);
        return this;
    }

    public int getParticleBudget() {
        return particleBudget;
    }

    /** The recipe under that name, or {@code null} when the game named none. */
    public EffectVisual effectNamed(String name) {
        return name == null ? null : effects.get(name);
    }

    // ---- particle systems ----

    private final Map<String, uz.dukeengine.core.content.ParticleSystem> particleSystems =
            new java.util.LinkedHashMap<>();
    private int particleSystemMost = Integer.MAX_VALUE;
    private int particleSystemNeverRefusedFrom = Integer.MAX_VALUE;

    /**
     * The game's particle systems — see {@link uz.dukeengine.core.content.ParticleSystem}.
     *
     * <p>Wherever the game names an effect — a thing's own, a projectile's, a moment's — it may name one of these
     * instead: a name that is an effect is drawn as that effect, as it always was, and one that is not is drawn
     * as the particle system of that name. So the two sit side by side and neither has to know of the other.
     */
    public Visuals particleSystems(java.util.Collection<uz.dukeengine.core.content.ParticleSystem> systems) {
        for (var system : systems) {
            if (system != null && system.name() != null) {
                particleSystems.put(system.name(), system);
            }
        }
        return this;
    }

    /** The particle system under that name, or {@code null} when the game named none. */
    public uz.dukeengine.core.content.ParticleSystem particleSystemNamed(String name) {
        return name == null ? null : particleSystems.get(name);
    }

    // ---- a thing hurt ----

    /**
     * How a blow shows on a thing: {@code hurt.<template>.<type>.major} for one worth at least {@code majorAt}
     * after its armour, {@code .minor} below it, and no second of the same damage type on the same thing within
     * {@code throttleFrames} of the game's frames.
     *
     * @param majorAt        the least a blow is worth to be a major one; {@link Float#POSITIVE_INFINITY} for never
     * @param throttleFrames how soon the same thing may show a blow of the same type again; 0 for every blow
     */
    public record HurtRule(float majorAt, int throttleFrames) {

        /** Where a game gave none: every blow minor, and every one shown. */
        public static final HurtRule NONE = new HurtRule(Float.POSITIVE_INFINITY, 0);
    }

    private final Map<String, HurtRule> hurtRules = new java.util.HashMap<>();

    /**
     * The rule for blows of {@code damageType} to things of {@code template} — the reference's {@code
     * AmountForMajorFX} and {@code ThrottleTime}, per armour set and damage type. Either may be null for any: a
     * rule for a template and a type beats one for the template, which beats one for the type, which beats one for
     * both.
     */
    public Visuals hurt(String template, String damageType, float majorAt, int throttleFrames) {
        hurtRules.put(hurtKey(template, damageType), new HurtRule(majorAt, Math.max(0, throttleFrames)));
        return this;
    }

    /** The rule for a blow of this type to a thing of this template: see {@link #hurt}. */
    public HurtRule hurtRule(String template, String damageType) {
        for (var key : new String[] {hurtKey(template, damageType), hurtKey(template, null),
            hurtKey(null, damageType), hurtKey(null, null)}) {
            var rule = hurtRules.get(key);
            if (rule != null) {
                return rule;
            }
        }
        return HurtRule.NONE;
    }

    private static String hurtKey(String template, String damageType) {
        return (template == null ? "*" : template) + "|"
                + (damageType == null ? "*" : damageType.toUpperCase(java.util.Locale.ROOT));
    }

    // ---- effect lists ----

    private final Map<String, uz.dukeengine.core.content.EffectList> effectLists = new java.util.LinkedHashMap<>();

    /**
     * The game's effect lists — see {@link uz.dukeengine.core.content.EffectList}: several things at once by one
     * name. Wherever the game names an effect it may name one of these instead, and a name that is a list is
     * played as that list before it is looked for as an effect or a particle system.
     */
    public Visuals effectLists(java.util.Collection<uz.dukeengine.core.content.EffectList> lists) {
        for (var list : lists) {
            if (list != null && list.name() != null) {
                effectLists.put(list.name(), list);
            }
        }
        return this;
    }

    /** The effect list under that name, or {@code null} when the game named none. */
    public uz.dukeengine.core.content.EffectList effectListNamed(String name) {
        return name == null ? null : effectLists.get(name);
    }

    private String seeThroughWord;
    private String glowWord;

    /**
     * The word a thing holds while it is drawn see-through to its own side and allies — the reference's stealth look:
     * its opacity pulsing from its template's faintest ({@link UnitVisual#seeThrough(float)}) to whole and back, a
     * pulse about every 31 of the game's frames, and its blip blinking once a second on their radar. The look alone:
     * who sees it at all is the simulation's ({@code Concealment}).
     */
    public Visuals seeThrough(String word) {
        this.seeThroughWord = word;
        return this;
    }

    /**
     * The word a thing holds while it is drawn as a glow — the reference's heat vision on a thing detected: to
     * everyone but its side and allies a glow instead of its model, to them a glow over its see-through look; whole
     * while it holds the word and fading fast once it does not. A template may say it never glows ({@link
     * UnitVisual#neverGlows()}).
     */
    public Visuals glow(String word) {
        this.glowWord = word;
        return this;
    }

    public String getSeeThroughWord() {
        return seeThroughWord;
    }

    public String getGlowWord() {
        return glowWord;
    }

    private String ownGlowWord;

    /**
     * The word a thing holds while it glows to its own player alone — the reference's stealthy unit that has shown
     * itself by firing or using an ability ({@code StealthUpdate::hintDetectableWhileUnstealthed}), drawn with the heat
     * vision for its controller only, so he knows it can be seen: the glow's light over its model, whole every frame it
     * holds the word and fading as the first word's glow does after. Nobody else sees it.
     */
    public Visuals ownGlow(String word) {
        this.ownGlowWord = word;
        return this;
    }

    public String getOwnGlowWord() {
        return ownGlowWord;
    }

    private final Map<String, uz.dukeengine.core.content.Laser> lasers = new java.util.LinkedHashMap<>();

    /** The game's looks for the beams its simulation owns — see {@link uz.dukeengine.core.content.Laser}. */
    public Visuals lasers(java.util.Collection<uz.dukeengine.core.content.Laser> looks) {
        for (var look : looks) {
            if (look != null && look.name() != null) {
                lasers.put(look.name(), look);
            }
        }
        return this;
    }

    /** The beam look under that name, or {@code null} when the game named none. */
    public uz.dukeengine.core.content.Laser laserNamed(String name) {
        return name == null ? null : lasers.get(name);
    }

    /** Every picture a beam is drawn with, for reading before the match starts. */
    java.util.List<String> laserTextures() {
        return lasers.values().stream().map(uz.dukeengine.core.content.Laser::texture)
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    /** Every model a list throws off, for reading before the match starts. */
    java.util.List<String> debrisModels() {
        return effectLists.values().stream().flatMap(list -> list.entries().stream())
                .filter(uz.dukeengine.core.content.EffectList.Debris.class::isInstance)
                .map(entry -> ((uz.dukeengine.core.content.EffectList.Debris) entry).model())
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    /** Every picture a list marks the ground with, for reading before the match starts. */
    java.util.List<String> scorchPictures() {
        return effectLists.values().stream().flatMap(list -> list.entries().stream())
                .filter(uz.dukeengine.core.content.EffectList.Scorch.class::isInstance)
                .flatMap(entry -> ((uz.dukeengine.core.content.EffectList.Scorch) entry).pictures().stream())
                .distinct().toList();
    }

    /** Every picture a particle system is drawn with, for reading before the match starts. */
    java.util.List<String> particleSystemTextures() {
        return particleSystems.values().stream().map(uz.dukeengine.core.content.ParticleSystem::texture)
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    /**
     * How many particle-system particles may be burning at once, and the priority from which one is never
     * refused. Past the most, making a particle lets go of the oldest of a lower priority first — the reference
     * game's rule. Left unset there is no most, and nothing is let go of.
     */
    public Visuals particleSystemBudget(int most, int neverRefusedFrom) {
        this.particleSystemMost = Math.max(0, most);
        this.particleSystemNeverRefusedFrom = neverRefusedFrom;
        return this;
    }

    public int getParticleSystemMost() {
        return particleSystemMost;
    }

    public int getParticleSystemNeverRefusedFrom() {
        return particleSystemNeverRefusedFrom;
    }

    /**
     * What the client may spend on things in flight.
     *
     * <p>Ceilings rather than targets, and the reason they exist at all is that a
     * fight is not one arrow. Fifty in the air, each with a light of its own, is
     * fifty dynamic lights — and dynamic lights are the expensive kind. Past the
     * ceiling a shot simply flies darker, but the same shot going to the same place.
     *
     * @param lights    how many may burn at once. The terrain shader reads four;
     *     more than that still light the creatures, which is where jME's own
     *     lighting is doing the work
     * @param distance  how far from the camera a thing is still worth the trouble;
     *     zero for no limit
     */
    public record EffectBudget(int lights, float distance) {
    }

    private EffectBudget budget = new EffectBudget(4, 0f);

    public Visuals effectBudget(int lights, float distance) {
        this.budget = new EffectBudget(lights, distance);
        return this;
    }

    public EffectBudget getEffectBudget() {
        return budget;
    }

    /** Every recipe, in the order the game declared them. */
    public java.util.Collection<EffectVisual> allEffects() {
        return java.util.List.copyOf(effects.values());
    }

    /**
     * What a thing looks like: the layers it is drawn from, the pieces of its wearer it
     * lights, and how hard it knocks the camera.
     *
     * <p>Every part may be left out, so a recipe that names only a knock is a recipe —
     * and a game that names no recipe at all draws exactly what it drew before any of
     * this existed.
     */
    public static final class EffectVisual {

        /** Words that name the pieces {@link #glow} lights; see there. */
        final java.util.List<String> glowParts = new java.util.ArrayList<>();
        java.awt.Color glowColour = java.awt.Color.WHITE;
        float shakeSeconds;
        float shakePower;
        /** The layers it is drawn from, in the order the file named them — see {@link EffectLayer}. */
        final java.util.List<EffectLayer> layers = new java.util.ArrayList<>();

        private EffectVisual() {
        }

        /** One more layer, drawn over the ones before it — by {@link LayeredEffects}. */
        public EffectVisual layer(EffectLayer layer) {
            if (layer != null) {
                layers.add(layer);
            }
            return this;
        }

        public java.util.List<EffectLayer> getLayers() {
            return java.util.List.copyOf(layers);
        }

        boolean hasLayers() {
            return !layers.isEmpty();
        }

        /**
         * Pieces of the wearer's model to light from inside — a skeleton's eye sockets, a
         * rune on a door, the coals in a brazier — by a word in their names.
         *
         * <p>The one thing an effect draws that is not a layer, because it is a material on a
         * model rather than something let out into the air — and the cheapest by a distance:
         * one material and no light, which is what makes it affordable on every creature in a
         * room. A word rather than the whole name, because a kit names the same piece
         * differently on every creature it ships — {@code Skeleton_Warrior_Eyes},
         * {@code Skeleton_Mage_Eyes} — and a game should be able to say "the eyes" once.
         */
        public EffectVisual glow(java.util.List<String> parts, java.awt.Color colour) {
            glowParts.clear();
            glowParts.addAll(parts);
            glowColour = colour == null ? java.awt.Color.WHITE : colour;
            return this;
        }

        public java.util.List<String> getGlowParts() {
            return java.util.List.copyOf(glowParts);
        }

        /**
         * How hard the camera is knocked, and for how long.
         *
         * <p>Small numbers. A shake is felt rather than seen, and one that can be
         * SEEN is one the player will ask you to turn off -- so this is a couple of
         * units for a couple of tenths, and zero for every skill that is not
         * supposed to land like a weight.
         */
        public EffectVisual shake(float seconds, float power) {
            this.shakeSeconds = seconds;
            this.shakePower = power;
            return this;
        }

        public float getShakePower() {
            return shakePower;
        }
    }

    /** The configuration for a template (empty defaults if none was set). */
    public UnitVisual of(String templateName) {
        return units.getOrDefault(templateName, defaults);
    }

    /**
     * Hide the map until the player has been there, and open it up around the
     * units built from {@code templateName} — a dungeon crawler's fog rather than
     * an RTS's.
     *
     * <p>An RTS shows the ground and hides what walks on it: the map is a briefing,
     * and the game is about what you cannot see moving across it. A crawler hides
     * the ground too, because there the map <em>is</em> the thing being discovered.
     * The engine only knows the first kind, so this is the client's answer to the
     * second, and games get it only by asking.
     *
     * <p>A template name rather than a distance on purpose. The radius is that
     * template's {@code VisionRange} — the very number the engine's own fog uses to
     * decide which creatures the player can see — so the ground opening up and the
     * monsters appearing are one setting in one file, and cannot be tuned apart.
     */
    public Visuals discoveredBy(String templateName) {
        this.discoveryTemplate = templateName;
        return this;
    }

    /** The template whose vision opens the map, or {@code null} for no discovery. */
    public String getDiscoveryTemplate() {
        return discoveryTemplate;
    }

    private Fog fog = Fog.DEFAULT;

    /**
     * How the dark behaves and what colour it is — see {@link Fog}.
     *
     * <p>Separate from {@link #discoveredBy} because they answer different
     * questions. That one says whose eyes open the map, which the client cannot
     * guess; this one says what the dark is worth, which the client has a
     * perfectly good default for and only a crawler wants to change.
     */
    public Visuals fog(Fog fog) {
        this.fog = fog == null ? Fog.DEFAULT : fog;
        return this;
    }

    public Fog getFog() {
        return fog;
    }

    private Sunlight sunlight = Sunlight.DEFAULT;

    /**
     * Where the light comes from and how much of it there is — see
     * {@link Sunlight}.
     *
     * <p>Not a detail of the art: how far off vertical the sun stands is what
     * decides whether the scene has any relief in it, because a sun straight
     * overhead meets a floor and the top of a wall at the same angle and shades
     * them the same. A game that never asks is lit exactly as the client always
     * lit it.
     *
     * <p><b>A fact about the world, not about the game.</b> A map is a place and a
     * time, and a night map lit at noon is the wrong map. So this may be said
     * again after launch — when a map is chosen, beside {@link #world(String...)}
     * — and it lights the next world built. A game that says it once before
     * launch, as games always have, sees no difference.
     */
    public Visuals sunlight(Sunlight sunlight) {
        this.sunlight = sunlight == null ? Sunlight.DEFAULT : sunlight;
        return this;
    }

    public Sunlight getSunlight() {
        return sunlight;
    }

    /**
     * A light shining one way — see {@link #groundLight} and {@link #thingSuns}: {@code pitch} degrees above the
     * horizon and {@code yaw} round, as a {@link Sunlight}'s, in {@code colour}, packed {@code 0xRRGGBB}.
     */
    public record Sun(float pitch, float yaw, int colour) {

        public Sun {
            pitch = Math.clamp(pitch, 0f, 90f);
        }

        /** The way it travels, down from where it stands. */
        public com.jme3.math.Vector3f direction() {
            return new Sunlight(Math.max(5f, pitch), yaw, 1f, 0f, colour, 0).direction();
        }

        /** Its colour, as the scene takes one. */
        public com.jme3.math.ColorRGBA light() {
            return new com.jme3.math.ColorRGBA((colour >> 16 & 0xFF) / 255f, (colour >> 8 & 0xFF) / 255f,
                    (colour & 0xFF) / 255f, 1f);
        }

        private com.jme3.math.Vector3f exactDirection() {
            // Straight down where it says 90: the Sunlight it borrows its sums from will not stand so high.
            return pitch >= 90f ? new com.jme3.math.Vector3f(0f, -1f, 0f) : direction();
        }
    }

    /**
     * The ground's own lights, apart from those that light things — the reference keeps two sets an hour and lights its
     * ground per corner with its three terrain lights ({@code BaseHeightMapRenderObjClass::doTheLight}): {@code
     * ambient}, packed {@code 0xRRGGBB}, and up to three suns, each by how squarely it meets the ground there, the
     * ground's lean smoothed from the heights round the corner, each colour held to one.
     */
    public record GroundLight(int ambient, java.util.List<Sun> suns) {

        public static final int MOST_SUNS = 3;

        public GroundLight {
            suns = suns == null ? java.util.List.of()
                    : java.util.List.copyOf(suns.subList(0, Math.min(MOST_SUNS, suns.size())));
        }

        /** The light on ground facing {@code normal}, in the client's frame, y up. */
        public com.jme3.math.ColorRGBA at(com.jme3.math.Vector3f normal) {
            float red = (ambient >> 16 & 0xFF) / 255f;
            float green = (ambient >> 8 & 0xFF) / 255f;
            float blue = (ambient & 0xFF) / 255f;
            for (var sun : suns) {
                float square = Math.max(0f, -normal.dot(sun.exactDirection()));
                var light = sun.light();
                red += light.r * square;
                green += light.g * square;
                blue += light.b * square;
            }
            return new com.jme3.math.ColorRGBA(Math.clamp(red, 0f, 1f), Math.clamp(green, 0f, 1f),
                    Math.clamp(blue, 0f, 1f), 1f);
        }
    }

    private GroundLight groundLight;

    /** The ground lit by its own lights rather than the things' — see {@link GroundLight}; null for the things'. */
    public Visuals groundLight(GroundLight light) {
        this.groundLight = light;
        return this;
    }

    public GroundLight getGroundLight() {
        return groundLight;
    }

    /**
     * A picture multiplied over all the ground — see {@link #groundShade}.
     *
     * @param size   how much ground one copy of it covers, in world units
     * @param slideU how far it slides a second, in its own widths; 0 lays it still
     */
    public record GroundShade(String picture, float size, float slideU, float slideV) {

        public GroundShade {
            size = Math.max(0.01f, size);
        }

        /** Where it lies {@code seconds} into the game: one over its size, twice, then how far it has slid. */
        public float[] placeAt(float seconds) {
            float u = slideU * seconds;
            float v = slideV * seconds;
            return new float[] {1f / size, 1f / size, u - (float) Math.floor(u), v - (float) Math.floor(v)};
        }
    }

    private final java.util.List<GroundShade> groundShades = new java.util.ArrayList<>();

    /**
     * A picture multiplied over all the ground by where it lies, still or sliding — the reference's slow cloud shadows
     * ({@code TSCloudMed}, sliding -0.02 and -0.03 a second) and its macro noise ({@code TSNoiseUrb}, a copy every
     * 31.5 cells) ({@code TerrainTex.cpp}, {@code HeightMap.cpp}). Up to two, the second over the first, put away and
     * back by the game while it plays ({@code Duke3D.groundShades}). A whole path from the resource root.
     */
    public Visuals groundShade(String picture, float size, float slideU, float slideV) {
        if (groundShades.size() < 2 && picture != null) {
            groundShades.add(new GroundShade(picture, size, slideU, slideV));
        }
        return this;
    }

    public java.util.List<GroundShade> getGroundShades() {
        return java.util.List.copyOf(groundShades);
    }

    private java.util.List<Sun> thingSuns = java.util.List.of();

    /**
     * Up to two more suns lighting the things beside the {@link Sunlight}'s — the reference's second and third object
     * lights, which its maps carry an hour apart from the ground's. Drawing only.
     */
    public Visuals thingSuns(java.util.List<Sun> suns) {
        this.thingSuns = suns == null ? java.util.List.of()
                : java.util.List.copyOf(suns.subList(0, Math.min(2, suns.size())));
        return this;
    }

    public java.util.List<Sun> getThingSuns() {
        return thingSuns;
    }

    private IconLook iconLook = IconLook.DEFAULT;

    /**
     * Whether the game's icons are white drawings to be tinted, or pictures
     * already painted — see {@link IconLook}.
     *
     * <p>A game that never says gets what the panel always did, which is to
     * colour them: the drawings it was built for were white, and tinting them is
     * how one file serves a slot that is ready, one reloading and one locked.
     */
    public Visuals iconLook(IconLook look) {
        this.iconLook = look == null ? IconLook.DEFAULT : look;
        return this;
    }

    public IconLook getIconLook() {
        return iconLook;
    }

    private StatLook statLook = StatLook.DEFAULT;

    /**
     * How the block of figures and attributes under the experience bar is drawn — see
     * {@link StatLook}. A game that never says gets its default sizes and colours.
     */
    public Visuals statLook(StatLook look) {
        this.statLook = look == null ? StatLook.DEFAULT : look;
        return this;
    }

    public StatLook getStatLook() {
        return statLook;
    }

    private PanelLook panelLook = PanelLook.DEFAULTS;

    /**
     * Which blocks the hero's bar has, in what order, how big its sockets are and every colour it is painted
     * in — see {@link PanelLook}. A game that never says gets the bar as it was designed.
     */
    public Visuals panelLook(PanelLook look) {
        this.panelLook = look == null ? PanelLook.DEFAULTS : look;
        return this;
    }

    public PanelLook getPanelLook() {
        return panelLook;
    }

    /**
     * What the caster is seen doing, and for how long.
     *
     * <p>{@code seconds} is 0 for the clip's own length, or what it should be
     * stretched or hurried to take. A gesture that ends as the spell lands reads
     * as having caused it; the same gesture running a second and a half past
     * reads as somebody waving after the fact.
     */
    public record CastAnim(String clip, float seconds) {
    }

    private final Map<String, CastAnim> castAnims = new HashMap<>();

    /**
     * The gesture that goes with an effect recipe.
     *
     * <p>Keyed by the recipe rather than by the skill, because the recipe is what
     * the client is told about when something is cast -- and because two skills
     * that look the same should move the same. A recipe nobody registers is cast
     * exactly as it always was, which is with an effect and a caster who does not
     * move.
     */
    public Visuals castAnim(String look, String clipName, float seconds) {
        if (look != null && clipName != null && !clipName.isBlank()) {
            castAnims.put(look, new CastAnim(clipName, seconds));
        }
        return this;
    }

    public CastAnim getCastAnim(String look) {
        return look == null ? null : castAnims.get(look);
    }

    private EdgeScroll edgeScroll = EdgeScroll.NONE;

    /**
     * Let the cursor shove the camera when it reaches the edge of the screen —
     * see {@link EdgeScroll}.
     *
     * <p>Asked for rather than assumed, because it is taste rather than
     * correctness: a game that never asks keeps the keys and nothing else, which
     * is what every game had.
     */
    public Visuals edgeScroll(EdgeScroll edgeScroll) {
        this.edgeScroll = edgeScroll == null ? EdgeScroll.NONE : edgeScroll;
        return this;
    }

    public EdgeScroll getEdgeScroll() {
        return edgeScroll;
    }

    private CameraFrame cameraFrame = CameraFrame.NONE;

    /** The player's camera as this game frames it — see {@link CameraFrame}; unsaid, the client's own. */
    public Visuals cameraFrame(CameraFrame frame) {
        this.cameraFrame = frame == null ? CameraFrame.NONE : frame;
        return this;
    }

    public CameraFrame getCameraFrame() {
        return cameraFrame;
    }

    private RightDrag rightDrag = RightDrag.NONE;

    /** The right button held scrolling the view, and letting go only as a click — see {@link RightDrag}. */
    public Visuals rightDrag(RightDrag drag) {
        this.rightDrag = drag == null ? RightDrag.NONE : drag;
        return this;
    }

    public RightDrag getRightDrag() {
        return rightDrag;
    }

    private OrderMark orderMark = OrderMark.DEFAULTS;

    /**
     * How the flash that answers a click should look and move — see
     * {@link OrderMark}.
     *
     * <p>A game that never asks gets the client's own, rather than nothing: an
     * order that leaves no mark reads as a click that missed, so there is no
     * sensible "off" to default to.
     */
    public Visuals orderMark(OrderMark orderMark) {
        this.orderMark = orderMark == null ? OrderMark.DEFAULTS : orderMark;
        return this;
    }

    public OrderMark getOrderMark() {
        return orderMark;
    }

    /** The answers the game names to clicks that give its words — see {@link #orderAnswer}. */
    private final Map<String, String> orderAnswers = new java.util.HashMap<>();

    /**
     * Which moment of the first selected thing answers a click that gives the game's word {@code word} — on a thing
     * ({@code DukeGame.contextOrder}) or on the ground ({@code DukeGame.groundOrder}): {@code supply} plays {@code
     * ordered.supply.<template>}, as the reference answers a dock with {@code VoiceSupply}; null plays nothing, as its
     * power steered says nothing on the click. A word it names nothing for is answered as a move, as always.
     */
    public Visuals orderAnswer(String word, String moment) {
        orderAnswers.put(word, moment == null || moment.isBlank() ? null : moment);
        return this;
    }

    /** The moment that answers a click giving {@code word}: the game's, none (null), or a move's. */
    String orderAnswerFor(String word) {
        return word != null && orderAnswers.containsKey(word) ? orderAnswers.get(word) : "move";
    }

    /**
     * How a thing flashes as it is selected — the reference's {@code Drawable::flashAsSelected}: white, or the colour
     * it is drawn in where {@code ownersColour} ({@code SelectionFlashHouseColor}), saturated as the reference
     * saturates it — each channel times {@code saturation} ({@code SelectionFlashSaturationFactor}) less half of it —
     * added to the thing at once and eased out over {@code frames} of the game's frames. The reference's: 0.5, white,
     * over 4, so +0.25 grey.
     */
    public record SelectionFlashLook(float saturation, boolean ownersColour, int frames) {

        public static final SelectionFlashLook REFERENCE = new SelectionFlashLook(0.5f, false, 4);

        public SelectionFlashLook {
            frames = Math.max(1, frames);
        }

        /** The colour it adds at its height to a thing drawn in {@code owner}'s colour. */
        com.jme3.math.ColorRGBA peakFor(com.jme3.math.ColorRGBA owner) {
            var base = ownersColour && owner != null ? owner : com.jme3.math.ColorRGBA.White;
            float less = saturation / 2f;
            return new com.jme3.math.ColorRGBA(base.r * saturation - less, base.g * saturation - less,
                    base.b * saturation - less, 1f);
        }
    }

    private SelectionFlashLook selectionFlash;

    /** Things flashing as they are selected, and the riders they show with them — see {@link SelectionFlashLook}. */
    public Visuals selectionFlash(SelectionFlashLook look) {
        this.selectionFlash = look;
        return this;
    }

    /** How a thing flashes as it is selected, or null where the game names no flash. */
    public SelectionFlashLook getSelectionFlash() {
        return selectionFlash;
    }

    /** What a click giving one of the game's words is marked with — see {@link #wordMark}. */
    public enum WordMark {
        /** As {@link OrderMark} marks an order the game names: its ring round the thing, or a move's arrowheads. */
        MARK,
        /** The thing it was given on flashed as a thing is when it is selected: the reference's garrison hint. */
        FLASH,
        /** Nothing drawn. */
        NONE
    }

    private final Map<String, WordMark> wordMarks = new java.util.HashMap<>();

    /**
     * How a click that gives the game's word {@code word} is marked — the reference answers an order to enter a thing
     * by flashing the thing entered ({@code InGameUI::createGarrisonHint}), as it flashes when it is selected, with the
     * game's selection flash or else the reference's, and draws nothing for a dock, a repair, a capture or a hijack. A
     * word it names nothing for is marked as {@link OrderMark} marks an order the game names.
     */
    public Visuals wordMark(String word, WordMark mark) {
        wordMarks.put(word, mark == null ? WordMark.MARK : mark);
        return this;
    }

    /** How a click giving {@code word} is marked. */
    WordMark wordMarkFor(String word) {
        return word == null ? WordMark.MARK : wordMarks.getOrDefault(word, WordMark.MARK);
    }

    private float dragDistance = SelectionBox.DRAG_THRESHOLD_PIXELS;

    /**
     * How far, in pixels across or down, a press must move before its release is a box rather than a click — the
     * reference's {@code DragTolerance}, 25 in its Mouse.ini; 5 unless named.
     */
    public Visuals dragDistance(float pixels) {
        this.dragDistance = pixels > 0f ? pixels : SelectionBox.DRAG_THRESHOLD_PIXELS;
        return this;
    }

    public float getDragDistance() {
        return dragDistance;
    }

    private BarColours barColours = BarColours.REFERENCE;

    /** The colours of each plain bar — see {@link UnitBarLook.Plain}; the reference's unless named. */
    public Visuals barColours(BarColours colours) {
        this.barColours = colours == null ? BarColours.REFERENCE : colours;
        return this;
    }

    public BarColours getBarColours() {
        return barColours;
    }

    private RallyLook rally = RallyLook.DEFAULT;

    /** How a selected building's rally point is shown — see {@link RallyLook}; the reference's line unless named. */
    public Visuals rally(RallyLook look) {
        this.rally = look == null ? RallyLook.DEFAULT : look;
        return this;
    }

    public RallyLook getRally() {
        return rally;
    }

    private UnitBarLook unitBars = UnitBarLook.NONE;

    /**
     * What the bar over a creature's head is made of — see {@link UnitBarLook}.
     *
     * <p>Naming none means none is drawn, which is the right answer for the three
     * games that are not this one: a bar divided into lots the client invented
     * would be marks that mean nothing.
     */
    public Visuals unitBars(UnitBarLook look) {
        this.unitBars = look == null ? UnitBarLook.NONE : look;
        return this;
    }

    public UnitBarLook getUnitBars() {
        return unitBars;
    }

    private RangeLook rangeLook = RangeLook.DEFAULT;
    private final Map<Character, SkillRange> skillRanges = new LinkedHashMap<>();

    /**
     * How far each of the game's keys reaches, and what the player is aiming when
     * he presses it — see {@link SkillRange}.
     *
     * <p>The client keeps the press-then-click and knows nothing about skills, and
     * that was enough while all it had to do was forward the click. It is not
     * enough to draw one: "how far does this go" is a number, and the number is
     * the game's. A key the game says nothing about simply gets no ring, which is
     * what every game on this client had.
     */
    public Visuals skillRange(SkillRange range) {
        if (range != null) {
            skillRanges.put(range.key(), range);
        }
        return this;
    }

    public SkillRange getSkillRange(char key) {
        return skillRanges.get(key);
    }

    /** How a reach is drawn — one look for every skill in the game. */
    public Visuals rangeLook(RangeLook rangeLook) {
        this.rangeLook = rangeLook == null ? RangeLook.DEFAULT : rangeLook;
        return this;
    }

    public RangeLook getRangeLook() {
        return rangeLook;
    }

    private HitNumbers hitNumbers = HitNumbers.DEFAULTS;

    /**
     * How the numbers that come off a creature as it is hurt or healed should look
     * — see {@link HitNumbers}.
     *
     * <p>The client knows how to read a health bar's movement and throw a number
     * off it; how long it should stay, how far it should drift and what colour a
     * blow is are the game's, like the fog and the pointer.
     */
    public Visuals hitNumbers(HitNumbers hitNumbers) {
        this.hitNumbers = hitNumbers == null ? HitNumbers.DEFAULTS : hitNumbers;
        return this;
    }

    public HitNumbers getHitNumbers() {
        return hitNumbers;
    }

    /** The row of a health bar a mark is placed from — see {@link MarkPlace}. */
    public enum MarkRow {
        /** Under the bar: the mark's top below its bottom. */
        UNDER,
        /** On the bar's middle row: the mark's top below it. */
        MIDDLE,
        /** Over the bar: the mark's bottom above its top. */
        OVER
    }

    /**
     * Where a mark stands by its thing's health bar: its left edge — or its middle, where {@code centred} — {@code
     * along} of the bar's width from the bar's left end, plus {@code pixels}; and {@code gapShare} of its own height
     * plus {@code gapPixels} from the bar's {@code row}. The reference's rank chevron is {@code (1.1, 1, false, MIDDLE,
     * 0, 1)}, and its enthusiastic mark {@code (0.25, 0, true, UNDER, 0.25, 0)}: a quarter of its own height under
     * the bar.
     */
    public record MarkPlace(float along, float pixels, boolean centred, MarkRow row, float gapShare, float gapPixels) {

        public MarkPlace {
            row = row == null ? MarkRow.UNDER : row;
        }

        /** Its middle {@code along} the bar and its top against the bar's bottom, as every mark stood before. */
        public static MarkPlace under(float along) {
            return new MarkPlace(along, 0f, true, MarkRow.UNDER, 0f, 0f);
        }
    }

    /**
     * A strip of pictures the simulation plays at a point of the world by name ({@code World.strip}) — the reference's
     * {@code Animation2d}: its pictures in order, whole paths from the resource root, each shown {@code frameMillis}
     * of the game's time, round again or held on the last.
     */
    public record Strip(java.util.List<String> frames, int frameMillis, boolean loop) {

        public Strip {
            frames = frames == null ? java.util.List.of() : java.util.List.copyOf(frames);
        }

        /** The picture shown {@code seconds} into the strip. */
        String frameAt(float seconds) {
            if (frames.isEmpty()) {
                return null;
            }
            int step = (int) Math.floor(seconds * 1000f / Math.max(1, frameMillis));
            return frames.get(loop ? Math.floorMod(step, frames.size()) : Math.clamp(step, 0, frames.size() - 1));
        }
    }

    private final java.util.Map<String, Strip> strips = new java.util.HashMap<>();

    /** The strip of pictures played by that name — see {@link Strip}. */
    public Visuals strip(String name, java.util.List<String> frames, int frameMillis, boolean loop) {
        strips.put(name, new Strip(frames, frameMillis, loop));
        return this;
    }

    Strip stripNamed(String name) {
        return name == null ? null : strips.get(name);
    }

    private FloatingTexts.Look floatingText = FloatingTexts.Look.REFERENCE;

    /**
     * How a text floated up from a point of the world moves — see {@code DukeGame.floatText}: {@code rise} pixels a
     * frame of the game, its colour kept {@code hold} frames, then {@code int(k × fade)} of its alpha lost on the k-th
     * frame after. Left alone, the reference's: 1, 10 and 0.1, so an alpha of 230 is gone 82 frames after it appeared.
     */
    public Visuals floatingText(float rise, int hold, float fade) {
        this.floatingText = new FloatingTexts.Look(rise, hold, fade);
        return this;
    }

    FloatingTexts.Look getFloatingText() {
        return floatingText;
    }

    /**
     * How a creature that is hit flashes -- see {@link HitFlash}.
     *
     * @param colour   what it flashes towards, 0xRRGGBB
     * @param seconds  the whole of it: there at once, and fading as a square
     * @param strength how far towards the colour, 0 to 1; 0 is no flash at all
     */
    public record HitFlashLook(int colour, float seconds, float strength) {
        public static final HitFlashLook NONE = new HitFlashLook(0xFFFFFF, 0f, 0f);
    }

    private HitFlashLook hitFlash = HitFlashLook.NONE;

    public Visuals hitFlash(HitFlashLook look) {
        this.hitFlash = look == null ? HitFlashLook.NONE : look;
        return this;
    }

    public HitFlashLook getHitFlash() {
        return hitFlash;
    }

    private float shakeScale = 1f;

    /**
     * How hard every knock of the camera is against what its effect asked for: 1 as
     * written, 0 for a camera that never moves.
     *
     * <p>One number rather than a ShakePower zeroed on every effect, because whether
     * the screen moves is a question about the player, not about any one skill.
     */
    public Visuals shakeScale(float scale) {
        this.shakeScale = Math.max(0f, scale);
        return this;
    }

    public float getShakeScale() {
        return shakeScale;
    }

    private float strikeWithin = 30f;

    /**
     * How near to where a shot was last drawn a blow must land, that same frame, for
     * the shot to have struck -- see {@link Landing#burstAt}.
     */
    public Visuals strikeWithin(float distance) {
        this.strikeWithin = Math.max(0f, distance);
        return this;
    }

    public float getStrikeWithin() {
        return strikeWithin;
    }

    // ---- the run's own moments ----

    /** A level gained. */
    public static final String LEVEL_UP = "LevelUp";
    /** The floor's boss, down. */
    public static final String BOSS_DOWN = "BossDown";
    /** The hero arriving on a floor: a run starting, or a stair taken down. */
    public static final String ARRIVED = "Arrived";

    /** Every moment a game may give a look to -- see {@link #moment}. */
    public static final java.util.Set<String> MOMENTS = java.util.Set.of(LEVEL_UP, BOSS_DOWN,
            ARRIVED);

    /**
     * What one of the run's moments looks like.
     *
     * @param effect the recipe it plays on the hero, by name
     * @param scale  how much bigger than the recipe is written; 1 as written
     */
    public record MomentLook(String effect, float scale) {
    }

    private final Map<String, MomentLook> moments = new java.util.HashMap<>();

    /**
     * Give one of the run's moments a look.
     *
     * <p>The client notices the moment -- a level, the boss down, a new floor -- and
     * the game says what it looks like, which is the division everything else here
     * keeps too. A moment given no look is not drawn.
     *
     * <p>The world's own moments take a look the same way, named as their sounds are:
     * {@code died.<template>} where a thing of that template died, and
     * {@code fired.<weapon>} where a weapon of that name fired — an explosion and a
     * muzzle flash. The effect named may be a particle system; see
     * {@link #particleSystems}.
     */
    public Visuals moment(String name, String effect, float scale) {
        if (name != null && effect != null && !effect.isBlank() && scale > 0f) {
            moments.put(name, new MomentLook(effect, scale));
        }
        return this;
    }

    /**
     * The look given to a moment, or, where none was, to the name it falls back to — the one without its last
     * dotted part, as a sound's cue does: {@code died.Soldier.exploded}, then {@code died.Soldier}, then {@code
     * died}. {@code null} for a moment nothing it falls back to was given a look.
     */
    public MomentLook getMoment(String name) {
        for (var key = name; key != null; key = key.lastIndexOf('.') < 0 ? null
                : key.substring(0, key.lastIndexOf('.'))) {
            var look = moments.get(key);
            if (look != null) {
                return look;
            }
        }
        return null;
    }

    // ---- the portrait ----

    private final Map<String, PortraitLook> portraits = new LinkedHashMap<>();
    private PortraitLook everyPortrait;
    private int portraitFps = 24;

    /**
     * Draw this template <em>alive</em> in the hero panel's frame, rather than as
     * the silhouette that stands there otherwise — see {@link PortraitLook}.
     *
     * <p>Named by the creature's own template, and so repeatable. No art is named
     * here — the model, its scale and the libraries its clips come from are bound
     * once in {@link #unit}, under this same name, and the portrait takes them
     * from there.
     *
     * <p>An override rather than the way in: {@link #portraits} already gives a
     * face to everything the player can select. This is for the creature that
     * wants a different one — a hero who holds his bow ready rather than standing
     * about, and has a flourish for a new level.
     */
    public Visuals portrait(String templateName, PortraitLook look) {
        if (templateName != null && look != null) {
            portraits.put(templateName, look);
        }
        return this;
    }

    /**
     * One portrait for everything the player can select, without naming any of
     * them.
     *
     * <p>The whole of what a game has to do to give every monster in it a face.
     * Nothing about a portrait is per-creature except where the camera stands, and
     * that is already written as fractions of whatever it is looking at — so one
     * block frames a skeleton, a hero and whatever is added next, each by its own
     * measured height. The clips need not be named either: a creature's own idle
     * and death are already bound on it.
     *
     * <p>Naming none leaves every frame to the drawing, which is what every game
     * on this client had.
     */
    public Visuals portraits(PortraitLook look) {
        this.everyPortrait = look;
        return this;
    }

    /**
     * How that template is drawn in the frame — its own, or the one every
     * selectable creature gets, or {@code null} for the silhouette.
     */
    public PortraitLook getPortrait(String templateName) {
        var own = templateName == null ? null : portraits.get(templateName);
        return own != null ? own : everyPortrait;
    }

    /**
     * How many times a second the portrait is worth redrawing.
     *
     * <p>A ceiling rather than a target, and the whole of what a live portrait
     * costs. A hundred and fifty pixels of one creature redrawn on every frame of
     * a game that is drawing a floor of the dungeon is work nobody can see; at a
     * third of that it still breathes, and the two frames in between cost
     * literally nothing, because a viewport that is switched off is skipped before
     * anything in it is touched.
     */
    public Visuals portraitFps(int framesPerSecond) {
        this.portraitFps = Math.clamp(framesPerSecond, 1, 60);
        return this;
    }

    public int getPortraitFps() {
        return portraitFps;
    }

    /**
     * Build the ground from a modular kit rather than from coloured blocks.
     *
     * <p>The same bargain as {@link #discoveredBy}: the client knows how to lay a
     * kit out — floor on open ground, walls on the boundary, posts in the corners
     * — and the game says which kit. A game that never asks keeps the blocks,
     * which is the right picture for a map that is a battlefield rather than a
     * building.
     */
    public Visuals tiles(Tileset tileset) {
        this.tileset = tileset;
        return this;
    }

    /** The kit the ground is built from, or {@code null} for plain blocks. */
    public Tileset getTiles() {
        return tileset;
    }

    // ---- themes ----

    /**
     * One named way a world can look: a kit to build it from, a colour for the
     * dark, and whatever creatures are drawn differently while it lasts.
     *
     * <p>Everything in it is optional. A theme that names only a kit changes only
     * the floor; one that overrides one monster leaves every other creature
     * exactly as the game described it outside any theme.
     */
    public static final class Theme {

        private Tileset tileset;
        private Integer fogTint;
        private final Map<String, UnitVisual> units = new java.util.LinkedHashMap<>();

        private Theme() {
        }

        public Theme tiles(Tileset tileset) {
            this.tileset = tileset;
            return this;
        }

        /** What the dark is coloured while this theme lasts, packed {@code 0xRRGGBB}. */
        public Theme fogTint(int packedRgb) {
            this.fogTint = packedRgb;
            return this;
        }

        /**
         * How one creature is drawn while this theme lasts.
         *
         * <p>A whole replacement, not a patch: a themed creature is described from
         * nothing, so a theme that gives it a model has to give it that model's
         * scale and clips too. Patching would mean a half-described creature
         * wearing one kit's animation names on another kit's skeleton.
         */
        public Theme unit(String templateName, Consumer<UnitVisual> config) {
            var visual = units.computeIfAbsent(templateName, n -> new UnitVisual());
            config.accept(visual);
            return this;
        }

        Tileset getTiles() {
            return tileset;
        }

        Integer getFogTint() {
            return fogTint;
        }

        /** How this theme draws a creature, or {@code null} if it has no opinion. */
        UnitVisual of(String templateName) {
            return units.get(templateName);
        }
    }

    private final Map<String, Theme> themes = new java.util.LinkedHashMap<>();

    /**
     * Register a named theme. Which one is current is the <em>game's</em> to say,
     * frame by frame, through the snapshot's status channel — see
     * {@code DukeRtsApp}. The client only ever asks "which of these, now".
     *
     * <p>Kept out of {@link #tiles} and {@link #fog} on purpose: those are what a
     * game looks like, full stop, and most games have exactly one answer. A theme
     * is for a game whose answer changes as it is played.
     */
    public Visuals theme(String name, Consumer<Theme> config) {
        var theme = themes.computeIfAbsent(name, n -> new Theme());
        config.accept(theme);
        return this;
    }

    /** The theme of that name, or {@code null} — including for a null name. */
    public Theme getTheme(String name) {
        return name == null ? null : themes.get(name);
    }

    /** Whether this game has any themes at all; most do not. */
    public boolean hasThemes() {
        return !themes.isEmpty();
    }

    // ---- menus ----

    private MenuStyle menuStyle = MenuStyle.DEFAULTS;

    /**
     * How this game's menus are lettered — see { MenuStyle}.
     *
     * <p>The same bargain as everywhere else: the client knows how to draw a
     * menu and the game says what it should look like.
     */
    public Visuals menuStyle(MenuStyle style) {
        this.menuStyle = style == null ? MenuStyle.DEFAULTS : style;
        return this;
    }

    public MenuStyle getMenuStyle() {
        return menuStyle;
    }

    private PanelSkin panelSkin = PanelSkin.NONE;

    /**
     * What the hero panel's edges are painted with — see {@link PanelSkin}.
     *
     * <p>The same bargain again, and the same one {@link #menuStyle} strikes about
     * lettering: the client knows where a skill socket goes and how big it is, and
     * the game says what its rim is painted with. Naming nothing leaves the panel
     * carved out of flat colour, which is what it was.
     */
    public Visuals panelSkin(PanelSkin skin) {
        this.panelSkin = skin == null ? PanelSkin.NONE : skin;
        return this;
    }

    public PanelSkin getPanelSkin() {
        return panelSkin;
    }

    private final java.util.Map<String, Cursors.Look> pointers = new java.util.LinkedHashMap<>();

    /**
     * What the mouse pointer looks like in one situation — see {@link Cursors}.
     *
     * <p>The situations are the client's, because what is under the pointer is a
     * fact about the screen; the pictures are the game's, like every other piece
     * of its art. Naming none leaves the system arrow, which is what every game
     * had.
     *
     * @param hotX how far from the left of the picture the tip is
     * @param hotY how far from the top of it — read the way anyone reads a file
     */
    public Visuals pointer(String situation, String assetPath, int hotX, int hotY) {
        return pointer(situation, assetPath, hotX, hotY, 0xFFFFFF);
    }

    /** The same, painted: the drawings are white, so this is what colours them. */
    public Visuals pointer(String situation, String assetPath, int hotX, int hotY, int tint) {
        if (situation != null && assetPath != null && !assetPath.isBlank()) {
            pointers.put(situation, new Cursors.Look(assetPath, hotX, hotY, tint));
        }
        return this;
    }

    /**
     * A pointer that moves: {@code frames} pictures side by side in one file, shown one after another round and
     * round, each for its {@code jiffies} — sixtieths of a second, the reference's {@code Mouse.ini} unit. The hot
     * spot is the same on every picture. The reference's back-and-forth orders are laid out as pictures in turn.
     *
     * <p>Besides {@code Point}, {@code Friend}, {@code Attack}, {@code Aim} and {@code Deny}, the client names {@code
     * Move} — open ground something of his that is selected would walk to — a situation for each order a click on a
     * thing would give, named by the game's word for it ({@code DukeGame.contextOrder}), and {@code Scroll-N},
     * {@code Scroll-NE} … {@code Scroll-NW} while the view is scrolled at the window's edge.
     */
    public Visuals pointer(String situation, String stripPath, int frames, int hotX, int hotY, int[] jiffies) {
        if (situation != null && stripPath != null && !stripPath.isBlank()) {
            var times = new java.util.ArrayList<Integer>();
            for (int jiffy : jiffies == null ? new int[0] : jiffies) {
                times.add(jiffy);
            }
            pointers.put(situation, new Cursors.Look(stripPath, hotX, hotY, 0xFFFFFF, frames, times));
        }
        return this;
    }

    java.util.Map<String, Cursors.Look> getPointers() {
        return java.util.Map.copyOf(pointers);
    }

    /** The pointer pictures, for {@link Preload}: read before the window needs them. */
    public java.util.List<String> pointerImages() {
        return pointers.values().stream().map(Cursors.Look::image).toList();
    }

    // ---- noise ----

    private SoundBank sounds = SoundBank.silent();

    /**
     * What this game sounds like — see {@link SoundBank}.
     *
     * <p>The same bargain as {@link #tiles}: the client raises the moments,
     * because it is the one drawing them, and the game says what each one sounds
     * like, because it is the one that knows. A game that never asks is silent,
     * which is what every game here was.
     */
    public Visuals sounds(SoundBank bank) {
        this.sounds = bank == null ? SoundBank.silent() : bank;
        return this;
    }

    public SoundBank getSounds() {
        return sounds;
    }

    // ---- what all of this adds up to ----

    /**
     * Every look this game can draw: the ones it named, and the ones its themes
     * name on top of them.
     *
     * <p>For {@link Preload}, which needs to know what will be asked for before it
     * is. A themed look is included even though nothing will wear it for another
     * ten floors — that is exactly the one whose file is not read yet when the
     * floor changes.
     */
    java.util.List<UnitVisual> allLooks() {
        var all = new java.util.ArrayList<>(units.values());
        for (var theme : themes.values()) {
            all.addAll(theme.units.values());
        }
        return all;
    }

    /**
     * The looks of these templates only — the game's own and every theme's — in the order they were
     * registered, for a plan that knows which templates a match can draw.
     */
    java.util.List<UnitVisual> looksNamed(java.util.Set<String> templateNames) {
        var named = new java.util.ArrayList<UnitVisual>();
        units.forEach((name, look) -> {
            if (templateNames.contains(name)) {
                named.add(look);
            }
        });
        for (var theme : themes.values()) {
            theme.units.forEach((name, look) -> {
                if (templateNames.contains(name)) {
                    named.add(look);
                }
            });
        }
        return named;
    }

    /** Every template name that has a look anywhere, so a plan can tell whose a sound is. */
    java.util.Set<String> lookNames() {
        var names = new java.util.LinkedHashSet<>(units.keySet());
        for (var theme : themes.values()) {
            names.addAll(theme.units.keySet());
        }
        return names;
    }

    /** Every kit a world may be built from: the game's own, and its themes'. */
    java.util.List<Tileset> allKits() {
        var all = new java.util.ArrayList<Tileset>();
        if (tileset != null) {
            all.add(tileset);
        }
        for (var theme : themes.values()) {
            if (theme.tileset != null) {
                all.add(theme.tileset);
            }
        }
        return all;
    }
}
