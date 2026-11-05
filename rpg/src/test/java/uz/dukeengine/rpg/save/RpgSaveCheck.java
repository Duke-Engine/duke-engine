package uz.dukeengine.rpg.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import uz.dukeengine.core.save.ModuleStates;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rpg.RpgModules;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.fight.Bow;
import uz.dukeengine.rpg.fight.EyesOnly;
import uz.dukeengine.rpg.fight.FallingUpdate;
import uz.dukeengine.rpg.fight.Swing;
import uz.dukeengine.rpg.hero.FountainUpdate;
import uz.dukeengine.rpg.hero.GrowableBody;
import uz.dukeengine.rpg.hero.Recovery;
import uz.dukeengine.rpg.skill.MendingUpdate;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SummoningUpdate;

/**
 * The promise a save keeps, checked for a world the RPG library plays in: played some frames, saved (every thing's
 * modules as {@link ModuleStates} writes them, the world's own lines as {@code GameLogic} does) and loaded into a fresh
 * one made by the same rules, it sums the same and saves the same; and the two, played on alike, sum and save the same
 * on every frame after.
 */
public final class RpgSaveCheck {

    private RpgSaveCheck() {
    }

    /** A world with every module of the library registered as a game registers them, and its revivers. */
    public static RpgWorld world(Map<String, List<Skill>> skills, Seal seal) {
        var world = new RpgWorld(skills, seal);
        var factory = world.getThingFactory().getModuleFactory();
        factory.register(Recovery.Data.class, (owner, _) -> new Recovery(owner))
                .register(GrowableBody.Data.class, (owner, data) -> new GrowableBody(owner, data))
                .register(FountainUpdate.Data.class, (owner, data) -> new FountainUpdate(owner, data, seal))
                .register(FallingUpdate.Data.class, (owner, _) -> new FallingUpdate(owner, seal))
                .register(MendingUpdate.Data.class, MendingUpdate::new)
                .register(SummoningUpdate.Data.class, (owner, _) -> new SummoningUpdate(owner, (risen, level) -> { }))
                .register(Swing.Data.class, (owner, _) -> new Swing(owner, seal))
                .register(Bow.Data.class, (owner, data) -> new Bow(owner, data, new Bow.Defaults("Arrow", 200f, 0f)))
                .register(EyesOnly.Data.class, (owner, _) -> new EyesOnly(owner, 40f));
        RpgModules.registerRevivers(factory);
        return world;
    }

    /**
     * Two worlds from {@code rules}: the first given {@code setup} and played {@code before} frames, then saved and
     * loaded into the second; both played {@code after} frames more. {@code orders} is told each frame, before it is
     * played, with the world and the frame, in both alike. What was saved.
     */
    public static String resumes(Supplier<RpgWorld> rules, Consumer<RpgWorld> setup,
            BiConsumer<RpgWorld, Integer> orders, int before, int after) {
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
            assertEquals(stateOf(played), stateOf(loaded), "frame " + played.getFrame() + " saves the same");
        }
        return saved;
    }

    /**
     * {@link #resumes}, and the same world loaded with its modules made afresh from their templates parts from the
     * played one within {@code after} frames: what the modules hold is what keeps the two together.
     */
    public static String resumesOnlyWithItsModules(Supplier<RpgWorld> rules, Consumer<RpgWorld> setup,
            BiConsumer<RpgWorld, Integer> orders, int before, int after) {
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
            parted = !stateOf(played).equals(stateOf(forgetful));
        }
        assertNotEquals(stateOf(played), stateOf(forgetful),
                "with its modules made afresh the world should part from the played one");
        return saved;
    }

    /** A fresh world of {@code rules} with {@code played} saved and loaded into it. */
    public static RpgWorld reloaded(Supplier<RpgWorld> rules, RpgWorld played) {
        var loaded = rules.get();
        copy(played, loaded);
        assertEquals(played.checksum(), loaded.checksum(), "the loaded world sums as the saved one");
        assertEquals(stateOf(played), stateOf(loaded), "and saves the same again");
        return loaded;
    }

    private static void play(RpgWorld world, BiConsumer<RpgWorld, Integer> orders, int frames) {
        for (int i = 0; i < frames; i++) {
            orders.accept(world, world.getFrame());
            world.update();
        }
    }

    /** Everything a save holds of {@code world}, as one text. */
    public static String stateOf(RpgWorld world) {
        var text = new StringBuilder();
        text.append("FRAME ").append(world.getFrame()).append('\n');
        text.append("NEXTID ").append(world.getNextObjectId()).append('\n');
        text.append("RANDOM ").append(world.random().state()).append('\n');
        var factory = world.getThingFactory().getModuleFactory();
        for (var thing : world.getObjects()) {
            text.append("OBJECT ").append(thing.getId().value()).append('|').append(thing.getTemplate().name())
                    .append('|').append(thing.getPlayerIndex()).append('|').append(thing.getPosition()).append('|')
                    .append(thing.getOrientation()).append('|').append(statusesOf(thing)).append('|')
                    .append(String.join(",", thing.getConditions())).append('|').append(thing.hasDied()).append('|')
                    .append(thing.isDestroyed()).append('\n');
            text.append("MODULES ").append(thing.getId().value()).append('|')
                    .append(ModuleStates.save(thing, factory)).append('\n');
        }
        var out = new SaveOut();
        world.saveWorld(out);
        return text.append("WORLD ").append(out.text()).append('\n').toString();
    }

    private static String statusesOf(uz.dukeengine.core.thing.GameObject thing) {
        var statuses = new StringBuilder();
        for (var status : ObjectStatus.values()) {
            if (thing.hasStatus(status)) {
                statuses.append(status.name()).append(',');
            }
        }
        return statuses.toString();
    }

    private static void copy(RpgWorld from, RpgWorld to) {
        copyThings(from, to);
        var modules = from.getThingFactory().getModuleFactory();
        for (var thing : from.getObjects()) {
            ModuleStates.load(to.findObject(thing.getId()), ModuleStates.save(thing, modules),
                    to.getThingFactory().getModuleFactory());
        }
        finish(from, to);
    }

    /** {@code from}'s things loaded into {@code to} with their modules as their templates make them afresh. */
    private static void copyWithoutModules(Supplier<RpgWorld> rules, RpgWorld from, RpgWorld to) {
        copyThings(from, to);
        var scratch = rules.get();
        var modules = scratch.getThingFactory().getModuleFactory();
        for (var thing : from.getObjects()) {
            var fresh = scratch.restoreObject(thing.getTemplate(), thing.getId());
            ModuleStates.load(to.findObject(thing.getId()), ModuleStates.save(fresh, modules),
                    to.getThingFactory().getModuleFactory());
        }
        finish(from, to);
    }

    private static void copyThings(RpgWorld from, RpgWorld to) {
        to.setFrame(from.getFrame());
        to.setNextObjectId(from.getNextObjectId());
        to.random().restore(from.random().state());
        for (var thing : from.getObjects()) {
            var copy = to.restoreObject(to.getThingFactory().findTemplate(thing.getTemplate().name()), thing.getId());
            copy.setPlayerIndex(thing.getPlayerIndex());
            copy.setPosition(thing.getPosition());
            copy.setOrientation(thing.getOrientation());
            for (var status : ObjectStatus.values()) {
                if (copy.hasStatus(status) && !thing.hasStatus(status)) {
                    copy.clearStatus(status);
                } else if (thing.hasStatus(status)) {
                    copy.setStatus(status);
                }
            }
            for (var word : List.copyOf(copy.getConditions())) {
                if (!thing.getConditions().contains(word)) {
                    copy.clearCondition(word);
                }
            }
            thing.getConditions().forEach(copy::setCondition);
            if (thing.hasDied()) {
                copy.markDied();
            }
            if (thing.isDestroyed()) {
                copy.markDestroyed();
            }
        }
    }

    private static void finish(RpgWorld from, RpgWorld to) {
        var world = new SaveOut();
        from.saveWorld(world);
        var in = new SaveIn(world.text(), to, "WORLD");
        to.loadWorld(in);
        in.expectEnd();
    }
}
