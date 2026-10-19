package uz.dukeengine.client3d.rpg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.item.Stat;
import uz.dukeengine.rpg.story.Merchant;

/** The trade window on the game's canvas: the stock and the bag listed, a click on each an order, Esc closing. */
class TradeScreenTest {

    private static final Loot SWORD = new Loot("sword", "Sword", "icons/items/sword.png", Stat.ATTACK, 8, 1, 1);
    private static final Loot SHIELD = new Loot("shield", "Shield", "icons/items/shield.png", Stat.ATTACK, 2, 1, 1);

    private final List<String> deals = new ArrayList<>();
    private final LootBag bag = new LootBag();
    private TradeScreen.Open open = new TradeScreen.Open(0, 7, "Smith",
            List.of(new Merchant.Ware("sword", 50, 2)), bag);

    private final TradeScreen screen = new TradeScreen(MenuStyle.DEFAULTS,
            new TradeScreen.Words("Gold", "For sale", "Your bag", "Esc — close", "icons/items/gold.png"),
            () -> open, () -> 120,
            new TradeScreen.Goods(id -> id.equals("sword") ? SWORD : null, item -> item == SHIELD ? 20 : -1,
                    item -> 0xFFFFFF, item -> false),
            new TradeScreen.Deals() {
                @Override
                public void buy(int player, int merchant, int ware) {
                    deals.add("buy " + player + " " + merchant + " " + ware);
                }

                @Override
                public void sell(int player, int merchant, int slot) {
                    deals.add("sell " + player + " " + merchant + " " + slot);
                }

                @Override
                public void close() {
                    deals.add("close");
                }
            });

    private static CanvasInput.Button click(Drawn.Picture on) {
        return new CanvasInput.Button(on.middleX(), on.middleY(), CanvasInput.Mouse.LEFT, true, false);
    }

    @Test
    void theStockAndTheBagAreListedWithThePurse() {
        bag.take(SHIELD, 0, 0);
        var canvas = new Drawn();

        screen.paint(canvas);
        screen.purse(canvas);

        assertTrue(canvas.text.containsAll(List.of("Smith", "For sale", "Your bag")), canvas.text.toString());
        assertTrue(canvas.text.contains("Sword — 50 (2)"), canvas.text.toString());
        assertTrue(canvas.text.contains("Shield — 20"), canvas.text.toString());
        assertTrue(canvas.text.contains("Gold: 120"), canvas.text.toString());
        assertNotNull(canvas.picture("icons/items/gold.png"), "the coins before the purse");
    }

    @Test
    void aClickOnAWareBuysItAndOneOnABagSlotSellsIt() {
        bag.take(SHIELD, 0, 0);
        var canvas = new Drawn();
        screen.paint(canvas);

        assertTrue(screen.take(click(canvas.picture(SWORD.icon()))));
        assertTrue(screen.take(click(canvas.picture(SHIELD.icon()))));

        assertEquals(List.of("buy 0 7 0", "sell 0 7 0"), deals);
    }

    @Test
    void escClosesAndAClickOnTheWindowIsNotAnOrderToTheWorld() {
        var canvas = new Drawn();
        screen.paint(canvas);
        var row = screen.rows().getFirst();

        assertTrue(screen.take(new CanvasInput.Key(CanvasInput.ESCAPE, true, false)));
        assertFalse(screen.take(new CanvasInput.Key(CanvasInput.ESCAPE + 1, true, false)));
        assertTrue(screen.take(new CanvasInput.Button(Math.round(row.x()), Math.round(row.y()) - 10,
                CanvasInput.Mouse.RIGHT, true, false)));
        assertEquals(List.of("close"), deals);
    }

    @Test
    void withNoMerchantNothingIsDrawnAndNothingTaken() {
        open = null;
        var canvas = new Drawn();

        screen.paint(canvas);

        assertTrue(canvas.text.isEmpty());
        assertFalse(screen.take(new CanvasInput.Key(CanvasInput.ESCAPE, true, false)));
        assertTrue(deals.isEmpty());
    }
}
