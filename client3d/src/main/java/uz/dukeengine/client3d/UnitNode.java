package uz.dukeengine.client3d;

import com.jme3.anim.AnimComposer;
import com.jme3.animation.AnimChannel;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import uz.dukeengine.core.view.UnitView;

/**
 * Everything the scene keeps per live unit — see {@link UnitScene}.
 */
final class UnitNode {
    Node root;
    /** The extra lights it wears for its group's scale, or null — see {@link LightScales}. */
    LightScales.Extra litBy;
    /** The clip it was busy with last frame, so a new one is seen starting. */
    String lastBusy;
    /** Whether its swing began with a wind-up, so the shot that ends it does not swing again. */
    boolean swungFromWindUp;
    Geometry ring;
    /** The disc the ring is laid as, over the ground under it — see {@code UnitScene.layRing}. */
    final GroundDisc ringDisc = new GroundDisc(24);
    /** The particle systems its look puts at its body's bones — see {@link BoneSystems}. */
    BoneSystems boneSystems;
    /** The line its body was laid along, for a thing drawn along one; null for the rest. */
    uz.dukeengine.core.thing.Span laidSpan;
    /**
     * How high over its feet its bar floats, measured off the body once.
     *
     * <p>Measured rather than assumed, because a creature kit's hero stands
     * three times taller than its rat — the old constant suited a capsule and
     * put the bar inside a hero's chest. Measured ONCE, because the answer
     * cannot change while the model does not, and re-measuring a bounding box
     * every frame for forty creatures is the sort of cost nobody goes looking
     * for afterwards.
     */
    float barTop;
    /**
     * Where and when it was first drawn. A thing that lay where it was put for a
     * while is a mark; a thing that moved is a shot -- see Landing.
     */
    float bornX;
    float bornZ;
    float bornAt;
    Spatial body;      // the shape a click has to hit
    /** The file the body was loaded from, so a change of condition is noticed. */
    String modelPath;
    /** Its pieces as its states have left them — kept for the thing, laid on again on a swapped model. */
    final Pieces pieces = new Pieces();
    /** Its clip as the words it holds choose it — see {@link Visuals.UnitVisual#clip}. */
    final WordClip wordClip = new WordClip();
    /** How high its model's top stands above the ground it is placed on, for one drawn rising as it is built. */
    float bodyTop;
    /** Its other models, each chosen and moved by its words on its own — see {@link Visuals.UnitVisual#layer}. */
    final java.util.List<ModelLayer> layers = new java.util.ArrayList<>();
    /** The weapon slots' bones its barrels were found by, as its words chose them. */
    java.util.Map<Integer, Visuals.WeaponBones> barrelBones;
    /** Its turrets' bones as its words chose them, or null to find them again. */
    java.util.Map<Integer, Visuals.TurretLook> turretLooks;
    /** The body its shadow was last laid on, and whether it casts one: laid again when either changes. */
    Spatial shadowBody;
    boolean casting;
    AnimComposer composer;
    AnimChannel legacyChannel;
    String currentAnim = "";
    /**
     * Until when a one-shot clip owns the model — a blow, or a flinch.
     *
     * <p>What a unit <em>is doing</em> is a loop and what <em>happens to it</em>
     * is not, and the second has to be able to interrupt the first without the
     * first taking it straight back. So while this is in the future the state
     * machine keeps its hands off.
     */
    float actionUntil;
    /**
     * Until when what it carries is out of its hands, or 0 for never.
     *
     * <p>Separate from {@link #actionUntil} although they are usually the same
     * number: a gesture can own the model without the hands having to be
     * empty, and every one-shot clip in the game except a two-handed spell
     * does exactly that.
     */
    float carryingAgainAt;
    /**
     * Until when a gesture the GAME asked for owns the model, or 0 for never.
     *
     * <p>Not the same as {@link #actionUntil}, which any one-shot sets. This
     * one outranks them: see {@code UnitScene.playOnce}.
     */
    float gestureUntil;
    /**
     * The health it had in the last snapshot: a drop is a blow that landed.
     */
    float lastHealth = Float.NaN;
    UnitView view;
    /** When each mark its words show by its bar began, in the window's seconds, as its strip is played from. */
    final java.util.Map<Integer, Float> markSince = new java.util.HashMap<>();
    /** The clip it died with, and the game's frame it died on: what it lies in while the world keeps it dead. */
    String deathClip;
    int diedOn;
}
