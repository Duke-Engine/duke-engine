package uz.duke.dungeon.content;

/**
 * What a monster is drawn as: a model, the skin on it, and which animations it
 * plays.
 *
 * <p>Separate from {@link MonsterKind} because it answers a different question.
 * A kind's sense radius decides what the monster <em>does</em> and is read by the
 * simulation; none of this is, and none of it may be — the game must play the same
 * whether anyone is looking. Keeping the two apart is what stops the second from
 * drifting into the first.
 *
 * <p>A free creature kit ships fewer models than a dungeon needs kinds, so a kind
 * is a model <em>and</em> a skin <em>and</em> a size: two models with three
 * colourways each, at whatever height the creature is meant to be, is six monsters
 * that read differently across a dark room.
 *
 * @param model       the mesh, or {@code null} to fall back to a coloured shape
 * @param texture     the colour map to put on it
 * @param modelScale  what to multiply the model by — creature kits are authored
 *                    around a metre and the map is in ten-unit cells, so this is
 *                    large and has to be said per kind, since each kind is a
 *                    different height
 * @param tint        multiplied over the texture, packed {@code 0xRRGGBB}. White
 *                    leaves the skin alone; a wash of colour separates two
 *                    monsters that share one
 * @param idle        clip names, taken from the animation library named in the
 *                    settings; null falls back to the library's defaults
 */
public record MonsterLook(
        String model,
        String texture,
        float modelScale,
        int tint,
        String idle,
        String walk,
        String attack) {

    /** No art at all: this kind is drawn as a coloured shape, as everything was. */
    public static final MonsterLook NONE = new MonsterLook(null, null, 1f, 0xFFFFFF, null, null, null);

    /** Whether there is a model to draw rather than a shape. */
    public boolean hasModel() {
        return model != null;
    }

    /** The tint as AWT sees it. */
    public java.awt.Color awtTint() {
        return new java.awt.Color(tint);
    }

    /** This look with any unset clip name filled in from the game's defaults. */
    public MonsterLook withDefaults(String defaultIdle, String defaultWalk, String defaultAttack) {
        return new MonsterLook(model, texture, modelScale, tint,
                idle == null ? defaultIdle : idle,
                walk == null ? defaultWalk : walk,
                attack == null ? defaultAttack : attack);
    }
}
