package uz.dukeengine.client3d;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.tween.action.BlendableAction;
import com.jme3.animation.AnimControl;
import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * The things of the world as they are drawn, one node each: their bodies as their looks and words choose them, where
 * they stand and how they are turned, the clips they play, what they ride, how they die and lie dead, the rings under
 * the selected and the bars over everybody.
 *
 * <p>Off the snapshot, every frame: what the simulation says a thing is, this draws it as.
 */
final class UnitScene {

    private final AssetManager assetManager;
    private final Visuals visuals;
    private final Bodies bodies;
    private final Looks looks;
    private final GroundMaterials groundLooks;
    private final SceneLight light;
    private final ProjectileEffects effects;
    private final LayeredEffects layered;
    private final Particles particles;
    private final HitFlash hitFlash;
    private final GroundPictures groundPictures;
    private final StealthLook stealthLook;
    private final MissingAssets missing;
    private final FogOfWar fog;
    private final Node rootNode;
    private final Node unitsNode;
    private final Camera cam;
    private final com.jme3.system.Timer timer;
    private final Supplier<DukeGame> game;
    private final Supplier<WorldSnapshot> snapshot;
    /** What is selected, in the order it was selected — the client's, which a thing leaving the world leaves. */
    private final Set<Integer> selected;
    private final WorldMoments.Floor floor;
    /** Whether a critical blow struck a thing this frame. */
    private final IntPredicate critical;
    /** Told each shot that ended this frame, for whether it struck — see {@link Landing}. */
    private final Consumer<Landing.Gone> ended;
    private final Map<Integer, UnitNode> unitNodes = new HashMap<>();
    private final SelectionFlash selectionFlash = new SelectionFlash();
    /** What was selected the frame before: a thing new to the selection flashes. */
    private java.util.Set<Integer> wasSelected = java.util.Set.of();
    /** Every drawn thing's barrels: where its shots come out, its flashes and its kick — see {@link Barrels}. */
    private final Barrels barrels = new Barrels();
    /** Every drawn vehicle's treads and wheels, run as it moves — see {@link RunningGear}. */
    private final RunningGear runningGear = new RunningGear();
    private final TurretBones turretBones = new TurretBones();
    private final DrivenBones drivenBones = new DrivenBones();
    /** The colours added to things by their words — see {@link WordTints}. */
    private final WordTints wordTints = new WordTints();
    /** The colours the simulation flashes things in — see {@link DrawnFlash}. */
    private final DrawnFlash drawnFlash = new DrawnFlash();
    private final Flinch.Clock flinches = new Flinch.Clock();

    UnitScene(AssetManager assetManager, Visuals visuals, Bodies bodies, Looks looks, GroundMaterials groundLooks,
            SceneLight light, ProjectileEffects effects, LayeredEffects layered, Particles particles,
            HitFlash hitFlash, GroundPictures groundPictures, StealthLook stealthLook, MissingAssets missing,
            FogOfWar fog, Node rootNode, Node unitsNode, Camera cam, com.jme3.system.Timer timer,
            Supplier<DukeGame> game, Supplier<WorldSnapshot> snapshot, Set<Integer> selected, WorldMoments.Floor floor,
            IntPredicate critical, Consumer<Landing.Gone> ended) {
        this.assetManager = assetManager;
        this.visuals = visuals;
        this.bodies = bodies;
        this.looks = looks;
        this.groundLooks = groundLooks;
        this.light = light;
        this.effects = effects;
        this.layered = layered;
        this.particles = particles;
        this.hitFlash = hitFlash;
        this.groundPictures = groundPictures;
        this.stealthLook = stealthLook;
        this.missing = missing;
        this.fog = fog;
        this.rootNode = rootNode;
        this.unitsNode = unitsNode;
        this.cam = cam;
        this.timer = timer;
        this.game = game;
        this.snapshot = snapshot;
        this.selected = selected;
        this.floor = floor;
        this.critical = critical;
        this.ended = ended;
    }

    /** The node of a thing drawn now, or null. */
    UnitNode node(int id) {
        return unitNodes.get(id);
    }

    /** Every thing drawn now. */
    Collection<UnitNode> nodes() {
        return unitNodes.values();
    }

    /** What a click on {@code node}'s body picks: a rider the thing it rides on, a ridden mount its rider. */
    UnitNode pickedAs(UnitNode node) {
        if (node.view == null) {
            return null;
        }
        return node.view.ridesOn() >= 0 ? unitNodes.get(node.view.ridesOn())
                : riderOf.getOrDefault(node.view.id(), node);
    }

    /** Every thing drawn again from the next frame, because some of them are drawn differently now. */
    void forgetAll() {
        for (var node : unitNodes.values()) {
            node.root.removeFromParent();
        }
        unitNodes.clear();
        barrels.clear();
        runningGear.clear();
    }

    /** A match over: its things, its dead and their barrels put away. */
    void forgetTheWorld() {
        for (var node : unitNodes.values()) {
            node.root.removeFromParent();
        }
        unitNodes.clear();
        for (var body : dying) {
            body.root().removeFromParent();
        }
        dying.clear();
        barrels.clear();
        runningGear.clear();
    }

    /** A new world: what lay under things, their tints, their stealth and their flashes are the last world's. */
    void newWorld() {
        groundPictures.clear();
        wordTints.clear();
        drawnFlash.clear();
        stealthLook.clear();
        selectionFlash.clear();
    }

    /** A thing died: its barrels, its running gear and its bones are let go. */
    void died(int id) {
        barrels.forget(id);
        runningGear.forget(id);
        turretBones.forget(id);
        drivenBones.forget(id);
    }

    /** A thing's barrel fired from {@code slot}: it flashes and kicks; the bone it fired from, or null. */
    Spatial fire(int shooter, int slot) {
        return barrels.fire(shooter, slot);
    }

    /** The barrels' flashes and kicks eased on. */
    void update(float tpf) {
        barrels.update(tpf);
    }

    /**
     * The bar over each creature that is being drawn.
     *
     * <p>Off the nodes rather than off the snapshot, so that whatever is not on
     * screen has already been left out once: {@link #sync} drops anything
     * behind a wall, and the snapshot itself was built from what the player can
     * see. What is left is filtered again by the edge of the screen inside
     * {@link UnitBars}, which is the one of the three that the camera decides.
     *
     * @param pointed what the pointer is on, for the plain bar; null for nothing
     * @return the floor's half of this frame's status line, read once for the bars and the moments
     */
    UnitBarReading showBars(UnitBars unitBars, UnitNode pointed) {
        var standing = new ArrayList<UnitBars.Standing>(unitNodes.size());
        int mine = game.get().getLocalPlayerIndex();
        var plain = visuals.getUnitBars().plain();
        for (var node : unitNodes.values()) {
            if (node.view == null || node.view.maxHealth() <= 0f || node.view.remembered()
                    || riderOf.containsKey(node.view.id())) {
                continue; // a prop or an arrow, a thing remembered through the fog, or a mount barred as its rider
            }
            var template = game.get().getLogic().getThingFactory().findTemplate(node.view.templateName());
            var shape = template == null ? null : uz.dukeengine.core.thing.Solid.of(template);
            float foot = node.root.getWorldTranslation().y;
            float top = barTop(foot, node.barTop, shape, plain);
            boolean picked = selected.contains(node.view.id()) || pointed != null && pointed.view.id() == node.view.id();
            standing.add(new UnitBars.Standing(node.view, top, foot, node.view.playerIndex() == mine,
                    badgesOf(node), sizeOf(shape), picked));
        }
        var barReading = UnitBarReading.read(snapshot.get().status());
        var eye = cam.getLocation();
        unitBars.update(cam, standing, barReading, eye.y - floor.at(eye.x, eye.z), visuals.getBarColours());
        return barReading;
    }

    /** The words over each thing that is being drawn and has any — {@code GameObject.setCaption}. */
    void showCaptions(Captions captions) {
        var lines = new ArrayList<Captions.Line>();
        for (var node : unitNodes.values()) {
            if (node.view != null && node.view.caption() != null && !node.view.remembered()) {
                var at = node.root.getWorldTranslation();
                lines.add(new Captions.Line(node.view.id(), node.view.caption(),
                        new Vector3f(at.x, at.y + node.barTop, at.z)));
            }
        }
        captions.update(cam, lines);
    }

    /** The light each thing that is being drawn carries, by the words it holds — {@code UnitVisual.light}. */
    void showLights(UnitLights lights) {
        var lines = new ArrayList<UnitLights.Line>();
        for (var node : unitNodes.values()) {
            var view = node.view;
            if (view == null || view.remembered()) {
                continue;
            }
            var visual = looks.visualFor(view);
            if (visual.wordLights.isEmpty()) {
                continue;
            }
            var light = visual.lightFor(visual.holding(view.healthFraction(), visuals.getWorldConditions(),
                    view.conditions()));
            if (light != null) {
                lines.add(new UnitLights.Line(view.id(), light, node.root.getWorldTranslation()));
            }
        }
        lights.update(lines, timer.getTimeInSeconds());
    }

    /** The rope each thing that is being drawn lets down from a bone of its model — {@code UnitVisual.rope}. */
    void showRopes(Ropes ropes) {
        var lines = new ArrayList<Ropes.Line>();
        for (var node : unitNodes.values()) {
            var view = node.view;
            if (view == null || view.remembered()) {
                continue;
            }
            var visual = looks.visualFor(view);
            if (visual.wordRopes.isEmpty()) {
                continue;
            }
            var rope = visual.ropeFor(visual.holding(view.healthFraction(), visuals.getWorldConditions(),
                    view.conditions()));
            var bone = rope == null ? null : boneOf(node, null, rope.bone());
            if (bone != null) {
                lines.add(new Ropes.Line(view.id(), rope, bone.getWorldTranslation().clone()));
            }
        }
        ropes.update(lines, floor);
    }

    /**
     * The world height a thing's bar floats at: over its feet where they stand this frame — up a hill, in a jump — by
     * its model's height, or by its shape's and the plain bar's lift.
     */
    static float barTop(float foot, float overFoot, uz.dukeengine.core.thing.Geometry shape, UnitBarLook.Plain plain) {
        return foot + (plain == null ? overFoot : topOf(shape) + plain.lift());
    }

    /** How far over its position a thing's geometry reaches: a box's or cylinder's height, a sphere's radius. */
    static float topOf(uz.dukeengine.core.thing.Geometry shape) {
        return switch (shape) {
            case uz.dukeengine.core.thing.Geometry.Box box -> box.height();
            case uz.dukeengine.core.thing.Geometry.Cylinder cylinder -> cylinder.height();
            case uz.dukeengine.core.thing.Geometry.Sphere sphere -> sphere.radius();
            case null -> 0f;
        };
    }

    /** A thing's two radii added, as the reference sizes a bar by them: a sphere's or cylinder's radius twice. */
    static float sizeOf(uz.dukeengine.core.thing.Geometry shape) {
        return switch (shape) {
            case uz.dukeengine.core.thing.Geometry.Box box -> box.majorRadius() + box.minorRadius();
            case uz.dukeengine.core.thing.Geometry.Cylinder cylinder -> 2f * cylinder.radius();
            case uz.dukeengine.core.thing.Geometry.Sphere sphere -> 2f * sphere.radius();
            case null -> 0f;
        };
    }

    /**
     * The marks a thing's words put by its bar, each at the picture its strip is on now: played from its first picture
     * as its words come, or from one drawn at random where the mark says so.
     */
    private java.util.List<UnitBars.Badge> badgesOf(UnitNode node) {
        var view = node.view;
        var look = looks.visualFor(view);
        if (look.marks.isEmpty()) {
            return java.util.List.of();
        }
        var shown = look.marksFor(look.holding(view.healthFraction(), visuals.getWorldConditions(), view.conditions()));
        node.markSince.keySet().retainAll(shown);
        float now = timer.getTimeInSeconds();
        var badges = new java.util.ArrayList<UnitBars.Badge>(shown.size());
        for (int index : shown) {
            var mark = look.marks.get(index);
            float since = node.markSince.computeIfAbsent(index,
                    _ -> now - mark.startSeconds(java.util.concurrent.ThreadLocalRandom.current()));
            badges.add(new UnitBars.Badge(mark.frameAt(now - since), mark.scale(), mark.place()));
        }
        return badges;
    }

    /** Every thing in this frame's snapshot drawn, and every thing no longer in it taken away. */
    void sync() {
        var snapshot = this.snapshot.get();
        var game = this.game.get();
        var seen = new HashSet<Integer>();
        lyingDead.clear();
        for (var view : snapshot.units()) {
            if (outOfSight(view)) {
                continue; // behind a wall: not drawn, and taken away if it was
            }
            if (!looks.visualFor(view).shownTo(view.hostile())) {
                selected.remove(view.id());
                continue; // kept from this viewer's side: not drawn either, as behind a wall
            }
            var corpse = corpseOf(view.id());
            if (corpse != null) {
                // Dead, and kept in the world while its death plays out: drawn where the world says it is — sinking,
                // if it sinks — and as its words and health say, and nothing of it gets up again.
                lookDead(corpse.node(), view);
                lyingDead.add(view.id());
                continue;
            }
            seen.add(view.id());
            boolean isNew = !unitNodes.containsKey(view.id());
            var node = unitNodes.computeIfAbsent(view.id(), _ -> createUnitNode(view));
            updateUnitNode(node, view);
            var look = looks.visualFor(view);
            if (isNew) {
                layered.flying(view.id(), look.effect, node.root,
                        new Vector3f(look.effectForward, look.yOffset, 0f), cam);
            }
        }
        var gone = unitNodes.entrySet().iterator();
        while (gone.hasNext()) {
            var entry = gone.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            // Its layers are grounded whether it arrived or merely walked out of the
            // light -- what it did on the way down is Landing's to say.
            layered.grounded(entry.getKey());
            barrels.forget(entry.getKey());
            runningGear.forget(entry.getKey());
            turretBones.forget(entry.getKey());
            drivenBones.forget(entry.getKey());
            groundPictures.forget(entry.getKey());
            wordTints.forget(entry.getKey());
            drawnFlash.forget(entry.getKey());
            stealthLook.forget(entry.getKey());
            selectionFlash.forget(entry.getKey());
            var node = entry.getValue();
            var at = node.root.getLocalTranslation();
            if (Landing.arrived(node.view, game.getLocalPlayerIndex(),
                    fog.canSee(at.x, at.z))) {
                // ★ Here, and not on ObjectDied. An arrow, a fireball and the mark a
                // meteor falls on have no body, and the core removes a thing with no
                // body without ever posting that it died. Gone is the moment it
                // ended; whether it ended in a strike is only known once this
                // frame's blows are read -- see burstWhatStruck.
                ended.accept(new Landing.Gone(entry.getKey(),
                        looks.visualFor(node.view).effect,
                        node.view.playerIndex() == game.getLocalPlayerIndex(),
                        node.bornX, node.bornZ, node.bornAt, at.x, at.z,
                        timer.getTimeInSeconds()));
            }
            node.root.removeFromParent();
            selected.remove(entry.getKey());
            gone.remove();
        }
    }

    /**
     * Whether something is standing where the player cannot see it.
     *
     * <p>The engine's fog is a circle and does not know about walls, so a monster
     * in the next room is in the snapshot and would be drawn standing in the dark.
     * Discovery already knows which cells he can see into; this asks it the same
     * question about a creature.
     *
     * <p>His own things are always drawn — they are his, and an arrow of his own
     * that vanished mid-flight would be a bug rather than a fog.
     */
    /** Whether a click or a box may take this thing: selectable, with a shape, and shown to this viewer. */
    boolean pickable(UnitView view) {
        return looks.visualFor(view).pickableBy(view.selectable(), view.hostile());
    }

    private boolean outOfSight(UnitView view) {
        return view.playerIndex() != game.get().getLocalPlayerIndex() && !fog.canSee(view.x(), view.y());
    }

    /**
     * One of theirs was hit: it goes white for an instant, from its own colour and
     * back to it. A blow that killed it finds nothing here -- the body was laid out
     * when the death was read, and a corpse has nothing left to flinch with.
     */
    void struck(int unitId) {
        var node = unitNodes.get(unitId);
        if (node == null || node.view == null) {
            return;
        }
        var tint = looks.visualFor(node.view).tint;
        var own = (tint == null ? ColorRGBA.White : Materials.colour(tint)).mult(Bodies.CREATURE_AMBIENT);
        hitFlash.struck(unitId, node.root, own);
    }

    /**
     * Every flash eased on by the game's time, and a thing new to the selection flashed as the game names — the
     * reference's {@code Drawable::onSelected} — with the riders it shows.
     */
    void flashTheNewlySelected(float tpf) {
        selectionFlash.update(tpf / Particles.FRAME_SECONDS);
        var look = visuals.getSelectionFlash();
        if (look != null) {
            for (int id : selected) {
                if (!wasSelected.contains(id)) {
                    flash(id, look);
                }
            }
        }
        wasSelected = java.util.Set.copyOf(selected);
    }

    /** A thing flashed as {@code look} says, and what rides on it with it — {@code clientVisibleContainedFlash...}. */
    void flash(int id, Visuals.SelectionFlashLook look) {
        var node = unitNodes.get(id);
        if (node == null || node.view == null) {
            return;
        }
        selectionFlash.flash(id, node.root, look, Materials.colour(game.get().getColor(node.view.wears())));
        for (var rider : unitNodes.values()) {
            if (rider.view != null && rider.view.ridesOn() == id) {
                selectionFlash.flash(rider.view.id(), rider.root, look,
                        Materials.colour(game.get().getColor(rider.view.wears())));
            }
        }
    }

    /**
     * A body playing out its death, and when to take it away.
     */
    private record Dying(int id, UnitNode node, float until) {

        Node root() {
            return node.root;
        }
    }

    /** The bodies the world still keeps, dead, this frame: taken away only once it lets them go. */
    private final java.util.Set<Integer> lyingDead = new java.util.HashSet<>();

    /** The body playing out {@code id}'s death, or null. */
    private Dying corpseOf(int id) {
        for (var body : dying) {
            if (body.id() == id) {
                return body;
            }
        }
        return null;
    }

    private final java.util.List<Dying> dying = new java.util.ArrayList<>();

    /**
     * Let something that has just died fall over before it goes.
     *
     * <p>Driven by the death <em>event</em> rather than by a unit leaving the
     * snapshot, because those are not the same thing: with fog, a monster walking
     * out of sight leaves the snapshot too, and a corpse dropped every time
     * something rounded a corner would be worse than none at all.
     *
     * <p>The body is taken out of the live units at once — it is no longer part of
     * the game, cannot be selected, and must not be given a walk animation because
     * the simulation says it is moving. What is left is a clip and a timer.
     */
    void layOut(int unitId, uz.dukeengine.core.module.DeathType deathType, int frame) {
        var node = unitNodes.remove(unitId);
        selected.remove(unitId);
        if (node == null) {
            return;
        }
        var clipName = looks.visualFor(node.view).dieAnimFor(deathType, unitId, frame);
        var clip = clipName == null || node.composer == null
                ? null : node.composer.getAnimClip(clipName);
        node.diedOn = frame;
        if (clip == null) {
            // Nothing to play: it goes — once the world lets it go, if it keeps the thing a while dead.
            node.ring.removeFromParent();
            dying.add(new Dying(unitId, node, timer.getTimeInSeconds()));
            return;
        }
        node.deathClip = clipName;
        // The trappings of something alive. The bar over its head goes without
        // being told: it is drawn from the snapshot, and a corpse is not in one.
        node.ring.removeFromParent();

        // Once through, not looping: a corpse that gets up and dies again forever
        // is worse than one that never fell over.
        node.composer.setCurrentAction(clipName, AnimComposer.DEFAULT_LAYER, false);
        node.currentAnim = clipName;
        dying.add(new Dying(unitId, node,
                (float) (timer.getTimeInSeconds() + clip.getLength() + CORPSE_LINGER)));
    }

    /**
     * How long a body stays after its death animation has played out.
     */
    private static final float CORPSE_LINGER = 1.5f;

    /** A body the world brought back to life: let go, so the next sync draws the thing afresh, whole and alive. */
    void raise(int unitId) {
        var corpse = corpseOf(unitId);
        if (corpse == null) {
            return;
        }
        dying.remove(corpse);
        lyingDead.remove(unitId);
        corpse.root().removeFromParent();
        groundPictures.forget(unitId);
        wordTints.forget(unitId);
    }

    void reapTheDead() {
        float now = (float) timer.getTimeInSeconds();
        dying.removeIf(body -> {
            if (now < body.until() || lyingDead.contains(body.id())) {
                return false; // still playing, or still kept in the world
            }
            body.root().removeFromParent();
            groundPictures.forget(body.id());
            wordTints.forget(body.id());
            drawnFlash.forget(body.id());
            return true;
        });
    }

    /**
     * A thing the world keeps dead, drawn by the words it holds and its health as a living thing is — the reference
     * goes on drawing a dead thing by its condition: a soldier flung by a blast flailing and then landing, a building
     * that keeps its ruin drawn as its ruin from its death. Its model, its pieces, its layers and the clips between
     * their looks, its tints as their words go; its clip what its words choose, else its death lain in; and what lay
     * under it fading out, as the reference's decal does at a death.
     */
    private void lookDead(UnitNode node, UnitView view) {
        node.view = view;
        var visual = looks.visualFor(view);
        var world = visuals.getWorldConditions();
        var holding = visual.holding(view.healthFraction(), world, view.conditions());
        var wanted = visual.modelFor(view.healthFraction(), world, view.conditions());
        if (node.body != null && wanted != null && !wanted.equals(node.modelPath)) {
            swapBody(node, view, visual, wanted);
        }
        if (visual.choosesByWords()) {
            wearTheWords(node, visual, holding);
        }
        if (!visual.boneParticles.isEmpty() && node.boneSystems != null) {
            node.boneSystems.choose(visual.particlesFor(holding), node.body);
        }
        node.root.setLocalTranslation(UnitPlacement.where(view, floor::at));
        node.root.setLocalRotation(UnitPlacement.turn(view));
        float frames = timer.getTimePerFrame() / Particles.FRAME_SECONDS;
        if (!visual.groundPictures.isEmpty()) {
            groundPictures.see(view.id(), visual, null, new Coord3D(view.x(), view.y(), 0f), view.orientation(),
                    frames, floor::at);
        }
        if (!visual.wordTints.isEmpty()) {
            wordTints.see(view.id(), node.root, visual, holding, frames);
        }
        for (var layer : node.layers) {
            layer.wear(node.root, view, world, snapshot.get().frame(), body -> paintHouseColour(body, view),
                    clip -> missing.warn(view.templateName() + "/" + clip, "animation"),
                    bone -> boneOf(node, layer, bone));
        }
        if (node.composer != null) {
            node.currentAnim = WordClip.playDead(node.composer, node.wordClip, node.currentAnim, visual, holding,
                    snapshot.get().frame(), view.id(), node.deathClip, node.diedOn,
                    clip -> missing.warn(view.templateName() + "/" + clip, "animation"));
        }
    }

    private UnitNode createUnitNode(UnitView view) {
        var visual = looks.visualFor(view);
        var node = new UnitNode();
        node.root = new Node("unit-" + view.id());
        node.bornX = view.x();
        node.bornZ = view.y();
        node.bornAt = timer.getTimeInSeconds();
        node.root.setUserData("unitId", view.id());
        node.root.setUserData("wears", view.wears()); // for what it throws once it is gone from the snapshot

        node.modelPath = visual.modelFor(view.healthFraction(), visuals.getWorldConditions(), view.conditions());
        Spatial body = visual.shapeless ? new Node("no-shape") : bodies.buildBody(visual, java.util.List.of(), node.modelPath);
        if (body != null && visual.line != null && view.span() != null) {
            body = layAlong(body, view, visual, node);
        }
        if (body != null) {
            paintHouseColour(body, view);
            takeUpBody(node, body, visual);
        }
        if (body == null) {
            // A fireball has no model and should not be given the capsule-with-a-
            // gun-barrel every other modelless thing gets. Its effect is its body.
            // It comes back already standing where its own fire burns from, and
            // at the width the recipe asked for: the scale on this visual is for
            // the plain shape a modelless thing falls back to, and an orb is
            // given its size in world units.
            body = effects.bodyFor(visual);
        }
        if (body == null) {
            body = buildPrimitive(view);
            // Size applies to a shape as much as to a model. Without this a game
            // could say how big a thing is only by shipping art for it, and
            // anything small — a dart, a spark, a rat — came out unit-sized.
            body.setLocalScale(visual.scale);
            body.setLocalTranslation(0, visual.yOffset, 0);
        }
        node.body = body;
        node.root.attachChild(body);

        node.boneSystems = new BoneSystems(particles, rootNode);
        for (var look : visual.layers.values()) {
            node.layers.add(new ModelLayer(look, path -> bodies.buildBody(look, java.util.List.of(), path),
                    new BoneSystems(particles, rootNode)));
        }
        node.ring = buildSelectionRing(view);
        node.root.attachChild(node.ring);
        node.barTop = heightOf(body, view) + visuals.getUnitBars().lift();

        unitsNode.attachChild(node.root);
        return node;
    }

    /** Whatever a node has to know about the body it is drawn with: how it is animated, and how tall it is. */
    private void takeUpBody(UnitNode node, Spatial body, Visuals.UnitVisual visual) {
        node.composer = Bodies.findControl(body, AnimComposer.class);
        node.legacyChannel = null;
        var legacy = Bodies.findControl(body, AnimControl.class);
        if (node.composer == null && legacy != null) {
            node.legacyChannel = legacy.createChannel();
        }
        snap(node.composer, visual.attackAnim);
        snap(node.composer, visual.hurtAnim);
        node.bodyTop = visual.risesAsBuilt ? UnitPlacement.riseOf(visual, body) : 0f;
        // Its barrels are the model's, so a new model is found again: a wreck may have none.
        Integer id = node.root.getUserData("unitId");
        if (id != null) {
            barrels.dress(id, body, node.barrelBones != null ? node.barrelBones : visual.weaponBones, visual.recoil);
            node.pieces.applyTo(body, barrels.flashes(id));
            runningGear.dress(id, body, visual);
            node.turretLooks = null; // a new model: its turrets found on it again
        }
    }

    /**
     * Its barrels and its pieces as the words it holds choose them: the best-fitting set's fire bones, flashes and
     * recoil bones, found on the model again when the choice changes, and its pieces hidden and shown as its states
     * leave them — see {@link Visuals.UnitVisual#pieces}. A barrel's flash is left to the barrel.
     */
    private void wearTheWords(UnitNode node, Visuals.UnitVisual visual, java.util.Set<String> holding) {
        Integer id = node.root.getUserData("unitId");
        if (id == null || node.body == null) {
            return;
        }
        var bones = visual.weaponBonesFor(holding);
        boolean redressed = bones != node.barrelBones;
        if (redressed) {
            node.barrelBones = bones;
            barrels.dress(id, node.body, bones, visual.recoil);
        }
        if (node.pieces.choose(visual.pieceStateFor(holding), visual.pieceStates) || redressed) {
            node.pieces.applyTo(node.body, barrels.flashes(id));
        }
        var turrets = visual.turretsFor(holding);
        if (turrets != node.turretLooks) {
            node.turretLooks = turrets;
            turretBones.dress(id, node.body, turrets);
        }
    }

    /**
     * Draw a thing with a different model than the one it has been wearing, without it moving.
     *
     * <p>A building crossing a health threshold stops being whole and starts being wrecked, and the thing
     * on screen has to become the other model where it stands. What holds its place is its own node — the
     * translation and the rotation live there and the body hangs under it — so the swap is a child
     * exchanged, and where it stands and which way it faces are never touched.
     *
     * <p>Everything measured off the body is measured again, because the new one is a different shape: the
     * height its bar floats at, and whatever animation controls the new file happens to carry. A swapped
     * model is usually a different skeleton, so the clip that was playing carries over <b>by name</b> where
     * the new one has a clip of that name; where it has not, the state machine is let choose again next
     * frame, which is a thing standing rather than a thing frozen.
     *
     * <p>A file that will not load leaves it wearing what it had. Half a wrecked building is better than
     * none of one.
     */
    private void swapBody(UnitNode node, UnitView view, Visuals.UnitVisual visual, String wanted) {
        stealthLook.forget(view.id()); // what it laid over the old body goes with it
        var body = bodies.buildBody(visual, java.util.List.of(), wanted);
        if (body != null && visual.line != null && view.span() != null) {
            body = layAlong(body, view, visual, node);
        }
        if (body == null) {
            node.modelPath = wanted; // it will not load; do not try again every frame
            return;
        }
        paintHouseColour(body, view); // a wrecked barracks is still his
        var playing = node.currentAnim;
        if (node.body != null) {
            node.body.removeFromParent();
        }
        node.modelPath = wanted;
        node.body = body;
        node.root.attachChild(body);
        takeUpBody(node, body, visual);
        node.barTop = heightOf(body, view) + visuals.getUnitBars().lift();
        // Cleared first either way: what it says it is playing has to be true of the model it is on.
        node.currentAnim = "";
        node.actionUntil = 0f;
        if (playing != null && !playing.isEmpty() && node.composer != null
                && node.composer.getAnimClip(playing) != null) {
            play(node, view.looksAs(), playing, true);
        }
    }

    /**
     * A model laid along its thing's line as its look says ({@link LinePieces}), in place of the model at its place,
     * and fogged as the ground under it is, since it lies where the ground does.
     */
    private Spatial layAlong(Spatial model, UnitView view, Visuals.UnitVisual visual, UnitNode node) {
        var laid = LinePieces.lay(model, visual.line, view.span().from(), view.span().to());
        if (groundLooks.fogs()) {
            laid.depthFirstTraversal(spatial -> {
                if (spatial instanceof Geometry geometry) {
                    geometry.setMaterial(groundLooks.plainFogged(visual.tint == null ? ColorRGBA.White
                            : Materials.colour(visual.tint), Bodies.skinOf(geometry.getMaterial())));
                }
            });
        }
        node.laidSpan = view.span();
        return laid;
    }

    /**
     * Paint the parts of a body its art marked for its owner in its owner's colour.
     *
     * <p>Laid over what is there, never instead of it: the material's own colour and its own picture are
     * multiplied by the owner's, so the shading painted into a house-colour mesh — which is grey for
     * exactly this reason — comes through. The shadowed side too, or a red army goes grey in the shade.
     *
     * <p>After the body is built rather than inside {@link Bodies#buildBody}, because a body does not know whose
     * it is — the same file serves both sides, and the portrait builds one that belongs to nobody. So it is
     * done where the owner is known: when a unit arrives, and again when its model is swapped for a
     * condition, because a wrecked barracks is still his.
     */
    private void paintHouseColour(Spatial body, UnitView view) {
        var prefix = visuals.getHouseColour();
        if (prefix == null || body == null || !looks.visualFor(view).ownerColoured) {
            return; // a game that names none, or a look that says no, is drawn as its file is
        }
        Bodies.paintOwner(body, prefix, Materials.colour(game.get().getColor(view.wears()))); // a disguise's to those it fools
    }

    /**
     * How long the old clip is faded out under a new one, on the legacy path.
     */
    private static final float BLEND_SECONDS = 0.2f;

    /**
     * How long a <em>blow</em> is allowed to fade in over what came before.
     *
     * <p>jME cross-fades into every clip and gives it four tenths of a second to
     * do it, which is right for settling from a walk into a stand and quite wrong
     * for a punch: the clip is one second long, so nearly half of it was spent
     * arriving, and the strike had no snap in it at all. A blow is a moment and
     * has to start on the frame it starts.
     */
    private static final float SNAP_SECONDS = 0.08f;

    /**
     * Let this clip start at once rather than easing in over the default fade.
     */
    private static void snap(AnimComposer composer, String clipName) {
        if (composer == null || clipName == null || composer.getAnimClip(clipName) == null) {
            return;
        }
        if (composer.action(clipName) instanceof BlendableAction blendable) {
            blendable.setTransitionLength(SNAP_SECONDS);
        }
    }

    /**
     * What colour to draw this unit: the type's own if the game gave it one,
     * otherwise its player's.
     *
     * <p>Player colour says whose it is, which is all an RTS usually needs. A
     * game fielding several kinds of thing per side needs to say what it is too,
     * and with no models yet there is nothing else to say it with.
     */
    ColorRGBA colourOf(UnitView view) {
        var own = looks.visualFor(view).colour;
        return Materials.colour(own != null ? own : game.get().getColor(view.wears()));
    }

    /**
     * A clean placeholder in the player's colour when no model is assigned.
     */
    private Spatial buildPrimitive(UnitView view) {
        var color = colourOf(view);
        var group = new Node("primitive");
        if (view.structure()) {
            var box = new Geometry("b", new Box(2.6f, 1.8f, 2.6f));
            box.setMaterial(Materials.lit(assetManager, color));
            box.setLocalTranslation(0, 1.8f, 0);
            group.attachChild(box);
        } else {
            var hull = new Geometry("h", new Cylinder(2, 16, 1.1f, 2.4f, true));
            hull.setMaterial(Materials.lit(assetManager, color));
            hull.rotate(FastMath.HALF_PI, 0, 0);
            hull.setLocalTranslation(0, 1.2f, 0);
            group.attachChild(hull);
            var barrel = new Geometry("barrel", new Box(1.1f, 0.18f, 0.18f));
            barrel.setMaterial(Materials.lit(assetManager, color.mult(1.4f)));
            barrel.setLocalTranslation(1.2f, 1.4f, 0);
            group.attachChild(barrel);
        }
        return group;
    }

    /**
     * The ring under a selected unit — green for his own, red for anything else.
     *
     * <p>Not decoration. Something that is not his can be selected, because that
     * is how its health and its damage are read off the bar, and it cannot be
     * ordered. A green ring under a skeleton would promise a command that the
     * next right-click is not going to give.
     */
    private Geometry buildSelectionRing(UnitView view) {
        var ring = new Geometry("ring", new com.jme3.scene.Mesh());
        ring.setMaterial(Materials.unshaded(assetManager, view.playerIndex() == game.get().getLocalPlayerIndex()
                ? new ColorRGBA(0.4f, 1f, 0.4f, 1f) : new ColorRGBA(1f, 0.36f, 0.3f, 1f)));
        ring.setCullHint(Spatial.CullHint.Always);
        return ring;
    }

    /** How far over the ground the ring under a selected thing lies. */
    private static final float RING_LIFT = 0.06f;

    /**
     * The ring under a selected thing laid over the ground under it, where a flat disc under its feet put the half on
     * a slope that climbs under the hill: turned back against its thing's own turn, pitch and sway, so it lies as the
     * ground does however the thing stands, and on the ground under a thing in the air.
     */
    private void layRing(UnitNode node, UnitView view) {
        var root = node.root.getLocalTranslation();
        node.ring.setLocalRotation(node.root.getLocalRotation().inverse());
        node.ringDisc.lay(node.ring.getMesh(), new Coord3D(root.x, root.z, 0f), view.structure() ? 4.2f : 2.2f,
                root.y - RING_LIFT, floor::at);
        node.ring.updateModelBound();
    }

    /**
     * How tall this unit stands, measured rather than assumed.
     *
     * <p>A model's height depends on the kit it came from and the scale the game
     * gave it, neither of which the client can guess. The fallback is the old
     * constant, for the primitives that have no bounds worth measuring.
     */
    private static float heightOf(Spatial body, UnitView view) {
        float fallback = view.structure() ? 6.5f : 4.5f;
        if (body == null) {
            return fallback;
        }
        body.updateModelBound();
        body.updateGeometricState();
        return body.getWorldBound() instanceof com.jme3.bounding.BoundingBox box
                ? Math.max(fallback, box.getYExtent() * 2f) : fallback;
    }

    private void updateUnitNode(UnitNode node, UnitView view) {
        boolean handedOver = node.view != null && node.view.playerIndex() != view.playerIndex();
        boolean recoloured = node.view != null && node.view.wears() != view.wears();
        node.view = view;
        if (handedOver || recoloured) {
            // Its owner changed, however it did, or the colours it wears to this viewer: drawn in the new ones.
            node.root.setUserData("wears", view.wears());
            paintHouseColour(node.body, view);
            node.layers.stream().filter(ModelLayer::ownerColoured)
                    .forEach(layer -> paintHouseColour(layer.body(), view));
        }
        if (handedOver) {
            selected.remove(view.id()); // out of the old owner's hands
        }
        if (!view.selectable()) {
            selected.remove(view.id()); // made unselectable, or sold: out of the selection the frame it is
        }
        // What it looks like can change while it stands there: a building past a health threshold is a
        // wrecked building, and the wreck is a different file. Asked every frame because the answer is a
        // lookup against a map that is empty for every template that named no second model.
        var visual = looks.visualFor(view);
        // Its own words too — an upgrade's weapon set, a rank — for a look that chooses anything by them.
        var holding = visual.choosesByWords()
                ? visual.holding(view.healthFraction(), visuals.getWorldConditions(), view.conditions()) : null;
        var wanted = visual.modelFor(view.healthFraction(), visuals.getWorldConditions(), view.conditions());
        if (node.body != null && wanted != null && !wanted.equals(node.modelPath)) {
            swapBody(node, view, visual, wanted);
        } else if (visual.line != null && view.span() != null && !view.span().equals(node.laidSpan)) {
            swapBody(node, view, visual, node.modelPath); // its line moved: laid again along the new one
        }
        if (holding != null) {
            wearTheWords(node, visual, holding);
        }
        if (!visual.boneParticles.isEmpty() && node.boneSystems != null) {
            node.boneSystems.choose(visual.particlesFor(holding != null ? holding
                    : visual.holding(view.healthFraction(), visuals.getWorldConditions(), view.conditions())),
                    node.body);
            node.boneSystems.drive(view, timer.getTimePerFrame() / Particles.FRAME_SECONDS);
        }
        node.root.setLocalTranslation(UnitPlacement.where(view, floor::at));
        node.root.setLocalRotation(UnitPlacement.turn(view));
        node.root.setLocalScale(UnitPlacement.scale(view));
        node.litBy = light.scales().dress(node.root, node.litBy, visual.lightGroup);
        drawnFlash.see(view.id(), node.root, view.flash());
        if (visual.sway != null && view.pitch() == 0f && view.roll() == 0f) {
            node.root.setLocalRotation(visual.sway.tiltAt(view.id(), snapshot.get().gameTimeSeconds())
                    .mult(node.root.getLocalRotation()));
        }
        if (node.laidSpan != null && node.body != null) {
            // The line is the world's: where it starts and which way it runs, taken into the thing's own frame.
            var start = new Vector3f(node.laidSpan.from().x(), node.laidSpan.from().z(), node.laidSpan.from().y());
            var end = new Vector3f(node.laidSpan.to().x(), node.laidSpan.to().z(), node.laidSpan.to().y());
            var back = node.root.getLocalRotation().inverse();
            node.body.setLocalTranslation(back.mult(start.subtract(node.root.getLocalTranslation())));
            node.body.setLocalRotation(back.mult(LinePieces.along(end.subtract(start))));
        }
        if (!visual.groundPictures.isEmpty()) {
            groundPictures.see(view.id(), visual,
                    visual.holding(view.healthFraction(), visuals.getWorldConditions(), view.conditions()),
                    view.hostile(), node.body, new Coord3D(view.x(), view.y(), 0f), view.orientation(),
                    timer.getTimePerFrame() / Particles.FRAME_SECONDS, floor::at);
        }
        if (visuals.getSeeThroughWord() != null || visuals.getGlowWord() != null || visuals.getOwnGlowWord() != null
                || view.opacity() < 1f || stealthLook.looking(view.id())) {
            stealthLook.see(view, node.body, visuals, visual, timer.getTimePerFrame() / Particles.FRAME_SECONDS,
                    view.playerIndex() == game.get().getLocalPlayerIndex());
        }
        if (!visual.wordTints.isEmpty()) {
            wordTints.see(view.id(), node.root, visual,
                    visual.holding(view.healthFraction(), visuals.getWorldConditions(), view.conditions()),
                    timer.getTimePerFrame() / Particles.FRAME_SECONDS);
        }
        if (visual.risesAsBuilt && node.body != null) {
            node.body.setLocalTranslation(0f, visual.yOffset - UnitPlacement.sunk(view.built(), node.bodyTop), 0f);
        }
        for (var layer : node.layers) {
            layer.wear(node.root, view, visuals.getWorldConditions(), snapshot.get().frame(),
                    body -> paintHouseColour(body, view),
                    clip -> missing.warn(view.templateName() + "/" + clip, "animation"),
                    bone -> boneOf(node, layer, bone));
        }
        runningGear.see(view.id(), view, snapshot.get().frame());
        turretBones.turn(view.id(), view.turrets());
        drivenBones.drive(view.id(), node.body, visual.drivenBones, view, timer.getTimeInSeconds());
        // Its shadow, from its opaque pieces as drawn — none while it is drawn see-through or faded.
        boolean casts = visual.castsShadow && !stealthLook.looking(view.id());
        if (light.shadows() != null && node.body != null && (casts != node.casting || node.body != node.shadowBody)) {
            light.shadows().cast(node.body, casts, visual.leastSunHeight);
            node.casting = casts;
            node.shadowBody = node.body;
        }

        boolean ringed = visuals.drawsSelectionRings() && selected.contains(view.id());
        node.ring.setCullHint(ringed ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        if (ringed) {
            layRing(node, view);
        }

        flinch(node, view);
        animate(node, view);
    }

    /** A bone of a thing's drawn models by name: its body's, else another of its layers' than {@code asking}. */
    private static Spatial boneOf(UnitNode node, ModelLayer asking, String name) {
        var bone = Bones.named(node.body, name);
        for (var layer : node.layers) {
            if (bone != null) {
                break;
            }
            if (layer != asking) {
                bone = Bones.named(layer.body(), name);
            }
        }
        return bone;
    }

    /**
     * A short flinch when something has taken health off it.
     *
     * <p>Read off the health in the snapshot rather than off whatever fired,
     * which is not the same moment and sometimes not the same thing at all: an
     * arrow is loosed a third of a second before it arrives, and splash, a spell
     * or a floor's own trap have no shot to listen for. A drop between two
     * snapshots is not a guess about what happened — it is the thing that
     * happened, and it is already in hand.
     *
     * <p>Without it a monster absorbs a blow with no sign that it landed, and a
     * fight reads as two things standing near each other.
     */
    private void flinch(UnitNode node, UnitView view) {
        float before = node.lastHealth;
        node.lastHealth = view.health();
        // Nothing on the first sight of a unit, and nothing on the blow that
        // killed it: that one has a death to play and this would talk over it.
        if (!Float.isNaN(before) && view.health() < before && view.health() > 0f) {
            float now = timer.getTimeInSeconds();
            // Never over a swing, a cast or its work: a hero in a crowd fights rather than trembles.
            boolean busy = now < node.actionUntil || now < node.gestureUntil || view.busyClip() != null
                    || view.attacking();
            if (flinches.flinches(visuals.getFlinch(), view.id(), now, busy, before - view.health(),
                    critical.test(view.id()), visuals.getHitNumbers().bigAt())) {
                playOnce(node, looks.visualFor(view).hurtAnim);
            }
        }
    }

    /** Whether a busy clip has just begun: one now, and not the one it had. */
    static boolean cutsIn(String wasBusy, String isBusy) {
        return isBusy != null && !isBusy.equals(wasBusy);
    }

    /**
     * Choose the clip that matches what the unit is doing.
     *
     * <p>What a unit is <em>doing</em> is only ever moving or standing. Attacking
     * used to be here as a third state and it was never a state: a snapshot's
     * {@code attacking} means the unit <em>has a target</em>, which stays true for
     * the whole engagement — the walk in, the reload, the standing about between
     * blows. A clip chosen from it therefore ran on a loop at its own tempo, so a
     * monster threw punches continuously and none of them was the blow. Worse, the
     * loop's rate is the clip's: the heavy one that strikes every 1.6 seconds threw
     * a one-second punch, so it punched half again as often as it hit.
     *
     * <p>The swing is a moment and is played as one, on the shot — see
     * {@link #playOnce}. Between blows a unit that has run out of things to do is
     * standing, which is what it looks like.
     */
    private void animate(UnitNode node, UnitView view) {
        if (riderOf.containsKey(view.id())) {
            return; // a ridden mount walks as its rider does — see paceTheMount
        }
        if (node.carryingAgainAt > 0f && timer.getTimeInSeconds() >= node.carryingAgainAt) {
            carrying(node, true);
            node.carryingAgainAt = 0f;
        }
        if (cutsIn(node.lastBusy, view.busyClip())) {
            // A stagger landing mid-swing: the swing's blows will not land, so its clip gives way to the flinch.
            node.actionUntil = 0f;
            UpperBody.end(node.composer);
        }
        node.lastBusy = view.busyClip();
        if (timer.getTimeInSeconds() < node.actionUntil) {
            return; // a blow or a flinch has the model; it will hand it back
        }
        var visual = looks.visualFor(view);
        if (playByWords(node, visual, view)) {
            return;
        }
        var busy = view.busyClip();
        if (busy != null) {
            if (!busy.equals(node.currentAnim)) {
                play(node, view.looksAs(), busy, true); // its work, looped; a clip it has not is played as nothing
            }
            return;
        }
        boolean walking = view.moving() && visual.walkAnim != null;
        if (!walking && visual.isIdle(node.currentAnim)) {
            return; // standing with the idle it drew as it began to
        }
        String wanted = walking ? visual.walkAnim : visual.idleFor(view.id(), snapshot.get().frame());
        if (wanted != null && !wanted.equals(node.currentAnim)) {
            play(node, view.looksAs(), wanted, true);
        }
        if (walking && visual.walkDistance > 0f && node.composer != null) {
            // Paced to its speed, the simulation's: a soldier slowed on a slope steps slower instead of sliding.
            var action = node.composer.getCurrentAction();
            var clip = node.composer.getAnimClip(visual.walkAnim);
            if (action != null && clip != null) {
                action.setSpeed(Visuals.paced(clip.getLength(), visual.walkDistance, view.speed(), 1.0));
            }
        }
    }

    /**
     * The clip the words it holds choose, where they choose one: set on the model when the choice changes, held still
     * — its time set from the game's frame, never advanced by the window's — and false where they choose none, so
     * its roles play. Leaving a chosen clip clears what it was playing, so its role starts afresh even where it is
     * the same clip.
     */
    private boolean playByWords(UnitNode node, Visuals.UnitVisual visual, UnitView view) {
        if (visual.clipStates.isEmpty() || node.composer == null) {
            return false;
        }
        var holding = visual.holding(view.healthFraction(), visuals.getWorldConditions(), view.conditions());
        boolean had = node.wordClip.chosen();
        var chosen = WordClip.playOn(node.composer, node.wordClip, node.currentAnim, visual, holding, snapshot.get().frame(),
                view.id(), view.speed(), clip -> missing.warn(view.templateName() + "/" + clip, "animation"));
        if (chosen == null) {
            if (had) {
                node.currentAnim = "";
            }
            return false;
        }
        node.currentAnim = chosen;
        return true;
    }

    /**
     * Play a clip once, over whatever the unit was doing, and give the model back
     * when it has finished.
     *
     * <p>For the things that <em>happen</em> — a blow struck, a blow taken. They
     * are moments, and a moment played on a loop is not the same thing slower: it
     * is a different thing entirely, and it is what made a monster in a fight look
     * like a monster shadow-boxing.
     *
     * <p><b>A gesture the game named outranks this.</b> A skill with a wind-up
     * fires the weapon too — a meteor is a shot that lands late — so the same
     * frame that starts the two-handed cast also carries a {@code WeaponFired},
     * and the ordinary swing was landing on top of the cast and winning. What the
     * player saw was a mage reaching out one hand, which is the shooting clip, and
     * no sign of the animation that had just been asked for.
     *
     * <p>The cost is that a flinch is swallowed for as long as a gesture runs. It
     * is the right way round: the gesture is a thing the game asked for by name
     * and the flinch is one the client supplies, and losing the whole of the first
     * to half a second of the second is the worse trade.
     */
    void playOnce(UnitNode node, String clipName) {
        if (clipName == null || node.composer == null) {
            return;
        }
        if (timer.getTimeInSeconds() < node.gestureUntil) {
            return; // a cast the game named is already being made
        }
        var clip = node.composer.getAnimClip(clipName);
        if (clip == null) {
            return;
        }
        var joint = node.view.moving() ? looks.visualFor(node.view).upperBody : null;
        var skin = joint == null ? null
                : AnimationLibrary.findControl(node.body, com.jme3.anim.SkinningControl.class);
        if (skin != null && UpperBody.play(node.composer, skin.getArmature(), joint, clipName)) {
            return; // on the move: the blow above, the walk going on below
        }
        play(node, node.view.looksAs(), clipName, false);
        node.actionUntil = (float) (timer.getTimeInSeconds() + clip.getLength());
    }

    /**
     * The gesture a spell is cast with, on the creature that cast it.
     *
     * <p>Timed rather than simply played. A spell with a wind-up on it -- a
     * meteor takes a second and a half to arrive -- wants a gesture that ENDS as
     * it lands: the same clip at its own speed either finishes early and leaves
     * him standing there waiting, or runs on past the impact and reads as
     * somebody waving at a hole in the ground. How long it should take is the
     * game's to say, since the game is the one that knows about the wind-up.
     *
     * <p>And his hands come empty. This one is two-handed, and a mage casting it
     * with a staff in one fist and a book in the other is a mage doing two things
     * at once -- see {@link #carrying}.
     */
    void castGesture(UnitNode node, Visuals.CastAnim gesture) {
        if (node == null || node.composer == null || gesture == null) {
            return;
        }
        var clip = node.composer.getAnimClip(gesture.clip());
        if (clip == null) {
            missing.warn(node.view.templateName() + "/" + gesture.clip(), "cast animation");
            return;
        }
        float length = (float) clip.getLength();
        float seconds = gesture.seconds() > 0f ? gesture.seconds() : length;
        play(node, node.view.looksAs(), gesture.clip(), false);
        var action = node.composer.getCurrentAction();
        if (action != null && seconds > 0f) {
            action.setSpeed(length / seconds);
        }
        node.actionUntil = (float) (timer.getTimeInSeconds() + seconds);
        node.gestureUntil = node.actionUntil;
        carrying(node, false);
        node.carryingAgainAt = node.actionUntil;
    }

    /**
     * Put what a creature carries into its hands, or take it out of them.
     *
     * <p>Culled rather than detached and rebuilt: a weapon hangs on the bone's own
     * attachments node, so hiding it is one flag and getting it back is the same
     * flag -- where taking it off and hanging it again would reload the model and
     * lose the tint and the turn that were measured onto it.
     */
    private void carrying(UnitNode node, boolean shown) {
        var skin = node.body == null ? null
                : AnimationLibrary.findControl(node.body, com.jme3.anim.SkinningControl.class);
        if (skin == null) {
            return;
        }
        var visual = looks.visualFor(node.view);
        for (var one : visual.carried) {
            if (one.bone == null || skin.getArmature().getJoint(one.bone) == null) {
                continue;
            }
            skin.getAttachmentsNode(one.bone).setCullHint(
                    shown ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        }
    }

    private void play(UnitNode node, String templateName, String clipName, boolean loop) {
        try {
            if (node.composer != null) {
                // At its own speed: a clip its words held still, or a cast timed to its wind-up, left its action slowed.
                node.composer.setCurrentAction(clipName, AnimComposer.DEFAULT_LAYER, loop).setSpeed(1);
            } else if (node.legacyChannel != null) {
                node.legacyChannel.setAnim(clipName, BLEND_SECONDS);
                node.legacyChannel.setLoopMode(loop
                        ? com.jme3.animation.LoopMode.Loop : com.jme3.animation.LoopMode.DontLoop);
            } else {
                return;
            }
            node.currentAnim = clipName;
        } catch (IllegalArgumentException _) {
            missing.warn(templateName + "/" + clipName, "animation");
            node.currentAnim = clipName; // don't retry every frame
        }
    }

    /** Each ridden mount's rider, by the mount's id — see {@code GameObject.rideOn}; filled each frame. */
    private final Map<Integer, UnitNode> riderOf = new HashMap<>();

    /**
     * Every rider hung on its mount's bone, the mount drawn where the rider stands and walking as he walks; one that
     * got off put back among the units where it stands.
     */
    void seatTheRiders() {
        riderOf.clear();
        for (var node : unitNodes.values()) {
            var riding = node.view == null ? null : node.view.riding();
            var mount = riding == null ? null : unitNodes.get(riding.mount());
            if (mount == null || mount.view == null) {
                if (node.root.getParent() != unitsNode) {
                    unitsNode.attachChild(node.root); // got off: its own place again, set next frame by its view
                }
                continue;
            }
            mount.root.setLocalTranslation(UnitPlacement.where(node.view, floor::at));
            mount.root.setLocalRotation(UnitPlacement.turn(node.view));
            if (Riders.seat(node.root, mount.body, riding.bone(), new Vector3f(riding.x(), riding.z(), riding.y()))) {
                riderOf.put(mount.view.id(), node);
                paceTheMount(mount, node.view);
            }
        }
    }

    /** A ridden mount walking as its rider walks, at his speed — the rider is what moves — else standing. */
    private void paceTheMount(UnitNode mount, UnitView rider) {
        var visual = looks.visualFor(mount.view);
        if (mount.composer == null) {
            return;
        }
        String wanted = rider.moving() && visual.walkAnim != null ? visual.walkAnim
                : visual.idleFor(mount.view.id(), snapshot.get().frame());
        if (wanted != null && !wanted.equals(mount.currentAnim)) {
            play(mount, mount.view.looksAs(), wanted, true);
        }
        if (rider.moving() && visual.walkDistance > 0f) {
            var action = mount.composer.getCurrentAction();
            var clip = mount.composer.getAnimClip(visual.walkAnim);
            if (action != null && clip != null) {
                action.setSpeed(Visuals.paced(clip.getLength(), visual.walkDistance, rider.speed(), 1.0));
            }
        }
    }
}
