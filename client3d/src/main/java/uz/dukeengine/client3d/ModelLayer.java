package uz.dukeengine.client3d;

import com.jme3.anim.AnimComposer;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import uz.dukeengine.core.view.UnitView;

/**
 * One of a thing's other models — see {@link Visuals.UnitVisual#layer}: drawn at its place and facing, and chosen,
 * shown and moved by the words the thing holds on its own, as the reference's second draw modules are. A set of words
 * that names no model draws nothing until the words change. What is picked, ringed and barred is the thing's body.
 */
final class ModelLayer {

    private final Visuals.UnitVisual look;
    private final Function<String, Spatial> load;
    private final Pieces pieces = new Pieces();
    private final WordClip clip = new WordClip();
    private Spatial body;
    /** The file it wears, or null while its words name no model. */
    private String modelPath;
    private AnimComposer composer;
    private String playing = "";
    private float top;
    private final BoneSystems systems;
    /** The look it wore last: the words of the model they chose, none for its plain one; null before its first frame. */
    private java.util.SortedSet<String> lastLook;
    /** The clip playing on the way between two looks, or null. */
    private Visuals.UnitVisual.Transition passing;
    private Spatial passingBody;
    private AnimComposer passingComposer;
    private int passingSince;
    private float passingLength;
    /** Where in its clip the transition began, in seconds: its first frame, its last, or the frame a look kept. */
    private float passingFrom;
    /** The words it wears now: what a look waiting for its clip to end goes on being worn by. */
    private Set<String> worn;

    /** A layer drawn as {@code look} says, its models built by {@code load}. */
    ModelLayer(Visuals.UnitVisual look, Function<String, Spatial> load) {
        this(look, load, null);
    }

    /** The same, running the particle systems its look puts at its bones through {@code systems}. */
    ModelLayer(Visuals.UnitVisual look, Function<String, Spatial> load, BoneSystems systems) {
        this.look = look;
        this.load = load;
        this.systems = systems;
    }

    /**
     * Wear what the words {@code view}'s thing holds choose — its own, the {@code world}'s and those its health
     * decides — hung from {@code parent}: the model, painted by {@code paint}, its pieces, how far it has risen where it
     * rises as it is built, and its clip at the game's frame {@code frame}.
     *
     * @param missing told the name of a clip its words chose that its model does not have
     */
    void wear(Node parent, UnitView view, Set<String> world, int frame, Consumer<Spatial> paint,
            Consumer<String> missing) {
        wear(parent, view, world, frame, paint, missing, bone -> null);
    }

    /**
     * The same, hung — where its look says so ({@link Visuals.UnitVisual#hungOn}) — at the bone {@code bones} finds by
     * name in the thing's other models, and at the thing's place where it finds none.
     */
    void wear(Node parent, UnitView view, Set<String> world, int frame, Consumer<Spatial> paint,
            Consumer<String> missing, Function<String, Spatial> bones) {
        var holding = look.holding(view.healthFraction(), world, view.conditions());
        if (worn != null && look.waits() && clip.holdsBack(frame)
                && look.waitsFor(look.lookFor(worn), look.lookFor(holding))) {
            holding = worn; // the look it wears is let play its clip to the end first
        }
        worn = holding;
        if (!look.transitions.isEmpty() && pass(parent, holding, frame, paint)) {
            return; // on its way between two looks
        }
        var wanted = look.modelFor(holding);
        if (!Objects.equals(wanted, modelPath)) {
            if (body != null) {
                body.removeFromParent();
            }
            modelPath = wanted;
            body = wanted == null ? null : load.apply(wanted);
            composer = body == null ? null : AnimationLibrary.findControl(body, AnimComposer.class);
            playing = "";
            if (body != null) {
                paint.accept(body);
                parent.attachChild(body);
                top = look.risesAsBuilt ? UnitPlacement.riseOf(look, body) : 0f;
                pieces.applyTo(body, Set.of());
            }
        }
        if (body == null) {
            if (systems != null) {
                systems.stop(); // a look that draws nothing runs nothing
            }
            return;
        }
        var host = look.hungOn == null ? parent : hostOf(bones.apply(look.hungOn), parent);
        if (body.getParent() != host) {
            host.attachChild(body); // hung from its bone once the model that has it is there, or anew after a swap
        }
        if (pieces.choose(look.pieceStateFor(holding), look.pieceStates)) {
            pieces.applyTo(body, Set.of());
        }
        if (systems != null) {
            systems.choose(look.particlesFor(holding), body);
        }
        if (look.risesAsBuilt) {
            body.setLocalTranslation(0f, look.yOffset - UnitPlacement.sunk(view.built(), top), 0f);
        }
        animate(holding, frame, view.id(), missing);
    }

    /** The clip its words choose, or else its idle on a loop. */
    private void animate(Set<String> holding, int frame, int thing, Consumer<String> missing) {
        if (composer == null) {
            return;
        }
        boolean had = clip.chosen();
        var chosen = WordClip.playOn(composer, clip, playing, look, holding, frame, thing, missing);
        if (chosen != null) {
            playing = chosen;
            return;
        }
        if (had) {
            playing = ""; // its idle afresh, even where it is the clip its words had
        }
        var idle = look.idleAnim;
        if (idle != null && !idle.equals(playing) && composer.getAnimClip(idle) != null) {
            composer.setCurrentAction(idle, AnimComposer.DEFAULT_LAYER, true).setSpeed(1);
            playing = idle;
        }
    }

    /**
     * The clip between its last look and the one its words choose now: started where a transition joins the two, stepped
     * by the game's frames, and ended once played — the new look's own worn from then.
     *
     * @return whether it is still on its way, drawn as the transition's model
     */
    private boolean pass(Node parent, Set<String> holding, int frame, Consumer<Spatial> paint) {
        var now = look.lookFor(holding);
        if (lastLook == null) {
            lastLook = look.lookFor(java.util.Set.of()); // appearing is coming from the look no words choose
        }
        if (!now.equals(lastLook)) {
            var way = look.transitionFor(lastLook, now);
            if (way != null) {
                begin(way, parent, frame, paint);
            }
        }
        lastLook = now;
        return passing != null && step(frame);
    }

    /** The transition's clip set to where it stands in the game's frame {@code frame}; false, and ended, once played. */
    private boolean step(int frame) {
        float played = (frame - passingSince) * uz.dukeengine.core.GameConstants.SECONDS_PER_LOGICFRAME
                * passing.speed();
        boolean backwards = passing.mode() == Visuals.ClipMode.ONCE_BACKWARDS;
        float at = backwards ? passingFrom - played : passingFrom + played;
        if (backwards ? at <= 0f && played > 0f : at >= passingLength) {
            end();
            return false;
        }
        // Never its end itself: to the model the end of a clip is its start again.
        float last = Math.nextDown(passingLength);
        passingComposer.setTime(AnimComposer.DEFAULT_LAYER, Math.clamp(at, 0f, last));
        return true;
    }

    /** Onto the transition's model and clip, off whatever it wore: the new look's own is worn once it has played. */
    private void begin(Visuals.UnitVisual.Transition way, Node parent, int frame, Consumer<Spatial> paint) {
        // Where the look it leaves stood, where both keep the frame: its share of its clip, read before it is let go.
        double kept = way.keepGroup() != null && way.keepGroup().equals(clip.keepGroup()) ? clip.share(frame) : -1;
        end();
        if (body != null) {
            body.removeFromParent();
        }
        body = null;
        modelPath = null;
        composer = null;
        playing = "";
        var model = way.model() == null ? null : load.apply(way.model());
        var clips = model == null ? null : AnimationLibrary.findControl(model, AnimComposer.class);
        var clip = clips == null ? null : clips.getAnimClip(way.clip());
        if (model == null || clip == null) {
            return; // nothing to play: straight on to the new look
        }
        paint.accept(model);
        parent.attachChild(model);
        clips.setCurrentAction(way.clip(), AnimComposer.DEFAULT_LAYER, false).setSpeed(0);
        passing = way;
        passingBody = model;
        passingComposer = clips;
        passingSince = frame;
        passingLength = (float) clip.getLength();
        passingFrom = kept >= 0 ? (float) (kept * passingLength)
                : way.mode() == Visuals.ClipMode.ONCE_BACKWARDS ? passingLength : 0f;
        if (systems != null) {
            systems.choose(way.particles(), model); // its own systems, while it plays
        }
        step(frame); // its first frame set at once
    }

    private void end() {
        if (passingBody != null) {
            passingBody.removeFromParent();
        }
        if (systems != null && passing != null) {
            systems.stop(); // its systems end with it; the look's own are chosen again as it is worn
        }
        passing = null;
        passingBody = null;
        passingComposer = null;
    }

    /**
     * The node it hangs from for a bone: the bone itself where it is a node or a joint's attachments, the node it
     * belongs to where it is a mesh; the thing's own node where there is no such bone.
     */
    private static Node hostOf(Spatial bone, Node parent) {
        if (bone instanceof Node node) {
            return node;
        }
        return bone != null && bone.getParent() != null ? bone.getParent() : parent;
    }

    /** What it draws now — the transition's model while one plays — or null for nothing. */
    Spatial body() {
        return passingBody != null ? passingBody : body;
    }
}
