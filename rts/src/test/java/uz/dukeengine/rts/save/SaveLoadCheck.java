package uz.dukeengine.rts.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import uz.dukeengine.core.save.ModuleStates;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.RtsSimulation;

/**
 * The promise a save keeps, checked: a world played some frames, saved and loaded into a fresh one made by the same
 * rules, sums the same and saves the same; and the two, played on alike, sum and save the same on every frame after.
 */
final class SaveLoadCheck {

    private SaveLoadCheck() {
    }

    /**
     * Two worlds from {@code rules}: the first given {@code setup} and played {@code before} frames, then saved and
     * loaded into the second; both played {@code after} frames more. {@code orders} is told each frame, before it is
     * played, with the world and the frame — what a player would order then — in both alike. What was saved.
     */
    static <W extends RtsSimulation> String resumes(Supplier<W> rules, Consumer<W> setup, BiConsumer<W, Integer> orders,
            int before, int after) {
        var played = rules.get();
        setup.accept(played);
        play(played, orders, before);
        var saved = stateOf(played);
        var loaded = rules.get();
        copy(played, loaded);
        assertEquals(played.checksum(), loaded.checksum(), "the loaded world sums as the saved one");
        assertEquals(saved, stateOf(loaded), "and saves the same again");
        for (int frame = 0; frame < after; frame++) {
            play(played, orders, 1);
            play(loaded, orders, 1);
            assertEquals(played.checksum(), loaded.checksum(), "frame " + played.getFrame() + " sums the same");
        }
        assertEquals(stateOf(played), stateOf(loaded), "and the two save the same at the end");
        return saved;
    }

    /**
     * {@link #resumes}, and the same world loaded with its modules made afresh from their templates instead parts from
     * the played one within {@code after} frames: what the modules hold is what keeps the two together.
     */
    static <W extends RtsSimulation> String resumesOnlyWithItsModules(Supplier<W> rules, Consumer<W> setup,
            BiConsumer<W, Integer> orders, int before, int after) {
        var saved = resumes(rules, setup, orders, before, after);
        var played = rules.get();
        setup.accept(played);
        play(played, orders, before);
        var forgetful = rules.get();
        copyWithoutModules(rules, played, forgetful);
        boolean parted = false;
        for (int frame = 0; frame < after && !parted; frame++) {
            play(played, orders, 1);
            play(forgetful, orders, 1);
            parted = played.checksum() != forgetful.checksum();
        }
        if (!parted) {
            assertNotEquals(stateOf(played), stateOf(forgetful),
                    "with its modules made afresh the world should part from the played one");
        }
        return saved;
    }

    private static <W extends RtsSimulation> void play(W world, BiConsumer<W, Integer> orders, int frames) {
        for (int i = 0; i < frames; i++) {
            orders.accept(world, world.getFrame());
            world.update();
        }
    }

    /** Everything a save holds of {@code world}. */
    static String stateOf(RtsSimulation world) {
        return GameSnapshot.save(world);
    }

    private static void copy(RtsSimulation from, RtsSimulation to) {
        GameSnapshot.load(GameSnapshot.save(from), to);
    }

    /**
     * {@code from}'s save loaded into {@code to} with every thing's modules as its template makes them afresh — each
     * {@code MODULES} line put in place by one written of a thing just made, in a world of {@code rules} of its own.
     */
    private static <W extends RtsSimulation> void copyWithoutModules(Supplier<W> rules, RtsSimulation from,
            RtsSimulation to) {
        var scratch = rules.get();
        var factory = scratch.getThingFactory().getModuleFactory();
        var text = new StringBuilder();
        for (var line : GameSnapshot.save(from).split("\n")) {
            if (line.startsWith("MODULES ")) {
                var thing = from.findObject(new ObjectId(Integer.parseInt(line.substring(8, line.indexOf('|')))));
                var fresh = scratch.restoreObject(thing.getTemplate(), thing.getId());
                line = "MODULES " + thing.getId().value() + "|" + ModuleStates.save(fresh, factory);
            }
            text.append(line).append('\n');
        }
        GameSnapshot.load(text.toString(), to);
    }
}
