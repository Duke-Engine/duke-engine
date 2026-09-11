package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a game will ask for, worked out before it asks.
 *
 * <p>The thing being pinned down is a list, and the reason it is worth pinning is
 * that getting it wrong is invisible. A file left out of the plan is not an error:
 * it is read later, in the frame it is first drawn, which is exactly the stall the
 * plan exists to prevent — and the game looks fine apart from a hitch nobody can
 * point at. So this checks the two ways of being wrong: missing something, and
 * reading the same twenty megabytes twice.
 */
class PreloadTest {

    private static List<String> pathsOf(Visuals visuals, Preload.Kind kind) {
        return Preload.plan(visuals).stream()
                .filter(job -> job.kind() == kind)
                .map(Preload.Job::assetPath)
                .toList();
    }

    /** A game with no art asks for nothing, and does not fall over saying so. */
    @Test
    void aGameWithNoArtAsksForNothing() {
        var visuals = Visuals.create().unit("Blob", u -> u.scale(2f));

        assertEquals(List.of(), Preload.plan(visuals));
    }

    /**
     * What a creature carries is read with the creature.
     *
     * <p>A bow fetched when the hero first appears is a stall at the one moment
     * the player is looking straight at him — and it would be a stall nobody could
     * place, because the hero himself was read minutes ago.
     */
    @Test
    void whatACreatureCarriesIsReadWithIt() {
        var visuals = Visuals.create().unit("Hero", u -> u
                .model("Models/ranger.glb")
                .holds("Models/bow.gltf", "handslot.l", 1f));

        assertEquals(List.of("Models/ranger.glb", "Models/bow.gltf"),
                pathsOf(visuals, Preload.Kind.MODEL));
    }

    /** Everything one creature is made of, in the order it is made. */
    @Test
    void oneCreatureBringsItsModelClipsSkinAndSounds() {
        var visuals = Visuals.create().unit("Skeleton", u -> u
                .model("Models/skeleton.glb")
                .texture("Models/skeleton.png")
                .animationsFrom("Models/library.glb")
                .fireSound("Sounds/bow.ogg")
                .dieSound("Sounds/bones.ogg"));

        assertEquals(List.of(
                new Preload.Job(Preload.Kind.MODEL, "Models/skeleton.glb"),
                new Preload.Job(Preload.Kind.ANIMATIONS, "Models/library.glb"),
                new Preload.Job(Preload.Kind.TEXTURE, "Models/skeleton.png"),
                new Preload.Job(Preload.Kind.SOUND, "Sounds/bow.ogg"),
                new Preload.Job(Preload.Kind.SOUND, "Sounds/bones.ogg")),
                Preload.plan(visuals));
    }

    /**
     * A file six creatures share is read once.
     *
     * <p>Which is the whole economy of a creature kit: one animation library moves
     * everything in it, and it is the largest file in the game. Reading it per
     * creature would make the loading screen six times as long as it needs to be.
     */
    @Test
    void aSharedFileIsReadOnce() {
        var visuals = Visuals.create()
                .unit("Skeleton", u -> u.model("Models/skeleton.glb")
                        .animationsFrom("Models/library.glb"))
                .unit("Runner", u -> u.model("Models/skeleton.glb")
                        .animationsFrom("Models/library.glb"))
                .unit("Brute", u -> u.model("Models/brute.glb")
                        .animationsFrom("Models/library.glb"));

        assertEquals(List.of("Models/skeleton.glb", "Models/brute.glb"),
                pathsOf(visuals, Preload.Kind.MODEL));
        assertEquals(List.of("Models/library.glb"), pathsOf(visuals, Preload.Kind.ANIMATIONS));
    }

    /**
     * A theme's art is read with everything else, and not when its floor arrives.
     *
     * <p>The temptation is to read a theme's models when the player reaches the
     * depth that wears it, and it is the wrong instinct: the moment the floor
     * changes is a moment with a screenful of new tiles in it already. A file read
     * then stalls the one frame that can least afford another twenty megabytes.
     */
    @Test
    void aThemesArtIsReadWithEverythingElse() {
        var visuals = Visuals.create()
                .unit("Skeleton", u -> u.model("Models/skeleton.glb"))
                .theme("SciFi,Bare", look -> look
                        .tiles(Tileset.create().floor("plate.obj").wall("bulkhead.obj"))
                        .unit("Skeleton", u -> u.model("Models/robot.glb")));

        assertTrue(pathsOf(visuals, Preload.Kind.MODEL).contains("Models/robot.glb"),
                "the robot is read before the floor that wears it");
        assertEquals(List.of("plate.obj", "bulkhead.obj"), pathsOf(visuals, Preload.Kind.TILE));
    }

    /** A kit with no corner post plans no corner post, rather than planning a null. */
    @Test
    void aKitMayLeaveAPieceOut() {
        var visuals = Visuals.create()
                .tiles(Tileset.create().floor("floor.obj").wall("wall.obj"));

        assertEquals(List.of("floor.obj", "wall.obj"), pathsOf(visuals, Preload.Kind.TILE));
    }

    /**
     * Two themes on one kit read it once.
     *
     * <p>Tones of a theme differ by tint far more often than by model, so the same
     * three pieces are named by every tone of it.
     */
    @Test
    void twoTonesOfOneKitReadItOnce() {
        var kit = Tileset.create().floor("floor.obj").wall("wall.obj");
        var visuals = Visuals.create()
                .theme("Ruins,Fallen", look -> look.tiles(kit))
                .theme("Ruins,Green", look -> look.tiles(kit));

        assertEquals(List.of("floor.obj", "wall.obj"), pathsOf(visuals, Preload.Kind.TILE));
    }
}
