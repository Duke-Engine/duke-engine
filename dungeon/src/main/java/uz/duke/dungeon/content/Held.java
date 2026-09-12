package uz.duke.dungeon.content;

/**
 * One more model a creature carries on one of its own bones — a bow, a blade, a
 * staff, a shield, the arrows for the bow.
 *
 * <p>Character kits ship weapons apart from characters and rig a bone to hang
 * them on, and they are right to: one skeleton and a rack of weapons is every
 * armed skeleton there is, where a skeleton-with-an-axe is one of them. The bone
 * is what the clips move, so a weapon parented to it needs nothing kept in step.
 *
 * @param model the file, or {@code null} for a creature that carries nothing
 * @param bone  the bone it hangs on — KayKit's are {@code handslot.l} and
 *              {@code handslot.r}
 * @param scale what to multiply it by, when it and the body were not authored at
 *              the same size
 * @param pitch degrees of turn to put it the right way round. The bone gets it
 *              into the hand and settles nothing about which way round it goes:
 *              that is between the bone and the model, and a kit does not always
 *              lay every model out the same way. This pack's swords, axes and
 *              staves all run along their own {@code +Y} and its bow runs along
 *              {@code +Z}, so the bow is the one that needs turning
 * @param yaw   the same, about the second axis
 * @param roll  the same, about the third
 * @param x     how far to shift it off the bone, and the same kind of number as
 *              the three above: a fact about the art rather than a setting. Most
 *              things want none of it -- a bone puts a weapon in a hand and a
 *              hand is where a weapon goes. A quiver is the exception this pack
 *              forces: the rig has exactly two attachment points and both of
 *              them are hands, so a quiver hangs off the chest and has to be
 *              pushed back and up until it is over the shoulder
 * @param y     the same, on the second axis
 * @param z     the same, on the third
 */
public record Held(String model, String bone, float scale, float pitch, float yaw, float roll,
        float x, float y, float z) {

    /** Empty-handed, which is what everything was before there was a bone to use. */
    public static final Held NOTHING = new Held(null, null, 1f, 0f, 0f, 0f, 0f, 0f, 0f);

    /** Whether there is anything to hang, and anywhere to hang it. */
    public boolean isCarried() {
        return model != null && bone != null;
    }

    /** The same, with the folder in front of the model. */
    public Held under(java.util.function.UnaryOperator<String> path) {
        return model == null ? this
                : new Held(path.apply(model), bone, scale, pitch, yaw, roll, x, y, z);
    }
}
