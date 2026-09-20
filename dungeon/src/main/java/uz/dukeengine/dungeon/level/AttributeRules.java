package uz.dukeengine.dungeon.level;

import java.util.List;

/**
 * Every attribute the file describes, in its order, and what a point of a primary is
 * worth on a blow.
 *
 * <p>Every coefficient is held in <b>hundredths</b> (0.15 of speed a point is 15) and
 * every attribute in tenths, so each figure is one sum of integer products divided once.
 * Nothing is accumulated and nothing depends on the order things happened in: the same
 * attributes give the same bits on every machine.
 *
 * @param attributes       the file's list; a place in it is a place in {@link Attributes}
 * @param damagePerPrimary attack a point of his primary attribute adds, in hundredths
 */
public record AttributeRules(List<Attribute> attributes, int damagePerPrimary) {

    /** A coefficient's unit is this many of the numbers it is held in. */
    public static final int HUNDREDTHS = 100;

    public static final AttributeRules NONE = new AttributeRules(List.of(), 0);

    private static final long SCALE = (long) Attributes.TENTHS * HUNDREDTHS;

    public AttributeRules {
        attributes = List.copyOf(attributes);
    }

    /** Where the attribute the file calls {@code word} stands in the list, or -1. */
    public int indexOf(String word) {
        for (int i = 0; i < attributes.size(); i++) {
            if (attributes.get(i).isNamed(word)) {
                return i;
            }
        }
        return -1;
    }

    /** Maximum health everything he has is worth, in whole points, rounded down. */
    public int health(Attributes points) {
        long sum = 0;
        for (int i = 0; i < attributes.size(); i++) {
            sum = Math.addExact(sum, Math.multiplyExact((long) points.at(i),
                    attributes.get(i).healthPerPoint().value()));
        }
        return Math.toIntExact(Math.floorDiv(sum, SCALE));
    }

    /** Maximum mana everything he has is worth, in whole points, rounded down. */
    public int mana(Attributes points) {
        long sum = 0;
        for (int i = 0; i < attributes.size(); i++) {
            sum = Math.addExact(sum, Math.multiplyExact((long) points.at(i),
                    attributes.get(i).manaPerPoint().value()));
        }
        return Math.toIntExact(Math.floorDiv(sum, SCALE));
    }

    /** Movement speed everything he has is worth: one exact integer, divided once. */
    public float speed(Attributes points) {
        long sum = 0;
        for (int i = 0; i < attributes.size(); i++) {
            sum = Math.addExact(sum, Math.multiplyExact((long) points.at(i),
                    attributes.get(i).speedPerPoint().value()));
        }
        return sum / (float) SCALE;
    }

    /**
     * What his primary attribute adds to his attack.
     *
     * <p>Only the primary. Strength on an archer is health and nothing else; strength on
     * a knight is health and his swing both, which is the whole of what being his
     * primary means.
     *
     * @param primary where his primary stands in the list, or -1 for none
     */
    public float attack(Attributes points, int primary) {
        if (primary < 0) {
            return 0f;
        }
        return Math.multiplyExact((long) points.at(primary), damagePerPrimary) / (float) SCALE;
    }
}
