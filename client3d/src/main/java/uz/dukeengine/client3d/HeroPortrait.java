package uz.dukeengine.client3d;

import com.jme3.anim.AnimComposer;
import com.jme3.bounding.BoundingBox;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import uz.dukeengine.game.view.UnitView;

/**
 * The creature in the hero panel's frame, drawn from a model rather than as a
 * silhouette: a small scene of its own with a camera close to its face, rendered
 * into a texture the panel hangs in its frame.
 *
 * <p>The frame used to hold a drawing, and the note where it was drawn said why —
 * a second camera was a real thing to want and a real thing to pay for, and what
 * the frame was for was saying which corner of the screen was the player's.
 * Warcraft's answer is that a portrait is not a picture: it is a person who
 * breathes while nothing is happening, stands ready when something is, and falls
 * over when he dies. That is worth the second camera, and the cost is kept where
 * it belongs — see {@link PortraitMood} for the rate.
 *
 * <p><b>His own copy of the model, never the one in the world.</b> Two reasons and
 * the second is not obvious. The first is that the world's hero is where the
 * simulation put him, facing where he walks, which is not a portrait. The second
 * is that a skinned material holds the pose of the skeleton driving it, so a
 * portrait sharing the world's materials would have the two of them writing over
 * each other's poses every frame — the same trap the world's own models pay for
 * by never sharing a material between two units.
 *
 * <p><b>What it is not.</b> It has no opinion about which creature is in it: it is
 * given one. It never names a clip — those come from {@link PortraitLook}, which
 * the game fills in. And it takes no part in the simulation: everything it reads
 * arrives in the snapshot the client is already drawing from.
 *
 * <p>Without a render manager — a test, a build with no display — it is built,
 * used and closed exactly as it would be, and quietly draws nothing. Same bargain
 * as the silence a machine with no speaker gets.
 */
final class HeroPortrait {

    /**
     * Builds a creature's body the way the world builds it.
     *
     * <p>A seam rather than a second copy of the building. What a creature is made
     * of — the model, its skin, the tint, the thing in its hand, the clips
     * borrowed off a library — is a paragraph of decisions that belongs in one
     * place, and a portrait drawn from a differently-built body would be a
     * portrait of somebody slightly else.
     *
     * @param alsoWanted clips beyond the creature's own five: a portrait asks for
     *     the ones only it plays, and nothing else would fetch them
     */
    interface Bodies {

        /** The built body, or {@code null} if its model would not load. */
        Spatial of(Visuals.UnitVisual visual, Collection<String> alsoWanted);
    }

    private static final Logger LOG = Logger.getLogger(HeroPortrait.class.getName());

    /**
     * Twice the frame the design draws, so it is still sharp where the panel is
     * scaled up — and small enough that a whole one of these is a rounding error
     * beside a floor of the dungeon.
     */
    private static final int WIDTH = 252;
    private static final int HEIGHT = 300;

    private final RenderManager renderManager;
    private final Visuals visuals;
    private final Bodies bodies;

    /** Its own scene: nothing of the world is in here, and nothing of this is there. */
    private final Node scene = new Node("portrait-scene");

    private ViewPort view;
    private FrameBuffer frame;
    private Texture2D texture;
    private Camera camera;
    /** Whether anything has ever been drawn into the texture; before that it is noise. */
    private boolean drawn;

    private String template;
    private PortraitLook look;
    private Spatial body;
    private AnimComposer composer;
    private PortraitMood mood;
    private int subject = -1;
    private float modelHeight = 1f;

    private String playing;
    private PortraitLook.State showing;

    private final Set<String> missingClips = new HashSet<>();
    /** Setting the render target failed once; it will not be tried again. */
    private boolean broken;

    private HeroPortrait(RenderManager renderManager, Visuals visuals, Bodies bodies) {
        this.renderManager = renderManager;
        this.visuals = visuals;
        this.bodies = bodies;
    }

    static HeroPortrait open(RenderManager renderManager, Visuals visuals, Bodies bodies) {
        return new HeroPortrait(renderManager, visuals, bodies);
    }

    /** For a client that will never draw one. */
    static HeroPortrait none() {
        return new HeroPortrait(null, null, null);
    }

    /**
     * Put this creature in the frame, or nobody.
     *
     * <p>Follows the card rather than always showing the hero, because the rest of
     * the bar does: the name, the health and the figures beside the frame are all
     * about whatever is selected, and a portrait of somebody else would make the
     * panel disagree with itself. A creature the game described no portrait for
     * leaves the frame to the drawing that was always there.
     *
     * <p>A fallen creature keeps the frame against <em>nobody</em>, and only
     * against nobody. It has to keep it against an empty card, since the card
     * empties on the frame he falls and his death would never be drawn; it must
     * not keep it against the next creature clicked on, or the frame would be
     * stuck showing a dead hero for the rest of the session, new run and all.
     *
     * @param rank the level as the panel last read it — a word, because the game
     *     spells its own levels, and only its changing means anything here. One
     *     frame old, which a change that has to be noticed exactly once can afford
     */
    void show(UnitView creature, String rank) {
        if (off()) {
            return;
        }
        if (creature == null) {
            if (mood != null && mood.holding()) {
                return; // he is dying, and the card emptied on the frame he fell
            }
            letGo();
            return;
        }
        // Whatever the player can select gets a face, and nothing else does. The
        // gate is the engine's own word for it rather than a list the game has to
        // keep: an arrow, a chest and a barrel all have models and none of them is
        // ever the thing the bar is describing.
        var wanted = creature.selectable()
                ? visuals.getPortrait(creature.templateName()) : null;
        if (wanted == null) {
            letGo();
            return;
        }
        if (!creature.templateName().equals(template)) {
            stand(creature.templateName(), wanted);
        }
        if (body == null) {
            return;
        }
        subject = creature.id();
        mood.nowShowing(creature.id());
        mood.sees(creature.attacking(), creature.healthFraction(), rank);
    }

    /** That creature died — the event, not a guess about why it left the snapshot. */
    void died(int unitId) {
        if (!off() && mood != null && unitId == subject) {
            mood.died();
        }
    }

    /**
     * Advance the portrait and, if it is owed a frame, let it be drawn.
     *
     * <p>The viewport is switched off at the top and only back on for a frame that
     * is owed, which is the whole of the rate limiting: a disabled viewport is
     * skipped by the render manager before anything of it is touched.
     *
     * <p>The scene is stepped by hand because nothing else will. A scene hanging
     * off a viewport rather than off the root is not in what the application
     * updates, so its animation would never move — and that turns out to be the
     * convenient half of the arrangement, since stepping it only on the frames it
     * is drawn on is exactly what a portrait at a third of the frame rate wants.
     *
     * @param drawing whether the world is on screen at all. A menu, a loading
     *     screen or a held-still level-up leave the last frame in the texture,
     *     which is the right picture for a game that is not running
     */
    void update(float tpf, boolean drawing) {
        if (off() || view == null) {
            return;
        }
        view.setEnabled(false);
        if (!drawing || body == null || mood == null) {
            return;
        }
        float seconds = mood.advance(tpf);
        if (seconds <= 0f) {
            return;
        }
        play();
        scene.updateLogicalState(seconds);
        scene.updateGeometricState();
        view.setEnabled(true);
        drawn = true;
    }

    /**
     * Stop drawing, and keep what is already in the frame.
     *
     * <p>For the screens the client leaves before it reaches {@link #update} at
     * all — the main menu and the loading screen. A viewport is skipped only if it
     * is switched off, so one left on would go on redrawing the same standing pose
     * every frame of a menu nobody can see it through.
     */
    void rest() {
        if (view != null) {
            view.setEnabled(false);
        }
    }

    /**
     * The live picture, or {@code null} when there is none — which is most games,
     * every creature nobody described a portrait for, and every frame before the
     * first one has been drawn.
     */
    Texture texture() {
        return off() || view == null || body == null || !drawn ? null : texture;
    }

    /** Give back the render target and everything standing in front of it. */
    void close() {
        if (view != null) {
            view.clearScenes();
            renderManager.removePreView(view);
            view = null;
        }
        scene.detachAllChildren();
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
        if (texture != null && texture.getImage() != null) {
            texture.getImage().dispose();
            texture = null;
        }
        letGo();
        drawn = false;
    }

    // ---- what is standing in it ----

    /**
     * Build this creature's body and put it in the scene, taking the last one out
     * first.
     *
     * <p>Only when the template changes. A hero who is looked at for an hour is
     * built once; clicking between him and a skeleton and back rebuilds two
     * models, which is two model loads for a whole session and the reason this is
     * keyed on the template rather than on the creature.
     */
    private void stand(String templateName, PortraitLook wanted) {
        // Whatever was here goes first, so the scene cannot grow across a run.
        scene.detachAllChildren();
        // And what is in the texture is the last one's face. Until this one has
        // been drawn the frame has nothing true to show, so it shows the drawing:
        // with the world held still — a pause, with the bar still taking clicks —
        // nothing would redraw it, and the skeleton's card would wear the hero's
        // head.
        drawn = false;
        template = null;
        look = null;
        body = null;
        composer = null;
        mood = null;
        playing = null;
        showing = null;

        var visual = visuals.of(templateName);
        var full = filledIn(wanted, visual);
        if (!full.any()) {
            return; // nothing to stand in: no clip named and none of its own
        }
        var built = bodies.of(visual, clipsOf(full));
        if (built == null) {
            return; // the model would not load; the drawn figure keeps the frame
        }
        // The portrait measures in the model's own units, so the world's scale and
        // the height it is lifted off the ground are both wrong here. Its rotation
        // is kept: that is the turn that brings its authored forward onto the
        // engine's, which is what lets one set of camera angles serve every model.
        built.setLocalScale(1f);
        built.setLocalTranslation(0f, 0f, 0f);
        scene.attachChild(built);
        scene.updateGeometricState();

        template = templateName;
        look = full;
        body = built;
        composer = AnimationLibrary.findControl(built, AnimComposer.class);
        modelHeight = standingHeight(built);
        mood = new PortraitMood(full, visuals.getPortraitFps(), this::lengthOf);
        if (composer == null) {
            LOG.warning(() -> "the portrait of " + templateName + " carries no animation"
                    + " — it will stand in the frame rather than live in it");
        }
        if (wire()) {
            place();
        }
    }

    private void letGo() {
        if (template == null) {
            return;
        }
        scene.detachAllChildren();
        template = null;
        look = null;
        body = null;
        composer = null;
        mood = null;
        playing = null;
        showing = null;
        subject = -1;
        if (view != null) {
            view.setEnabled(false);
        }
    }

    /** Every clip any of its states may call for, so they are all fetched at once. */
    private static List<String> clipsOf(PortraitLook look) {
        var wanted = new ArrayList<String>();
        for (var state : PortraitLook.State.values()) {
            var clip = look.clip(state);
            if (clip != null && !wanted.contains(clip)) {
                wanted.add(clip);
            }
        }
        return wanted;
    }

    /**
     * How tall the creature <em>stands</em>, measured rather than declared.
     *
     * <p>Because the camera is placed in fractions of it — see
     * {@link PortraitLook.Camera} — a creature nobody has measured still gets a
     * portrait of its face, which is what lets one block serve every monster in
     * the game.
     *
     * <p><b>How far its top is off the ground, not how tall its box is.</b> The
     * two are the same for anything that stands on its feet and quite different
     * for anything that does not: the dungeon's two drifting creatures are
     * modelled with something trailing below the floor they hover over, so their
     * boxes are 3.05 tall where they stand 2.17. Measured by the box, the camera
     * looks a third of a body too low on exactly the two creatures nobody would
     * think to check.
     */
    static float standingHeight(Spatial body) {
        if (!(body.getWorldBound() instanceof BoundingBox box)) {
            return 1f; // nothing to measure: the fractions then mean world units
        }
        float top = box.getCenter().y + box.getYExtent();
        if (top > 0.001f) {
            return top;
        }
        // Drawn entirely under the ground, or not drawn at all. Neither is a thing
        // a camera can be placed against, so fall back to the box and then to one.
        float whole = box.getYExtent() * 2f;
        return whole > 0.001f ? whole : 1f;
    }

    /**
     * The look with the creature's own clips put in where the portrait named none.
     *
     * <p>The whole of what a monster needs to have a portrait: it already has an
     * idle and a death bound on it, so standing in the frame and falling over in
     * it cost nothing and no block. What a block is for is the states a creature
     * has no clip for — a bow held ready, a flourish for a new level.
     *
     * <p>Fighting and being hurt fall back to standing rather than to the unit's
     * attack clip, and deliberately: a unit's attack is one blow, played once on
     * the shot. Looped in a portrait it is a monster shadow-boxing, which is the
     * same mistake the world's own animation made and paid for.
     */
    static PortraitLook filledIn(PortraitLook look, Visuals.UnitVisual visual) {
        var named = look.clips();
        String standing = or(named.calm(), visual.idleAnim);
        return new PortraitLook(look.camera(),
                new PortraitLook.Clips(standing,
                        or(named.fight(), standing),
                        or(named.hurt(), standing),
                        or(named.dead(), visual.dieAnim),
                        named.levelUp()), // nothing falls back to a flourish
                look.hurtBelowPercent(), look.hurtSpeed());
    }

    private static String or(String named, String fallback) {
        return named != null ? named : fallback;
    }

    private float lengthOf(String clipName) {
        if (composer == null || clipName == null) {
            return 0f;
        }
        var clip = composer.getAnimClip(clipName);
        return clip == null ? 0f : (float) clip.getLength();
    }

    // ---- what it is doing ----

    private void play() {
        if (composer == null) {
            return;
        }
        var wanted = mood.clip();
        var use = clipOnHand(composer, wanted, look.clip(PortraitLook.State.CALM));
        if (wanted != null && !wanted.equals(use) && missingClips.add(template + "/" + wanted)) {
            LOG.warning(() -> "the portrait of " + template + " asks for " + wanted
                    + ", which is not on the model — standing instead");
        }
        if (use == null) {
            return;
        }
        composer.setGlobalSpeed(mood.speed());
        var state = mood.state();
        // The state as well as the clip: two levels in quick succession are the
        // same clip twice, and a flourish that did not restart would be one.
        if (use.equals(playing) && state == showing) {
            return;
        }
        playing = use;
        showing = state;
        composer.setCurrentAction(use, AnimComposer.DEFAULT_LAYER, mood.loops());
    }

    /**
     * The clip to actually play: the one asked for, the standing one if the model
     * does not carry it, or nothing at all.
     *
     * <p>A model is the game's art and the clip names are written in its data
     * file, so the two can disagree — a kit with no death animation, a clip
     * renamed by an exporter — and neither compiler sees the other. Falling back
     * to standing keeps a portrait that is wrong rather than a portrait that is
     * frozen, and the caller says so once in the log.
     */
    static String clipOnHand(AnimComposer composer, String wanted, String calm) {
        if (composer == null) {
            return null;
        }
        if (wanted != null && composer.getAnimClip(wanted) != null) {
            return wanted;
        }
        return calm != null && composer.getAnimClip(calm) != null ? calm : null;
    }

    // ---- the render target ----

    /**
     * Build the camera, the target and the viewport — once, and never again.
     *
     * <p>A frame buffer is a lump of graphics memory and a viewport is a standing
     * entry in the render manager's list; making one per hero, or worse per frame,
     * is how a portrait becomes the reason a run leaks.
     *
     * @return whether there is anything to draw into. False with no render manager,
     *     which is a test rather than a failure, and false once anything has gone
     *     wrong with it — the panel then keeps the drawing it always had
     */
    private boolean wire() {
        if (view != null) {
            return true;
        }
        if (renderManager == null || broken) {
            return false;
        }
        try {
            texture = new Texture2D(WIDTH, HEIGHT, Image.Format.RGBA8);
            texture.setMagFilter(Texture.MagFilter.Bilinear);
            texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
            frame = new FrameBuffer(WIDTH, HEIGHT, 1);
            frame.addColorTarget(FrameBuffer.FrameBufferTarget.newTarget(texture));
            frame.setDepthTarget(FrameBuffer.FrameBufferTarget.newTarget(Image.Format.Depth));
            camera = new Camera(WIDTH, HEIGHT);
            view = renderManager.createPreView("hero-portrait", camera);
            view.setClearFlags(true, true, true);
            // Nothing behind him. The panel paints its own lit recess and its gold
            // frame, and an opaque background in here would be a grey card laid
            // over both of them.
            view.setBackgroundColor(new ColorRGBA(0f, 0f, 0f, 0f));
            view.setOutputFrameBuffer(frame);
            view.attachScene(scene);
            view.setEnabled(false);
            light();
            return true;
        } catch (RuntimeException e) {
            broken = true;
            view = null;
            LOG.warning(() -> "the portrait could not be given a render target ("
                    + e.getMessage() + ") — drawing the figure that was there before");
            return false;
        }
    }

    /**
     * His own light, because the dungeon's is under fog and half a floor away.
     *
     * <p>A key from over the camera's shoulder, a cold fill from the other side so
     * the shadowed half is not black, and enough ambient that the whole of him
     * reads. Which is a portrait light rather than a scene light: what the frame
     * is for is his face, and the dark he is standing in is the panel's business.
     */
    private void light() {
        var key = new DirectionalLight(
                new Vector3f(-0.6f, -0.45f, -0.65f).normalizeLocal(),
                new ColorRGBA(1.25f, 1.17f, 0.98f, 1f));
        var fill = new DirectionalLight(
                new Vector3f(0.7f, -0.12f, 0.7f).normalizeLocal(),
                new ColorRGBA(0.34f, 0.37f, 0.5f, 1f));
        scene.addLight(key);
        scene.addLight(fill);
        scene.addLight(new AmbientLight(new ColorRGBA(0.42f, 0.4f, 0.38f, 1f)));
    }

    /**
     * Stand the camera where the file says, in the model's own height.
     *
     * <p>Straight ahead is {@code +X}, which is where the model's authored forward
     * has already been turned to — so a yaw of zero means his face for every kit,
     * and the angles in one hero's block mean the same thing in another's.
     */
    private void place() {
        var where = look.camera();
        float eye = where.head() * modelHeight;
        float radius = Math.max(0.01f, where.distanceFor(modelHeight));
        float yaw = FastMath.DEG_TO_RAD * where.yaw();
        float pitch = FastMath.DEG_TO_RAD * where.pitch();
        var target = new Vector3f(0f, eye, 0f);
        var back = new Vector3f(
                FastMath.cos(yaw) * FastMath.cos(pitch),
                FastMath.sin(pitch),
                FastMath.sin(yaw) * FastMath.cos(pitch)).multLocal(radius);
        camera.setLocation(target.add(back));
        camera.lookAt(target, Vector3f.UNIT_Y);
        camera.setFrustumPerspective(where.fov(), (float) WIDTH / HEIGHT,
                Math.max(0.001f, radius * 0.05f), radius * 10f);
    }

    private boolean off() {
        return visuals == null || bodies == null;
    }

    // ---- for the tests, which have no window ----

    /** What is standing in the scene, so a test can see it is one thing and not two. */
    Node scene() {
        return scene;
    }

    /** Which template the frame is built for, or {@code null} for an empty one. */
    String standing() {
        return template;
    }
}
