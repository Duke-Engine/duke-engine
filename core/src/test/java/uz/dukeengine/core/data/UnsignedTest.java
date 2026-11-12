package uz.dukeengine.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The written-out parsers give what the JDK gives, number or refusal. */
class UnsignedTest {

    private static final List<String> TEXTS = List.of("0", "1", "+7f", "FF", "ff00ff", "7fffffff", "80000000",
            "ffffffff", "100000000", "7fffffffffffffff", "8000000000000000", "ffffffffffffffff",
            "10000000000000000", "", "+", "-", "-0", "-1", "g", "12x", "0000000000000000000ff");

    @Test
    void parsesAsTheJdkDoes() {
        for (var radix : List.of(16, 10, 2)) {
            for (var text : TEXTS) {
                assertEquals(outcome(() -> Integer.parseUnsignedInt(text, radix)),
                        outcome(() -> Unsigned.parseInt(text, radix)), "int " + text + " radix " + radix);
                assertEquals(outcome(() -> Long.parseUnsignedLong(text, radix)),
                        outcome(() -> Unsigned.parseLong(text, radix)), "long " + text + " radix " + radix);
            }
        }
    }

    private static String outcome(java.util.function.Supplier<Object> parse) {
        try {
            return String.valueOf(parse.get());
        } catch (NumberFormatException e) {
            return "refused";
        }
    }
}
