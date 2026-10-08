package uz.dukeengine.client3d.rpg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.core.view.CommandButton;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.item.Stat;

/** The bag on the game's canvas: drawn while a match is played, its things taken in hand to put down or to use. */
class BagScreenTest {

    private static final BagScreen.Words WORDS = new BagScreen.Words("NARSALAR", "Yerga qo'y", "O'ng tugma: ol",
            "Chap tugma: ishlat", "PickUp", "ToTheGate", "Use");
    private static final Loot SWORD = new Loot("sword", "Qilich", "icons/items/sword.png", Stat.ATTACK, 8, 1, 1);
    private static final Loot KEY = new Loot("key", "Kalit", "icons/items/key.png", "KEY", 0, 0, 1, "", "", 0, 0,
            1, ItemUse.UNLOCK, "");

    private record Armed(CommandButton button) {
    }

    private final List<Armed> armed = new ArrayList<>();
    private final List<String> under = new ArrayList<>();
    private final LootBag bag = new LootBag();
    private final BagScreen screen = new BagScreen(MenuStyle.DEFAULTS, WORDS,
            item -> List.of("+" + item.value() + "% Zarba"),
            new BuffScreen(List.of(), List.of(), MenuStyle.DEFAULTS, PanelLook.DEFAULTS, 24f,
                    new BuffScreen.Words("Zarba", "Mana/s", "Qon", "Tezlik", "s")),
            (button, ended) -> armed.add(new Armed(button)), () -> 0L);

    private DukeGame shown() {
        var game = DukeGame.create("bag-test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        game.addPlayer("One", Color.BLUE);
        screen.show(game, () -> bag, order -> order, canvas -> under.add("under"));
        return game;
    }

    @Test
    void beforeAFrameNothingIsDrawnAndThenTheBagWithWhatIsUnderIt() {
        var game = shown();
        bag.take(SWORD, 0, 0);
        var canvas = new Drawn();

        screen.paint(canvas);
        assertTrue(canvas.pictures.isEmpty() && under.isEmpty(), "no frame yet: a menu, not a match");

        game.runHeadless(1);
        screen.paint(canvas);
        assertTrue(canvas.text.contains("NARSALAR"), canvas.text.toString());
        assertNotNull(canvas.picture(SWORD.icon()));
        assertEquals(List.of("under"), under);
    }

    @Test
    void theRightButtonTakesAThingToPutDownAndTheLeftOneAThingThatDoesSomethingToUse() {
        var game = shown();
        bag.take(SWORD, 0, 0);
        bag.take(KEY, 0, 0);
        game.runHeadless(1);
        var canvas = new Drawn();
        screen.paint(canvas);
        var sword = canvas.picture(SWORD.icon());
        var key = canvas.picture(KEY.icon());

        assertTrue(screen.take(
                new CanvasInput.Button(sword.middleX(), sword.middleY(), CanvasInput.Mouse.LEFT, true, false)),
                "the bag's own click");
        assertTrue(armed.isEmpty(), "the left button takes only a thing that does something");
        screen.take(new CanvasInput.Button(sword.middleX(), sword.middleY(), CanvasInput.Mouse.RIGHT, true, false));
        assertEquals("drop:0", armed.getLast().button().id());
        assertEquals(CommandButton.Aim.GROUND, armed.getLast().button().aim());
        assertEquals(0, BagScreen.slotOf(armed.getLast().button().id()));

        screen.take(new CanvasInput.Button(key.middleX(), key.middleY(), CanvasInput.Mouse.LEFT, true, false));
        assertEquals("use:1", armed.getLast().button().id());
        assertEquals(CommandButton.Aim.UNIT, armed.getLast().button().aim());
        assertEquals("Use", armed.getLast().button().answeredAs(), "answered as the game's own order");
    }
}
