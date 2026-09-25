package uz.dukeengine.client3d;

import com.jme3.anim.AnimComposer;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import uz.dukeengine.game.view.UnitView;

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

    /** A layer drawn as {@code look} says, its models built by {@code load}. */
    ModelLayer(Visuals.UnitVisual look, Function<String, Spatial> load) {
        this.look = look;
        this.load = load;
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
            return;
        }
        var host = look.hungOn == null ? parent : hostOf(bones.apply(look.hungOn), parent);
        if (body.getParent() != host) {
            host.attachChild(body); // hung from its bone once the model that has it is there, or anew after a swap
        }
        if (pieces.choose(look.pieceStateFor(holding), look.pieceStates)) {
            pieces.applyTo(body, Set.of());
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
     * The node it hangs from for a bone: the bone itself where it is a node or a joint's attachments, the node it
     * belongs to where it is a mesh; the thing's own node where there is no such bone.
     */
    private static Node hostOf(Spatial bone, Node parent) {
        if (bone instanceof Node node) {
            return node;
        }
        return bone != null && bone.getParent() != null ? bone.getParent() : parent;
    }

    /** What it draws now, or null for nothing. */
    Spatial body() {
        return body;
    }
}
