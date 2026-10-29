package uz.dukeengine.core.save;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.thing.GameObject;

/**
 * A module that writes what it holds into a save and reads it back, ported in spirit from SAGE's {@code Snapshot::xfer}:
 * the same values in the same order both ways, so a world loaded from a save goes on frame for frame as the saved one
 * would have.
 *
 * <p>What is written is the module's own state as the simulation left it at a frame's end — what its data does not
 * already say. Its data comes back from its template, and its thing's place, health, words and statuses from the
 * thing's own line, so neither is written again. Drawing alone is never written: nothing the simulation decides reads it.
 *
 * <p>Every module a saved thing carries is either this or declared {@link Stateless}; any other is refused at the save
 * by name ({@link UnsavedModuleException}), never left out. A module added to its thing while the match ran — an order,
 * a site — is made again on a load by the {@link Reviver} its class is registered with
 * ({@code ModuleFactory.revives}); one from the thing's template is made by the template and handed what it wrote.
 */
public interface Saveable {

    /** Write what this module holds, in an order {@link #load} reads it back in. */
    void save(SaveOut out);

    /**
     * Read back what {@link #save} wrote, into a module its template has just made afresh on its thing, every thing of
     * the save already in the world. A value left unread is refused.
     */
    void load(SaveIn in);

    /**
     * Makes a module that was added to its thing while the match ran, on {@code owner}, from what it saved of itself:
     * it reads every value its {@link #save} wrote — what it was made with first — and returns the module, not yet on
     * its thing. A {@link Stateless} one reads nothing.
     */
    @FunctionalInterface
    interface Reviver<M extends Module> {
        M revive(GameObject owner, SaveIn in);
    }
}
