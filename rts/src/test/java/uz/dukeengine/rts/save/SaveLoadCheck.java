package uz.dukeengine.rts.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import uz.dukeengine.core.save.ModuleStates;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
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
        copyWithoutModules(played, forgetful);
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

    /** Everything a save holds of {@code world}: the world's own lines, its sides', each thing's modules, the rest. */
    static String stateOf(RtsSimulation world) {
        var text = new StringBuilder(GameSnapshot.save(world));
        var players = world.getPlayerList();
        for (int i = 0; i < players.getPlayerCount(); i++) {
            var side = new SaveOut();
            world.getRtsPlayer(i).save(side);
            text.append("SIDE ").append(i).append('|').append(players.getPlayer(i).getRelationships()).append('|')
                    .append(side.text()).append('\n');
        }
        var factory = world.getThingFactory().getModuleFactory();
        for (var thing : world.getObjects()) {
            text.append("MODULES ").append(thing.getId().value()).append('|')
                    .append(ModuleStates.save(thing, factory)).append('\n');
        }
        var core = new SaveOut();
        world.saveWorld(core);
        var rts = new SaveOut();
        world.saveRts(rts);
        var movers = new SaveOut();
        world.saveMovers(movers);
        return text.append("WORLD ").append(core.text()).append("\nRTS ").append(rts.text()).append("\nMOVERS ")
                .append(movers.text()).append('\n').toString();
    }

    private static void copy(RtsSimulation from, RtsSimulation to) {
        copyWithoutModules(from, to);
        var factory = to.getThingFactory().getModuleFactory();
        for (var thing : from.getObjects()) {
            ModuleStates.load(to.findObject(thing.getId()), ModuleStates.save(thing, factory), factory);
        }
    }

    /** The world's lines, its sides and what it and its RTS keep — its things' modules made afresh. */
    private static void copyWithoutModules(RtsSimulation from, RtsSimulation to) {
        GameSnapshot.load(GameSnapshot.save(from), to);
        for (var thing : from.getObjects()) {
            var copy = to.findObject(thing.getId());
            copy.setContained(thing.isContained()); // the snapshot's line for a thing will carry these
            copy.setSeesOut(thing.seesOut());
        }
        var players = from.getPlayerList();
        for (int i = 0; i < players.getPlayerCount(); i++) {
            for (var stance : players.getPlayer(i).getRelationships().entrySet()) {
                to.getPlayerList().getPlayer(i).setRelationshipTo(to.getPlayerList().getPlayer(stance.getKey()),
                        stance.getValue());
            }
            var side = new SaveOut();
            from.getRtsPlayer(i).save(side);
            to.getRtsPlayer(i).load(new SaveIn(side.text(), to, "SIDE"));
        }
        var rts = new SaveOut();
        from.saveRts(rts);
        to.loadRts(new SaveIn(rts.text(), to, "RTS"));
        var core = new SaveOut();
        from.saveWorld(core);
        to.loadWorld(new SaveIn(core.text(), to, "WORLD"));
        var movers = new SaveOut();
        from.saveMovers(movers);
        var in = new SaveIn(movers.text(), to, "MOVERS");
        to.loadMovers(in); // last: the obstacles it lays are the things' as the rest of the save left them
        in.expectEnd();
    }
}
