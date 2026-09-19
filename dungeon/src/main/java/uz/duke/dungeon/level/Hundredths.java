package uz.duke.dungeon.level;

import java.math.BigDecimal;

/**
 * A figure kept as a whole count of hundredths, written as the decimal it is: {@code 0.15} is 15.
 *
 * <p>Read as a decimal rather than through a float: 0.15 has no float, and the nearest one times
 * a hundred is 14.999999. A figure with more places than this keeps is refused rather than
 * rounded, so what the file says is what the game does.
 */
public record Hundredths(int value) {

    public static final Hundredths ZERO = new Hundredths(0);

    public static Hundredths of(String written) {
        try {
            return new Hundredths(new BigDecimal(written.trim()).movePointRight(2).intValueExact());
        } catch (NumberFormatException | ArithmeticException wrong) {
            throw new IllegalArgumentException(written + " is not a number with at most two decimal places");
        }
    }
}
