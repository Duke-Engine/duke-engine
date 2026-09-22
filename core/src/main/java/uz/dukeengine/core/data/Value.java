package uz.dukeengine.core.data;

import java.util.List;

/** What follows the {@code =} of a field. */
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

    /** {@code Geometry = Cylinder}, its fields under it, and its {@code End}: one record, named by its class. */
    record Nested(Block block) implements Value {
    }

    /** {@code Modules = [}, a block for each item, each named by its class, then {@code ]}. */
    record NestedList(List<Block> blocks) implements Value {
        public NestedList {
            blocks = List.copyOf(blocks);
        }
    }

    /**
     * {@code Pieces = [}, {@code Minimap = Piece} and its fields for each entry, then {@code ]}: the
     * entries of a map whose values hold more than a line does. Every value is a {@link Nested}.
     */
    record NestedEntries(List<Field> entries) implements Value {
        public NestedEntries {
            entries = List.copyOf(entries);
        }
    }
}
