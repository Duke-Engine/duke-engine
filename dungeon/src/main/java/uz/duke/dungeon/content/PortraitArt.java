package uz.duke.dungeon.content;

import uz.duke.core.data.Clip;
import java.util.List;

/**
 * What a creature looks like in the panel's frame, alive.
 *
 * <p>Named after the creature template it is the face of, and repeatable for the
 * same reason {@link HeroLook} is: the frame follows whatever is selected, so a
 * second hero — or, one day, a boss worth a portrait of his own — is a block in
 * his own file and nothing in Java.
 *
 * <p><b>It names no art.</b> The model, its scale and the libraries its clips come
 * out of are already in the hero's own block, under this same name. What is here
 * is the two things a portrait needs that a creature walking about does not:
 * where the little camera stands, and which clip each of its states plays.
 *
 * <p>The camera is measured in fractions of the creature's own height rather than
 * in world units — see the block's comment in the file. Creatures are whatever
 * size their kit made them, and a camera written in world units would sit in one's
 * chest and a metre over another's head.
 *
 * @param name             the creature template this is the portrait of, or the
 *                         empty string for the one every selectable creature gets
 * @param head             how far up it the camera looks, 1 being the top of it
 * @param show             how much of its height fills the frame: 0.5 is its top
 *                         half
 * @param yaw              degrees round him from his own forward
 * @param pitch            degrees above him looking down; negative looks up
 * @param fov              the lens, in degrees
 * @param calm             the clip he stands in
 * @param fight            the clip he holds while he has something to fight
 * @param hurt             the clip he wears when he is nearly finished
 * @param dead             the clip he falls in — played once, and the frame keeps
 *                         where it left him
 * @param levelUp          the clip he celebrates a level with — played once
 * @param hurtBelowPercent below this much of his health he counts as hurt
 * @param hurtSpeed        how much faster the hurt clip runs. A kit ships no tired
 *                         stand, and speed says "labouring" without one
 */
public record PortraitArt(
        String name,
        float head,
        float show,
        float yaw,
        float pitch,
        float fov,
        @Clip String calm,
        @Clip String fight,
        @Clip String hurt,
        @Clip String dead,
        @Clip String levelUp,
        float hurtBelowPercent,
        float hurtSpeed) {

    /** What a {@code Portrait} block leaves out: the framing that suits most things standing up. */
    static final PortraitArt DEFAULTS = new PortraitArt(null, 0.74f, 0.64f, 22f, -4f, 34f,
            null, null, null, null, null, 30f, 1.5f);

    /** This framing, as the face of {@code name}: a unit's block writes it without saying whose. */
    public PortraitArt named(String name) {
        return new PortraitArt(name, head, show, yaw, pitch, fov, calm, fight, hurt, dead, levelUp,
                hurtBelowPercent, hurtSpeed);
    }

    /** Every clip it asks for, so a test can check they are all in his libraries. */
    public List<String> clips() {
        return java.util.stream.Stream.of(calm, fight, hurt, dead, levelUp)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

}
