package uz.duke.dungeon.content;

import java.util.List;

/**
 * What the hero is drawn as.
 *
 * <p>Separate from {@link MonsterLook} because he carries things a monster does
 * not — a bow in his hand — and because his clips come from more than one
 * library. The shape is otherwise the same one, and it was not always: he used to
 * be described as one file per movement, because that is how an animation site
 * hands its work out, and every one of those files carried the same
 * exporter-generated clip name so the file was the only thing saying which was
 * the run. A character <em>kit</em> is the other way round — a handful of
 * libraries, each holding dozens of clips under names that mean something — and
 * so the names moved into this file and the four fields became two.
 *
 * <p>He and the bestiary sit on different skeletons, which is fine and worth
 * saying: a creature is animated from a library built on <em>its</em> rig, and
 * nothing requires every creature in a game to share one.
 *
 * @param model      the mesh, or {@code null} to fall back to a coloured shape
 * @param texture    a colour map to override the model's own, or {@code null} to
 *                   keep whatever it shipped with
 * @param modelScale what to multiply the model by to reach its creature's height
 * @param facing     degrees of turn to bring the model's forward onto the
 *                   engine's. A quarter out and he walks sideways; a half out and
 *                   he moonwalks — so it is data, and fixing it is an edit
 * @param animations the libraries his clips are taken from, in the order named.
 *                   Several, because a kit sorts its work by what the movement is
 *                   for — standing and dying in one file, walking in another, a
 *                   bow in a third
 * @param idle       the clip he stands in
 * @param walk       the clip he moves in
 * @param attack     the clip he shoots in
 * @param hurt       the clip he flinches in, or {@code null} for a hero who does
 *                   not flinch
 * @param death      the clip he falls in
 * @param holds      a second model carried on one of his bones — a bow — or
 *                   {@code null} for a hero who carries nothing
 * @param heldIn     the bone it hangs on. Character kits ship a bone for exactly
 *                   this and it is worth using: the hand is what the clips move,
 *                   so a weapon parented to it needs nothing kept in step
 * @param heldScale  what to multiply the held model by, when it and the body were
 *                   not authored at the same size
 */
public record HeroLook(
        String model,
        String texture,
        float modelScale,
        float facing,
        List<String> animations,
        String idle,
        String walk,
        String attack,
        String hurt,
        String death,
        String holds,
        String heldIn,
        float heldScale) {

    public HeroLook {
        animations = List.copyOf(animations);
    }

    /** No art: he is drawn as a shape, as he was before there was a model. */
    public static final HeroLook NONE = new HeroLook(null, null, 1f, 0f, List.of(),
            null, null, null, null, null, null, null, 1f);

    public boolean hasModel() {
        return model != null;
    }

    /** Every clip he asks his libraries for, so a test can check they are all there. */
    public List<String> clips() {
        return java.util.stream.Stream.of(idle, walk, attack, hurt, death)
                .filter(java.util.Objects::nonNull).toList();
    }
}
