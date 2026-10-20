package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.light.PointLight;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Plays an effect's layers at the moment something happened, and puts everything
 * away when it is over.
 *
 * <p><b>Drawing only, and late.</b> The simulation decides that a skill went off,
 * where, and what it hit; this hangs a picture on that afterwards. Nothing here is
 * ever read back, every number may be as random as it likes, and a client that
 * drew nothing would play exactly the same game.
 *
 * <p><b>Three moments, and every layer belongs to one of them.</b> A skill is
 * CAST, and every layer of its look plays at the cast. A projectile FLIES, and its
 * trails, the glow it carries and the light it throws play for as long as it is in
 * the air; when it LANDS the rest of its layers — the burst, the flash, the ring —
 * play where it came down. Which layer is which follows from its type, so the
 * settings file never has to say.
 *
 * <p><b>Budgets, not targets.</b> Past the particle ceiling a new layer is drawn
 * with fewer particles and past the light ceiling it burns without lighting the
 * floor; anything too far off, under the fog, or off the edge of the screen is not
 * started at all. What is lost is always decoration and never an effect's meaning:
 * the ring still opens, the cast still happened.
 *
 * <p><b>Nothing outlives its moment.</b> A layer that has finished goes back to the
 * pool it came from and its light is put out. A world being rebuilt takes back
 * everything at once. The scene is the same size after an hour as after a minute.
 *
 * <p>This starts and ends layers; the rest is {@link LayerPlay} (one layer burning),
 * {@link LayerLaying} (its particles), {@link LayerSpots} (where, and whether it is worth it),
 * {@link ParticleStock} (what it is drawn with) and {@link SystemRides} (a particle system in
 * an effect's place).
 */
final class LayeredEffects {

    private static final Logger LOG = Logger.getLogger(LayeredEffects.class.getName());

    /** A creature nobody named. */
    static final int NOBODY = 0;

    /** What the effects need to know about the world they are drawn in. */
    interface Surroundings {

        /** The height of the floor under a point. */
        float floorAt(float x, float z);

        /** Where a creature stands this frame, on the floor, or null once it is gone. */
        Vector3f whereIs(int unitId);

        /** The other side's creatures within reach of a spot, as the caster's enemies. */
        int[] enemiesNear(float x, float z, float radius, int caster);

        /** Whether the player can see a spot right now. */
        boolean canSee(float x, float z);
    }

    /**
     * What a cast knows about itself.
     *
     * @param spot     where it happened, on the floor
     * @param from     where a run or a blink began, or null for a cast that did not
     *                 go anywhere
     * @param to       and where it ended
     * @param facingX  which way it went, across the floor — from the caster to the
     *                 spot, or along the run
     * @param facingZ  the other half of that
     * @param radius   how far the skill reached, or 0
     * @param on       whose it is, for a layer that follows somebody
     * @param by       who cast it
     */
    record Moment(Vector3f spot, Vector3f from, Vector3f to, float facingX, float facingZ,
            float radius, int on, int by) {

        boolean hasPath() {
            return from != null && to != null;
        }
    }

    private record Riding(Spatial node, Vector3f offset) {
    }

    private final Node root;
    private final Visuals visuals;
    private final LightPool lights;
    private final LayerSpots spots;
    private final ParticleStock stock;
    private final LayerLaying laying;
    private final SystemRides systems;

    private final List<LayerPlay> playing = new ArrayList<>();
    private final Set<String> unmeasured = new HashSet<>();
    /** The game's effect lists, for a name that is one of them: played wherever the effect would have been. */
    private EffectLists lists;
    /** Every light layer's power and radius times this — the night's fires reaching further; 1 as written. */
    private float lightScale = 1f;

    LayeredEffects(AssetManager assets, Node root, Visuals visuals, LightPool lights,
            Surroundings world, int maxParticles, float maxDistance) {
        this(assets, root, visuals, lights, world, maxParticles, maxDistance, new Random());
    }

    LayeredEffects(AssetManager assets, Node root, Visuals visuals, LightPool lights,
            Surroundings world, int maxParticles, float maxDistance, Random dice) {
        this.root = root;
        this.visuals = visuals;
        this.lights = lights;
        this.spots = new LayerSpots(world, maxDistance);
        this.stock = new ParticleStock(assets, maxParticles);
        this.laying = new LayerLaying(spots, dice);
        this.systems = new SystemRides(visuals);
    }

    /**
     * Draw a name that is a particle system rather than an effect with these: wherever an effect is started —
     * at a moment's spot, on a thing, on a projectile — a system of that name is started instead, at the spot or
     * riding the thing until it is gone.
     */
    void drawsSystemsWith(Particles particles) {
        systems.drawsWith(particles);
    }

    /**
     * Play a name that is an effect list with these: at a moment's spot, handed its reach; on a thing or a
     * projectile, riding it — a list's systems that attach to their thing go where it goes, and end with it.
     */
    void drawsListsWith(EffectLists lists) {
        this.lists = lists;
    }

    /** A particle system of that name riding a thing's node till it is gone; false where the game has none by it. */
    boolean rides(String name, Spatial node) {
        return systems.rides(name, node);
    }

    /**
     * A place in this client's frame, as a particle system's own Z-up frame has it — the models' quarter turn
     * undone: the client's {@code (x, y, z)} is the system's {@code (x, -z, y)}, and a turn about the client's up
     * is the same turn about the system's.
     */
    static Particles.Placement placementAt(Vector3f at, float turn) {
        return new Particles.Placement(at.x, -at.z, at.y, turn);
    }

    // ---- the three moments ----

    /** A skill went off: every layer of its look plays now. */
    void cast(String recipeName, Moment moment, Camera camera) {
        cast(recipeName, moment, camera, 1f);
    }

    /**
     * The same, played toward creature {@code toward}: its homing layers' particles fly into it where it stands as
     * drawn, every frame, and fade where they are once it has gone.
     */
    void cast(String recipeName, Moment moment, Camera camera, int toward) {
        int before = playing.size();
        cast(recipeName, moment, camera, 1f);
        for (int index = before; index < playing.size(); index++) {
            var one = playing.get(index);
            if (one.layer.home()) {
                one.homeTo = toward;
                one.homeIn(spots, one.age());
            }
        }
    }

    /**
     * The same, drawn bigger or smaller than it is written: its sizes, its reach and
     * a pillar's height, all times {@code scale}. What lets a level and a boss falling
     * be one look at two sizes, rather than the same blocks written out twice.
     */
    void cast(String recipeName, Moment moment, Camera camera, float scale) {
        if (lists != null && moment != null && moment.spot() != null && lists.play(recipeName,
                new EffectLists.Cue(moment.spot(), null, null, null, moment.radius() * scale))) {
            return;
        }
        var recipe = visuals.effectNamed(recipeName);
        if (recipe == null) {
            systems.startAt(recipeName, moment == null ? null : moment.spot());
            return;
        }
        if (moment == null || moment.spot() == null || scale <= 0f) {
            return;
        }
        startAll(recipeName, recipe.getLayers(), false, moment, camera, scale);
    }

    /**
     * A projectile is in the air: its trails, the glow it carries and its light
     * start, and go where it goes.
     */
    void flying(int projectileId, String recipeName, Spatial node, Vector3f offset,
            Camera camera) {
        var shift = offset == null ? Vector3f.ZERO : offset;
        if (lists != null && node != null && lists.has(recipeName)) {
            lists.play(recipeName, new EffectLists.Cue(node.localToWorld(shift, null),
                    node.getWorldRotation().clone(), node, null, 0f));
            return;
        }
        var recipe = visuals.effectNamed(recipeName);
        if (recipe == null) {
            systems.ride(projectileId, recipeName, node, offset);
            return;
        }
        if (node == null) {
            return;
        }
        var layers = recipe.getLayers();
        var at = node.localToWorld(shift, null);
        var moment = new Moment(at, null, null, 0f, 1f, 0f, NOBODY, NOBODY);
        for (int index = 0; index < layers.size(); index++) {
            var layer = layers.get(index);
            if (!inFlight(layer)) {
                continue;
            }
            start(recipeName, index, layer, new LayerSpots.Place(at, NOBODY), moment, camera,
                    projectileId, new Riding(node, shift.clone()), 1f);
        }
    }

    /**
     * It came down: what it carried stops being fed and burns out where it is, and
     * the rest of its layers play at the spot.
     */
    void landed(int projectileId, String recipeName, Vector3f where, Camera camera) {
        grounded(projectileId);
        var recipe = visuals.effectNamed(recipeName);
        if (recipe == null || where == null) {
            return;
        }
        startAll(recipeName, recipe.getLayers(), true, new Moment(where, null, null, 0f, 1f, 0f, NOBODY, NOBODY),
                camera, 1f);
    }

    /**
     * It left without landing — walked out of the light, or was never going to
     * burst. What it was trailing finishes burning where it is.
     */
    void grounded(int projectileId) {
        if (projectileId == NOBODY) {
            return;
        }
        systems.release(projectileId);
        for (var going : playing) {
            if (going.projectile == projectileId && going.rides != null) {
                going.stopFeeding();
                going.rides = null;
            }
        }
    }

    /**
     * Which layers a projectile carries while it flies, rather than leaves where it
     * lands: anything let out behind it, anything wrapped round it, and a light with
     * no time of its own.
     */
    static boolean inFlight(EffectLayer layer) {
        return EffectLayer.TRAIL.equals(layer.type()) && !EffectLayer.PATH.equals(layer.at())
                || EffectLayer.AURA.equals(layer.type())
                || EffectLayer.LIGHT.equals(layer.type()) && layer.seconds() <= 0f;
    }

    // ---- starting a layer ----

    /** Start every layer of a cast, or, for a landing, every one a flight did not already carry. */
    private void startAll(String recipeName, List<EffectLayer> layers, boolean landing, Moment moment,
            Camera camera, float scale) {
        for (int index = 0; index < layers.size(); index++) {
            var layer = layers.get(index);
            if (landing && inFlight(layer)) {
                continue;
            }
            for (var place : spots.placesFor(layer, moment)) {
                start(recipeName, index, layer, place, moment, camera, NOBODY, null, scale);
            }
        }
    }

    private void start(String recipeName, int index, EffectLayer layer, LayerSpots.Place place,
            Moment moment, Camera camera, int projectile, Riding riding, float scale) {
        float unit = unitFor(recipeName, layer, moment) * scale;
        if (!spots.worthStarting(layer, unit, place.at(), camera)) {
            return;
        }
        boolean aura = EffectLayer.AURA.equals(layer.type());
        // An aura goes where its man goes, always; anything else only if it says so.
        boolean wears = aura || layer.follows();
        boolean follows = wears && place.follows() != NOBODY || riding != null;
        String key = aura && place.follows() != NOBODY
                ? recipeName + '#' + index + '@' + place.follows() : null;
        if (key != null && carriedOn(key, layer, spanOf(recipeName, layer))) {
            return;
        }

        ParticleLayer drawn = null;
        if (layer.draws()) {
            int room = stock.room();
            if (room <= 0) {
                return; // over the ceiling: this layer goes undrawn, the rest may not
            }
            drawn = stock.borrow(Math.min(layer.count(), room));
            drawn.dress(layer, stock.textureFor(layer.texture()));
        }
        PointLight light = null;
        if (EffectLayer.LIGHT.equals(layer.type())) {
            light = lights == null ? null : lights.take();
            if (light == null) {
                return; // every light is burning: this one glows in nobody's eyes
            }
        }

        var going = new LayerPlay(layer, drawn, light, key, unit, projectile, place.at());
        going.span = spanOf(recipeName, layer);
        going.face(moment.facingX(), moment.facingZ());
        if (riding != null) {
            going.rides = riding.node();
            going.offset = riding.offset();
        } else if (follows) {
            going.follows = place.follows();
        }
        going.attached = wears && (going.follows != NOBODY || going.rides != null);
        going.emitting = layer.continuous();
        going.fall = EffectLayer.TRAIL.equals(layer.type()) || aura || EffectLayer.LIGHT.equals(layer.type())
                ? layer.fall() : 0f; // only what is carried can fall: a burst is over before it could
        going.until = going.untilFor();

        if (drawn != null) {
            drawn.floor(going.anchor.y); // what a bouncing layer lands on: the floor where it was played
            laying.lay(going, moment);
            root.attachChild(drawn.geometry());
            drawn.show(layer.delay() <= 0f);
        }
        playing.add(going);
        going.place(spots, 0f);
    }

    /**
     * Whether this aura is already burning, and if so carried on: the whirlwind is drawn again on every blow it
     * lands, and every one of those arrives as a cast. The aura it wears is the whole run's, from the first -- a
     * second one stacked on it would be twice as bright and would outlast the skill by however long the blows
     * went on. One that renews is a status given again, and wears on to the new cast's end.
     */
    private boolean carriedOn(String key, EffectLayer layer, float span) {
        boolean burning = false;
        for (var one : playing) {
            if (!one.burning(key)) {
                continue;
            }
            burning = true;
            if (layer.renews()) {
                one.renew(span);
            }
        }
        return burning;
    }

    /**
     * What one of a layer's sizes is: a unit, or the skill's reach.
     *
     * <p>The reach is the cast's when the cast knows it, and otherwise the one the
     * game gave this look -- a projectile landing does not carry the radius of the
     * blast it makes, but the skill that threw it does.
     */
    private float unitFor(String recipeName, EffectLayer layer, Moment moment) {
        if (!EffectLayer.REACH.equals(layer.measure())) {
            return 1f;
        }
        float reach = moment.radius() > 0f ? moment.radius() : visuals.getEffectReach(recipeName);
        if (reach > 0f) {
            return reach;
        }
        if (unmeasured.add(recipeName)) {
            LOG.warning(() -> recipeName + " has a layer measured in reach and nothing says"
                    + " how far it reaches -- drawn in units");
        }
        return 1f;
    }

    /** How long a layer of a recipe goes on for: its own seconds, the recipe's, or one life of it. */
    private float spanOf(String recipeName, EffectLayer layer) {
        return layer.seconds() > 0f ? layer.seconds()
                : visuals.getEffectSeconds(recipeName) > 0f ? visuals.getEffectSeconds(recipeName) : layer.lifeMax();
    }

    // ---- every frame ----

    /** Move everything on, feed what is still being fed, and put away what is done. */
    void update(float tpf, Camera camera) {
        var going = playing.iterator();
        while (going.hasNext()) {
            var one = going.next();
            one.clock += tpf;
            float local = one.age();
            if (local < 0f) {
                continue; // not yet: smoke waits for its fire
            }
            if (one.drawn != null) {
                one.drawn.show(true);
            }
            one.place(spots, local);
            one.homeIn(spots, local);
            laying.feed(one, local, tpf);
            one.fadeOutAnAura(local);
            one.lightUp(local, lightScale);
            if (one.drawn != null) {
                one.drawn.time(local);
            }
            if (local >= one.until) {
                putAway(one);
                going.remove();
            }
        }
    }

    void lightScale(float times) {
        this.lightScale = Math.max(0f, times);
    }

    float lightScale() {
        return lightScale;
    }

    // ---- putting things away ----

    private void putAway(LayerPlay one) {
        if (one.drawn != null) {
            stock.giveBack(one.drawn);
        }
        if (one.light != null && lights != null) {
            lights.give(one.light);
        }
    }

    /** Everything back in the box at once: a world being rebuilt has nothing burning. */
    void clear() {
        systems.clear();
        for (var one : playing) {
            putAway(one);
        }
        playing.clear();
    }

    // ---- what a test may ask ----

    int playingCount() {
        return playing.size();
    }

    int particlesInUse() {
        return stock.inUse();
    }

    int bornSoFar() {
        return laying.bornSoFar();
    }

    int layersMade() {
        return stock.made();
    }
}
