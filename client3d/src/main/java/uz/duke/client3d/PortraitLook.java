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
     * Where the little camera stands, in fractions of the model's own height
     * rather than in world units.
     *
     * <p>Deliberately relative. Heroes are not the same size — a model's height is
     * a fact about the file it came out of — and a portrait described in world
     * units would put the second hero's camera in his chest or a metre over his
     * head. Described as fractions, a hero nobody has tuned still gets a usable
     * portrait, and the per-hero numbers are a correction rather than a
     * requirement.
     *
     * @param head     how far up him the camera looks, 1 being the top of his head
     * @param distance how far back it stands, as a share of his height
     * @param yaw      degrees round him from straight ahead. Straight ahead is his
     *     own authored forward, because {@code Visuals.facing} has already been
     *     applied — so 0 means the same thing for every model
     * @param pitch    degrees above him, looking down; negative looks up at him
     * @param fov      the lens, in degrees. Narrow flatters a face, wide bends it
     */
    public record Camera(float head, float distance, float yaw, float pitch, float fov) {

        /** Head and shoulders, three-quarters on — the portrait everybody draws. */
        public static final Camera DEFAULT = new Camera(0.86f, 0.75f, 22f, -4f, 34f);
    }

    /**
     * Which clip each state plays, by the names the model's own libraries use.
     *
     * <p>Every one may be {@code null}, and a state with no clip falls back to
     * {@link #calm} — a portrait that stops dead because the kit has no death
     * animation would be worse than one that goes on standing.
     */
    public record Clips(String calm, String fight, String hurt, String dead, String levelUp) {

        /** For a game that has named none, which leaves the portrait standing still. */
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
