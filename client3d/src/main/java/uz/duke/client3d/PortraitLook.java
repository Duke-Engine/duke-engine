package uz.duke.client3d;

/**
 * A creature drawn <em>alive</em> in the hero panel's frame: a second camera on a
 * copy of its model, rendered into the portrait every few frames.
 *
 * <p>Warcraft's portrait is not a picture, and that is the whole of why it reads
 * as a person rather than as an icon — it is a small scene with its own camera
 * close to the face and its own animation running in it. The frame then answers
 * two questions instead of one: not only "which corner of the screen is mine" but
 * "how is he doing", which is a question a player asks continuously and a drawing
 * cannot answer.
 *
 * <p>The same bargain as {@link PanelSkin} and {@link Tileset}: the client owns
 * the mechanism — where the little camera goes, what a <em>state</em> is, how
 * often the thing is drawn — and the game owns every value in it. In particular
 * the client never names a clip. A model's clips are the game's art, and three
 * other games use this client.
 *
 * <p><b>No model is named here.</b> The creature's model, its scale and the
 * libraries its clips come out of are already bound, once, in {@link Visuals}
 * under the same template name — so a portrait cannot drift out of step with the
 * creature it is the face of, and a second hero is a block of settings rather
 * than a second copy of his art.
 *
 * @param camera           where the little camera stands
 * @param clips            which clip each state plays
 * @param hurtBelowPercent below this much of its health it is {@code HURT}
 * @param hurtSpeed        how much faster the hurt clip runs — a kit rarely ships
 *     a tired stand, and speed says "labouring" without one
 */
public record PortraitLook(Camera camera, Clips clips, float hurtBelowPercent, float hurtSpeed) {

    /**
     * What the portrait can be showing.
     *
     * <p>The client's, not the game's: a state is a question about the world the
     * client is already reading out of the snapshot, and what a state <em>is</em>
     * decides whether its clip loops. The game answers each of them with a clip.
     */
    public enum State {
        /** Nothing happening: he stands and breathes. */
        CALM,
        /** He has something to fight — a ready stance, not a blow. */
        FIGHT,
        /** Under {@link #hurtBelowPercent} of his health. */
        HURT,
        /** He is down. Plays once and the frame holds where it left him. */
        DEAD,
        /** He has just gone up a level. Plays once, then back to whatever is true. */
        LEVEL_UP
    }

    /**
     * Where the little camera stands, in fractions of the creature's own height
     * rather than in world units.
     *
     * <p>Deliberately relative, and this is the part that makes one block serve
     * every creature in the game. A model's height is a fact about the file it
     * came out of — a hero, a skeleton and whatever is added next are all
     * different — and a camera described in world units would sit in one
     * creature's chest and a metre over another's head.
     *
     * <p><b>How much is in the frame is said outright.</b> {@code show} is the
     * share of the creature that fills the frame from top to bottom, and the
     * distance is worked out from it and the lens. Said the other way round — a
     * distance and a lens, with the framing left to fall out of them — the two
     * numbers have to be re-tuned together for every creature, and widening the
     * lens silently zooms out.
     *
     * @param head  how far up the creature the camera looks, 1 being the top of it
     * @param show  how much of its height fills the frame: 0.5 is its top half
     * @param yaw   degrees round it from straight ahead. Straight ahead is its own
     *     authored forward, because {@code Visuals.facing} has already been
     *     applied — so 0 means the same thing for every model
     * @param pitch degrees above it, looking down; negative looks up at it
     * @param fov   the lens, in degrees. Narrow flatters a face, wide bends it —
     *     and it no longer changes how much is in the frame, only how it is bent
     */
    public record Camera(float head, float show, float yaw, float pitch, float fov) {

        /**
         * Head and shoulders, three-quarters on — the portrait everybody draws.
         *
         * <p>Measured rather than chosen. The kit these were written for draws its
         * characters with a head nearly half their height: the jaw sits at 0.55 of
         * the model and the top of the hair at 1.0. Framing from 0.42 to 1.06 is
         * therefore the chest up, with the head filling about seven-tenths of the
         * frame and a little air over it.
         */
        public static final Camera DEFAULT = new Camera(0.74f, 0.64f, 22f, -4f, 34f);

        /** How far back the camera has to stand to frame {@code show} of this one. */
        float distanceFor(float standingHeight) {
            float visible = Math.max(0.001f, show * standingHeight);
            float halfLens = com.jme3.math.FastMath.DEG_TO_RAD * Math.clamp(fov, 1f, 170f) / 2f;
            return visible / 2f / com.jme3.math.FastMath.tan(halfLens);
        }
    }

    /**
     * Which clip each state plays, by the names the model's own libraries use.
     *
     * <p>Every one may be {@code null}, and a creature's own clips are used where
     * the portrait names none — its walking-about idle for standing, its death for
     * dying. Which is what lets one block serve every monster in the game: a
     * skeleton needs no portrait of its own to breathe in the frame and fall over
     * in it, because those two clips are already bound on it.
     *
     * <p>Naming them is for the states a creature has no clip for anyway: a bow
     * held ready, which is not the same as the blow a unit's attack clip is, and a
     * flourish for a level nothing but a hero has.
     */
    public record Clips(String calm, String fight, String hurt, String dead, String levelUp) {

        /** For a game that names none and leaves every creature its own. */
        public static final Clips NONE = new Clips(null, null, null, null, null);
    }

    public PortraitLook {
        camera = camera == null ? Camera.DEFAULT : camera;
        clips = clips == null ? Clips.NONE : clips;
        hurtBelowPercent = Math.clamp(hurtBelowPercent, 0f, 100f);
        // A speed of zero is a portrait frozen mid-breath, which reads as a crash.
        hurtSpeed = hurtSpeed <= 0f ? 1f : hurtSpeed;
    }

    /** The clip for a state, or {@code null} when the game named none for it. */
    public String clip(State state) {
        return switch (state) {
            case CALM -> clips.calm();
            case FIGHT -> clips.fight();
            case HURT -> clips.hurt();
            case DEAD -> clips.dead();
            case LEVEL_UP -> clips.levelUp();
        };
    }

    /**
     * Whether a state's clip runs on a loop.
     *
     * <p>The client's to decide, because it is what the state <em>means</em>
     * rather than anything about the art: standing, fighting and bleeding are
     * conditions and go round; dying and going up a level are things that happen
     * and are over.
     */
    public boolean loops(State state) {
        return state != State.DEAD && state != State.LEVEL_UP;
    }

    /** How fast that state's clip runs. Only being hurt changes it. */
    public float speed(State state) {
        return state == State.HURT ? hurtSpeed : 1f;
    }

    /** Whether the game described a portrait at all, or only left the frame empty. */
    public boolean any() {
        return clips.calm() != null;
    }
}
