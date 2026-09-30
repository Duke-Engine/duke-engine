package uz.dukeengine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.thing.ThingFactory;

/**
 * What core gives a runtime to run a world of any kind on: its own work each frame, where the world's simulate is, and
 * the commands the world's own set does not know — and the kind of game, found on the classpath.
 */
class FlavourSeamsTest {

    /** The runtime's word for a command: nothing a world of this kind knows. */
    private record Cheer(int playerIndex, String who) implements Command {
    }

    /** A world whose set is its test commands, handing on anything else, and saying when it simulates. */
    private static final class Stage extends GameLogic {
        final List<String> said;

        Stage(List<String> said) {
            super(new ThingFactory(ModuleFactory.withDefaults()));
            this.said = said;
        }

        @Override
        protected void onCommand(Command command) {
            switch (command) {
                case TestCommand own -> said.add("own:" + own.getClass().getSimpleName());
                default -> onOtherCommand(command);
            }
        }

        @Override
        protected void simulate() {
            said.add("simulate@" + getFrame());
        }
    }

    @Test
    void theRuntimesWorkRunsEachFrameJustBeforeTheWorldSimulates() {
        var said = new ArrayList<String>();
        var world = new Stage(said);
        world.init();
        world.eachFrame(() -> said.add("runtime@" + world.getFrame()));
        world.update();
        world.update();
        assertEquals(List.of("runtime@0", "simulate@0", "runtime@1", "simulate@1"), said);
    }

    @Test
    void aCommandOfNoSetTheWorldKnowsGoesToTheRuntimesHandlerAsItIsApplied() {
        var said = new ArrayList<String>();
        var world = new Stage(said);
        world.init();
        world.onOtherCommands(command -> said.add("other:" + ((Cheer) command).who() + "@" + world.getFrame()));
        world.issueCommand(new TestCommand.Halt(1, List.of()));
        world.issueCommand(new Cheer(1, "crowd"));
        world.update();
        assertEquals(List.of("own:Halt", "other:crowd@0", "simulate@0"), said, "in the order given, before simulate");
    }

    @Test
    void withNoHandlerSuchACommandIsDroppedNotApplied() {
        var said = new ArrayList<String>();
        var world = new Stage(said);
        world.init();
        world.issueCommand(new Cheer(1, "nobody"));
        world.update();
        assertEquals(List.of("simulate@0"), said);
    }

    @Test
    void withNoKindOfGameOnTheClasspathFindingOneSaysSo() {
        var none = assertThrows(IllegalStateException.class, Flavour::found);
        assertTrue(none.getMessage().contains("no kind of game"), none.getMessage());
    }
}
