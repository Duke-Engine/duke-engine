package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import uz.duke.client3d.Visuals;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * What burns, what it burns like, and that none of it can reach the fight.
 *
 * <p>The last of those is the one worth a test rather than a comment. Everything
 * here is drawing: a colour, a count of sparks, how far a light reaches. If any
 * of it ever touched the simulation it would do so silently — the game would
 * still play, just not the same game twice — and it would be found by somebody
 * noticing that two runs of one seed had diverged.
 */
class ProjectileEffectTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Every projectile that names an effect names one the file describes. */
    @Test
    void everyEffectNamedIsDescribed() {
        var described = new HashSet<String>();
        for (var effect : SETTINGS.effects()) {
            described.add(effect.name());
        }
        assertFalse(described.isEmpty(), "the file describes no effects at all");

        for (var projectile : SETTINGS.projectiles()) {
            if (projectile.effect() == null) {
                continue; // a plain shot, which is most of them
            }
            assertTrue(described.contains(projectile.effect()),
                    projectile.name() + " burns like " + projectile.effect()
                            + ", which nothing describes");
        }
    }

    /**
     * And every effect uses kinds the client knows how to draw.
     *
     * <p>A name the client does not recognise is ignored rather than refused,
     * which is the right behaviour at run time and the wrong one to find out
     * about: the arrow flies, nothing burns, and the file looks correct.
     */
    @Test
    void everyKindNamedIsOneTheClientCanDraw() {
        var known = Visuals.EffectVisual.allKinds();
        // An effect with layers is drawn by its layers alone, so it may name no kind.
        var layered = new HashSet<String>();
        for (var layer : SETTINGS.effectLayers()) {
            layered.add(layer.effect());
        }
        for (var effect : SETTINGS.effects()) {
            assertFalse(effect.kinds().isEmpty() && !layered.contains(effect.name()),
                    effect.name() + " is an effect that does nothing");
            for (var kind : effect.kinds()) {
                assertTrue(known.contains(kind),
                        effect.name() + " asks for " + kind + ", and the client draws " + known);
            }
        }
    }

    /** A thing drawn as a glowing orb needs a size to be drawn at. */
    @Test
    void anythingDrawnAsAnOrbIsGivenOne() {
        for (var effect : SETTINGS.effects()) {
            if (!effect.kinds().contains(Visuals.EffectVisual.GLOW_ORB)) {
                continue;
            }
            assertTrue(effect.orbSize() > 0f,
                    effect.name() + " is a glowing orb of no size, which is nothing at all");
        }
    }

    /**
     * The projectile with no model of its own has an effect that draws one.
     *
     * <p>Otherwise it falls back to the client's plain shape, which is a capsule
     * with a gun barrel on it — right for a nameless unit in a test game and
     * wrong for a ball of fire.
     */
    @Test
    void aProjectileWithNoModelIsDrawnBySomething() {
        for (var projectile : SETTINGS.projectiles()) {
            if (projectile.hasModel()) {
                continue;
            }
            assertNotNull(projectile.effect(),
                    projectile.name() + " has neither a model nor an effect to stand in for one");
            var effect = SETTINGS.effects().stream()
                    .filter(e -> e.name().equals(projectile.effect())).findFirst().orElseThrow();
            assertTrue(effect.kinds().contains(Visuals.EffectVisual.GLOW_ORB),
                    projectile.name() + " has no model and its effect draws no body either");
        }
    }

    /**
     * None of it reaches the fight.
     *
     * <p>The same seed with every effect in the file and with none of them plays
     * the same floors, frame for frame. Not a claim about this code so much as
     * about where it lives: a number the simulation can read is a number that can
     * change what happens, and there is no way to tell by looking.
     */
    @Test
    void theEffectsCannotReachTheSimulation() {
        var with = DungeonSettings.load();
        var without = DungeonSettings.parse(withoutTheBurning(Content.data()));

        assertFalse(with.effects().isEmpty(), "the shipped file should describe effects");
        assertTrue(without.effects().isEmpty(), "and the stripped one should describe none");
        assertEquals(signature(with), signature(without),
                "a floor played differently once the fire was taken out of it");
    }

    /** The same files with every top-level Effect block cut out of them. */
    private static String withoutTheBurning(String file) {
        var kept = new StringBuilder();
        boolean inside = false;
        for (var line : file.split("\n", -1)) {
            if (line.equals("Effect")) {
                inside = true;
            }
            if (!inside) {
                kept.append(line).append('\n');
            }
            if (inside && line.equals("End")) {
                inside = false;
            }
        }
        return kept.toString();
    }

    /** Two floors of one seed, as the simulation itself counts them. */
    private static String signature(DungeonSettings settings) {
        var session = Dungeon.newSession(31L, settings);
        var game = session.game();
        game.runHeadless(1);
        var line = new StringBuilder();
        for (int frame = 0; frame < 90; frame++) {
            game.runHeadless(1);
            line.append(game.getLogic().checksum()).append('|');
        }
        return line.toString();
    }
}
