package uz.duke.core.thing;

/**
 * A unique handle for a {@link GameObject}, ported from SAGE's {@code ObjectID}.
 *
 * <p>Ids are assigned in creation order by {@code GameLogic}, which makes them a
 * stable, deterministic identity that can be referenced across the network and
 * in saved games — far safer than passing object references around.
 */
public record ObjectId(int value) {

    /** The id used for "no object". */
    public static final ObjectId INVALID = new ObjectId(0);

    public boolean isValid() {
        return value != 0;
    }
}
