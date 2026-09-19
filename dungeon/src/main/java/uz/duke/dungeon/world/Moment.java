package uz.duke.dungeon.world;

import uz.duke.core.data.Link;
import uz.duke.client3d.Effect;

/**
 * What one of the run's own moments plays on the hero: a level gained, the boss down, the hero
 * arriving on a floor. The client notices the moment; this says which effect it plays on him.
 *
 * @param name   which moment, in the client's word for it
 * @param effect the effect it plays, an {@code Effect} drawn in layers
 * @param scale  how much bigger than the effect is written it is drawn; 1 as written
 */
public record Moment(String name, @Link(Effect.class) String effect, float scale) {

    /** What a block leaves out. */
    public static final Moment DEFAULTS = new Moment("", "", 1f);
}
