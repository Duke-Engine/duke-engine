package uz.duke.dungeon.content;

/**
 * What the hero is drawn as.
 *
 * <p>Separate from {@link MonsterLook} because the two get their movement in
 * different shapes, and pretending otherwise would put two meanings on one field.
 * The bestiary shares one library and picks clips out of it <em>by name</em>; the
 * hero has one file per movement, because that is how animation sites hand their
 * work out — and every one of those files carries the same exporter-generated
 * clip name, so the file is the only thing that says which is the run.
 *
 * <p>The two also sit on different skeletons, which is fine and worth saying: a
 * creature is animated from a library built on <em>its</em> rig, and nothing
 * requires every creature in a game to share one.
 *
 * @param model      the mesh, or {@code null} to fall back to a coloured shape
 * @param texture    a colour map to override the model's own, or {@code null} to
 *                   keep whatever it shipped with
 * @param modelScale what to multiply the model by to reach its creature's height
 * @param facing     degrees of turn to bring the model's forward onto the
 *                   engine's. A quarter out and he walks sideways; a half out and
 *                   he moonwalks — so it is data, and fixing it is an edit
 * @param idleFrom   the file holding the standing animation
 * @param walkFrom   the file holding the moving animation
 * @param attackFrom the file holding the swing
 */
public record HeroLook(
        String model,
        String texture,
        float modelScale,
        float facing,
        String idleFrom,
        String walkFrom,
        String attackFrom,
        String deathFrom) {

    /** The names the game gives these clips once they are on him. */
    public static final String IDLE = "Idle";
    public static final String WALK = "Walk";
    public static final String ATTACK = "Attack";
    public static final String DEATH = "Death";

    /** No art: he is drawn as a shape, as he was before there was a model. */
    public static final HeroLook NONE =
            new HeroLook(null, null, 1f, 0f, null, null, null, null);

    public boolean hasModel() {
        return model != null;
    }
}
