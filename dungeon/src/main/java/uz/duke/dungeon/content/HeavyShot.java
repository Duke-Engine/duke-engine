package uz.duke.dungeon.content;

/**
 * The drawn shot, as its {@code HeavyShot} block writes it: which projectile it becomes — the
 * same shaft, drawn bigger — and how fast that flies. Slower than an ordinary arrow, on
 * purpose: it is the one shot the player chose to spend, so it is the one worth watching.
 */
public record HeavyShot(String template, float speed) {

    /** What a block leaves out, and what a game with no such block gets. */
    static final HeavyShot DEFAULTS = new HeavyShot("HeavyArrow", 120f);
}
