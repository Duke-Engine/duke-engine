package uz.duke.dungeon.content;

/**
 * A second model a creature carries on one of its own bones — a bow, a blade, a
 * staff.
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
 */
public record Held(String model, String bone, float scale, float pitch, float yaw, float roll) {

    /** Empty-handed, which is what everything was before there was a bone to use. */
    public static final Held NOTHING = new Held(null, null, 1f, 0f, 0f, 0f);

    /** Whether there is anything to hang, and anywhere to hang it. */
    public boolean isCarried() {
        return model != null && bone != null;
    }

    /** The same, with the folder in front of the model. */
    public Held under(java.util.function.UnaryOperator<String> path) {
        return model == null ? this
                : new Held(path.apply(model), bone, scale, pitch, yaw, roll);
    }
}
