package uz.duke.core.data;

import java.util.List;

/**
 * One block of a {@code .duke} file: the word that opens it, its fields and the blocks written
 * inside it, in file order. Syntax only; what the words mean is the {@link Binder}'s.
 */
public record Block(String word, List<Field> fields, List<Block> blocks, String source, int line) {

    public Block {
        fields = List.copyOf(fields);
        blocks = List.copyOf(blocks);
    }

    /** A line of it as an error names it: {@code data/units/brute.duke:12}. */
    public String at(int line) {
        return source + ":" + line;
    }
}
