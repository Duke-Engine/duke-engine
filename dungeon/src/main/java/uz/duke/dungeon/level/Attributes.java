package uz.duke.dungeon.level;

import java.util.Arrays;

/**
 * How much of each attribute, held in <b>tenths</b> of a point, in the order the file
 * lists the attributes.
 *
 * <p>Tenths because a level adds 1.8 of something and a hero is fifteen levels of it.
 * Summed as floats that is a number two machines can disagree about in its last bit, and
 * what it becomes — his health — is inside the checksum. As whole tenths it is the same
 * integer everywhere, and every operation here is exact or fails loudly.
 *
 * <p>A place past the end reads as nothing, so {@link #NONE} is nothing of every
 * attribute however many the file names.
 */
public final class Attributes {

    /** A point is this many of the units these are held in. */
    public static final int TENTHS = 10;

    public static final Attributes NONE = new Attributes(new int[0]);

    private final int[] tenths;

    private Attributes(int[] tenths) {
        this.tenths = tenths;
    }

    /** Tenths, in the file's order. */
    public static Attributes of(int... tenths) {
        return new Attributes(tenths.clone());
    }

    /** Whole points, as an item or a test would name them. */
    public static Attributes ofWhole(int... points) {
        var tenths = new int[points.length];
        for (int i = 0; i < points.length; i++) {
            tenths[i] = Math.multiplyExact(points[i], TENTHS);
        }
        return new Attributes(tenths);
    }

    /** The attribute at this place in the file's list, in tenths. */
    public int at(int index) {
        return index >= 0 && index < tenths.length ? tenths[index] : 0;
    }

    /** How many places are held; every one past it is nothing. */
    public int size() {
        return tenths.length;
    }

    public Attributes plus(Attributes other) {
        var sum = new int[Math.max(tenths.length, other.tenths.length)];
        for (int i = 0; i < sum.length; i++) {
            sum[i] = Math.addExact(at(i), other.at(i));
        }
        return new Attributes(sum);
    }

    /** This many times over, as one multiplication rather than a running sum. */
    public Attributes times(int count) {
        var product = new int[tenths.length];
        for (int i = 0; i < product.length; i++) {
            product[i] = Math.multiplyExact(tenths[i], count);
        }
        return new Attributes(product);
    }

    /** What the panel shows: whole points, rounded down. */
    public int whole(int index) {
        return Math.floorDiv(at(index), TENTHS);
    }

    /** Equal when every attribute is, a missing place counting as nothing. */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Attributes that)) {
            return false;
        }
        for (int i = 0; i < Math.max(tenths.length, that.tenths.length); i++) {
            if (at(i) != that.at(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int end = tenths.length;
        while (end > 0 && tenths[end - 1] == 0) {
            end--;
        }
        return Arrays.hashCode(Arrays.copyOf(tenths, end));
    }

    @Override
    public String toString() {
        return "Attributes" + Arrays.toString(tenths);
    }
}
