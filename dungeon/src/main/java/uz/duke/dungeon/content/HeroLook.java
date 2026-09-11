package uz.duke.dungeon.content;

import java.util.List;

/**
 * What the hero is drawn as.
 *
 * <p>Separate from {@link MonsterLook} because he takes his clips from several
 * libraries rather than one, and because there is only ever one of him — a kind
 * of monster is a template the generator picks from, and he is not. The shape is
 * otherwise the same, and it was not always: he used to be described as one file
 * per movement, because that is how an animation site hands its work out, and
 * every one of those files carried the same exporter-generated clip name so the
 * file was the only thing saying which was the run. A character <em>kit</em> is
 * the other way round — a handful of libraries, each holding dozens of clips
 * under names that mean something — and so the names moved into this file and the
 * four fields became two.
 *
 * <p>Named, and so repeatable: the name is the creature template in
 * {@code creatures.ini} this describes, and it is the same name his skills are
 * already headed with. So a second hero is a block in each of the two files and
 * no Java at all — which is the promise the rest of the game's data layer makes
 * about monsters, themes and skills, and it was the one thing here that could not
 * keep it.
 *
 * @param name       the creature template this is the look of — {@code Hero}, and
 *                   whatever the next one is called
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
 * @param held       the bow in his hand — see {@link Held}
 */
public record HeroLook(
        String name,
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
        Held held) {

    public HeroLook {
        animations = List.copyOf(animations);
    }

    /** No art: he is drawn as a shape, as he was before there was a model. */
    public static final HeroLook NONE = new HeroLook("Hero", null, null, 1f, 0f, List.of(),
            null, null, null, null, null, Held.NOTHING);

    public boolean hasModel() {
        return model != null;
    }

    /** Every clip he asks his libraries for, so a test can check they are all there. */
    public List<String> clips() {
        return java.util.stream.Stream.of(idle, walk, attack, hurt, death)
                .filter(java.util.Objects::nonNull).toList();
    }
}
