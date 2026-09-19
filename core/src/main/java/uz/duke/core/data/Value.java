package uz.duke.core.data;

import java.util.List;

/** What follows the {@code =} of a field: one value, or a list of them. */
public sealed interface Value {

    /** Everything after {@code =} as written, or a quoted string without its quotes. */
    record Text(String text) implements Value {
    }

    /** {@code [a, b, c]}: each item as written, a quoted one without its quotes. */
    record Items(List<String> items) implements Value {
        public Items {
            items = List.copyOf(items);
        }
    }
}
