package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.content.EffectList;
import uz.dukeengine.core.event.ObjectHurt;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.thing.ObjectId;

/** Critical and big numbers, a catch in the motion on a heavy blow, and shakes that obey the game's scale (E58). */
class HitFeelTest {

    private static final HitNumbers LOOK = new HitNumbers(0.4f, 2.1f, 0.85f, 34f, 38f, 14f, 1f, 0xFFE0A0, 0xC22A22,
            0x4FBF4F, 1f, true, 50f, 1.3f, 0xFFC040, 0xFF4010, 1.8f);

    private static ObjectHurt hurt(float amount, boolean critical) {
        return new ObjectHurt(1, new ObjectId(7), "Skeleton", 1, DamageType.NORMAL, amount, new ObjectId(3), null,
                null, critical);
    }

    @Test
    void aCriticalBlowIsDrawnInItsOwnColourAndSize() {
        var critical = hurt(20f, true);
        assertEquals(0xFF4010, LOOK.colourOf(false, false, critical.amount(), critical.critical()));
        assertEquals(1.8f, LOOK.sizeOf(false, false, critical.amount(), critical.critical()));
        assertEquals(0xFFC040, LOOK.colourOf(false, false, 60f, false), "big");
        assertEquals(1.3f, LOOK.sizeOf(false, false, 60f, false));
        assertEquals(0xFFE0A0, LOOK.colourOf(false, false, 20f, false), "plain");
        assertEquals(0xC22A22, LOOK.colourOf(false, true, 60f, true), "a blow he takes keeps its look");
        assertEquals(1f, LOOK.sizeOf(true, false, 60f, true), "a heal keeps its look");
    }

    @Test
    void aMajorBlowCatchesBothCreaturesForItsTimeAndThenLetsGo() {
        var stops = new HitStops();
        var look = new HitStop(0.05f, 0.2f, true, false);
        Map<Integer, Float> speeds = new HashMap<>();

        stops.heard(hurt(10f, false), look, LOOK.bigAt(), 0f);
        stops.apply(0.01f, speeds::put);
        assertEquals(Map.of(), speeds, "a small blow catches nothing");

        stops.heard(hurt(60f, false), look, LOOK.bigAt(), 0f);
        stops.apply(0.01f, speeds::put);
        assertEquals(Map.of(7, 0.2f, 3, 0.2f), speeds, "the struck and the striker slowed");

        stops.heard(hurt(60f, true), look, LOOK.bigAt(), 0.03f);
        stops.apply(0.051f, speeds::put);
        assertEquals(Map.of(7, 1f, 3, 1f), speeds, "a second blow did not lengthen it");

        speeds.clear();
        stops.apply(0.1f, speeds::put);
        assertEquals(Map.of(), speeds, "let go, and forgotten");
    }

    @Test
    void aShakeAtScaleZeroMovesNothing() {
        var show = new ListShow(null, new Node(), null, (_, _) -> { }, () -> Vector3f.ZERO, (_, _) -> 0f,
                (_, piece) -> new Node("piece"), (_, _) -> { }, null, null);
        show.shake(Vector3f.ZERO, EffectList.Shake.Strength.values()[EffectList.Shake.Strength.values().length - 1]);
        assertNotEquals(Vector3f.ZERO, show.shakeNow(), "at scale 1 it knocks");

        var still = new ListShow(null, new Node(), null, (_, _) -> { }, () -> Vector3f.ZERO, (_, _) -> 0f,
                (_, piece) -> new Node("piece"), (_, _) -> { }, null, null);
        still.shakeScale(0f);
        still.shake(Vector3f.ZERO, EffectList.Shake.Strength.values()[EffectList.Shake.Strength.values().length - 1]);
        assertSame(Vector3f.ZERO, still.shakeNow());
    }
}
