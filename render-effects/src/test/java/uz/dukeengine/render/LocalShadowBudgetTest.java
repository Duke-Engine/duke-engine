package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Which local lights have a shadow, and that none of them gains or loses it in one frame. */
class LocalShadowBudgetTest {

    private static final float FRAME = 1f / 60f;

    private static float weightOf(LocalShadowBudget budget, Object light) {
        for (var slot : budget.slots()) {
            if (slot.light == light) {
                return slot.weight;
            }
        }
        return 0f;
    }

    private static void run(LocalShadowBudget budget, Map<Object, Float> worth, int frames) {
        for (int i = 0; i < frames; i++) {
            budget.update(worth, FRAME);
        }
    }

    @Test
    void worthFallsWithDistanceAndIsNothingForWhatGivesNoLight() {
        assertTrue(LocalShadowBudget.worth(10f, 5f, 1f) > LocalShadowBudget.worth(10f, 5f, 20f));
        assertTrue(LocalShadowBudget.worth(10f, 5f, 3f) > LocalShadowBudget.worth(1f, 5f, 3f));
        assertTrue(LocalShadowBudget.worth(10f, 20f, 30f) > LocalShadowBudget.worth(10f, 2f, 30f));
        assertEquals(0f, LocalShadowBudget.worth(0f, 5f, 1f));
        assertEquals(0f, LocalShadowBudget.worth(10f, 0f, 1f));
    }

    @Test
    void theMostWorthyHaveTheSlotsAndNoMoreThanThereAreSlots() {
        var budget = new LocalShadowBudget(2, LocalShadowBudget.FADE);
        var worth = new HashMap<Object, Float>();
        var lights = new Object[5];
        for (int i = 0; i < lights.length; i++) {
            lights[i] = new Object();
            worth.put(lights[i], (float) (i + 1));
        }
        run(budget, worth, 60);
        assertEquals(2, budget.shown().size());
        assertEquals(1f, weightOf(budget, lights[4]));
        assertEquals(1f, weightOf(budget, lights[3]));
        assertEquals(0f, weightOf(budget, lights[0]));
    }

    @Test
    void aLightFadesInAndOutByTheFrameNotAtOnce() {
        var budget = new LocalShadowBudget(1, LocalShadowBudget.FADE);
        var light = new Object();
        var worth = new HashMap<Object, Float>(Map.of(light, 1f));
        float last = 0f;
        float most = 0f;
        for (int i = 0; i < 30; i++) {
            budget.update(worth, FRAME);
            float now = weightOf(budget, light);
            most = Math.max(most, Math.abs(now - last));
            last = now;
        }
        assertEquals(1f, last);
        assertTrue(most <= FRAME / LocalShadowBudget.FADE + 1e-6f, "no jump larger than a frame's share of the fade: " + most);

        worth.clear();
        float before = last;
        budget.update(worth, FRAME);
        assertTrue(weightOf(budget, light) > 0f && weightOf(budget, light) >= before - FRAME / LocalShadowBudget.FADE - 1e-6f);
        run(budget, worth, 30);
        assertEquals(0f, weightOf(budget, light));
        assertTrue(budget.shown().isEmpty());
    }

    @Test
    void aBetterLightWaitsForTheOldOnesShadowToFadeAndNeverHasTheSlotsOverfull() {
        var budget = new LocalShadowBudget(1, LocalShadowBudget.FADE);
        var old = new Object();
        var better = new Object();
        var worth = new HashMap<Object, Float>(Map.of(old, 1f));
        run(budget, worth, 30);
        worth.put(better, 5f);
        float lastOld = 1f;
        float lastBetter = 0f;
        for (int i = 0; i < 60; i++) {
            budget.update(worth, FRAME);
            float now = weightOf(budget, old);
            float then = weightOf(budget, better);
            assertTrue(budget.shown().size() <= 1, "one slot, one shadow");
            assertTrue(lastOld - now <= FRAME / LocalShadowBudget.FADE + 1e-6f);
            assertTrue(then - lastBetter <= FRAME / LocalShadowBudget.FADE + 1e-6f);
            lastOld = now;
            lastBetter = then;
        }
        assertEquals(0f, weightOf(budget, old));
        assertEquals(1f, weightOf(budget, better));
    }

    @Test
    void lightsOfAlmostEqualWorthDoNotTradeTheSlot() {
        var budget = new LocalShadowBudget(1, LocalShadowBudget.FADE);
        var a = new Object();
        var b = new Object();
        var worth = new HashMap<Object, Float>(Map.of(a, 1f));
        run(budget, worth, 30);
        // The camera sways: b is now just over a, then a just over b, every frame.
        for (int i = 0; i < 300; i++) {
            worth.put(a, i % 2 == 0 ? 1f : 1.1f);
            worth.put(b, i % 2 == 0 ? 1.1f : 1f);
            budget.update(worth, FRAME);
            assertEquals(1f, weightOf(budget, a));
            assertEquals(0f, weightOf(budget, b));
        }
        // But one clearly worth more does take it.
        worth.put(b, 3f);
        run(budget, worth, 120);
        assertEquals(1f, weightOf(budget, b));
        assertFalse(budget.shown().contains(a));
    }

    @Test
    void aLightThatLeavesTheSceneStillFadesOutInsteadOfVanishing() {
        var budget = new LocalShadowBudget(2, LocalShadowBudget.FADE);
        var a = new Object();
        var b = new Object();
        var worth = new HashMap<Object, Float>(Map.of(a, 2f, b, 1f));
        run(budget, worth, 30);
        worth.remove(a);
        budget.update(worth, FRAME);
        assertTrue(weightOf(budget, a) > 0.9f, "one frame later it is still nearly all there");
        assertEquals(1f, weightOf(budget, b));
    }
}
