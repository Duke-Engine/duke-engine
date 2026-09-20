package uz.dukeengine.core.module;

/**
 * Immutable configuration for a {@link Module}, ported from SAGE's
 * {@code ModuleData}.
 *
 * <p>One {@code ModuleData} is read from a module's block and shared by every instance built
 * from the same template — it is the "class" half of a module, while the {@link Module} itself
 * holds the per-object mutable state. Each module's data is a record nested in its class as
 * {@code Data}, and the block is named after the class: {@code MoveUpdate … End} is a
 * {@code MoveUpdate.Data}, its fields the record's components.
 */
public interface ModuleData {
}
