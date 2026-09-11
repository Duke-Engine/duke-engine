package uz.duke.client3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything a game will ask for while it is played, worked out before it is.
 *
 * <p>The problem this exists for is a stall you can feel: the first monster of a
 * kind reaches the floor, and the client goes off and reads nine megabytes of
 * model, seven of animation library and a megabyte and a half of texture — on the
 * render thread, in one frame, while the player is looking at it. Twenty frames a
 * second for a second, then fine again until the next kind arrives.
 *
 * <p>None of that work can be avoided, but all of it can be done earlier, because
 * none of it depends on anything that happens in the game. A model is named in
 * {@link Visuals} before the window opens. So this walks what the game declared
 * and returns the list of files, in the order a unit is built from them, each
 * exactly once — a game that draws six monsters out of one library reads it once.
 *
 * <p>Deliberately pure and jME-free: what to load is a question about the
 * configuration, and answering it here means it can be checked without a window.
 * Doing the loading is {@code DukeRtsApp}'s, because only it has the asset
 * manager and the one thread allowed to talk to the graphics card.
 */
final class Preload {

    /** What a file is, which is what says how it is read and what it costs. */
    enum Kind {
        /** A creature or a prop. The big ones. */
        MODEL,
        /** A file clips are borrowed from — read once, shared by everything. */
        ANIMATIONS,
        /** A colour map. Cheap to read and expensive to hand the graphics card. */
        TEXTURE,
        /** One piece of a modular kit: a floor, a wall, a corner post. */
        TILE,
        /** A shot or a death. Small, but the first one still stops the frame. */
        SOUND
    }

    /** One file to read before the game starts. */
    record Job(Kind kind, String assetPath) {
    }

    private Preload() {
    }

    /**
     * Every file this game can ask for, each once, in the order a unit is built.
     *
     * <p>Themes are walked as well as the game's own looks. A theme swaps a
     * creature's model at a depth the player has not reached yet, and the whole
     * point is to have read it by then — the alternative is the same stall, moved
     * to the moment the floor changes, which is a worse moment for it.
     */
    static List<Job> plan(Visuals visuals) {
        var jobs = new ArrayList<Job>();
        Set<String> seen = new LinkedHashSet<>();
        var looks = visuals.allLooks();
        add(jobs, seen, Kind.MODEL, looks.stream().map(look -> look.modelPath).toList());
        // What a unit carries is a model like any other, and read at the same
        // moment as the unit: a bow fetched when the hero first appears is a stall
        // at the one moment the player is watching him.
        add(jobs, seen, Kind.MODEL, looks.stream().map(look -> look.heldPath).toList());
        for (var look : looks) {
            add(jobs, seen, Kind.ANIMATIONS,
                    look.animations.stream().map(Visuals.AnimationSource::assetPath).toList());
        }
        add(jobs, seen, Kind.TEXTURE, looks.stream().map(look -> look.texturePath).toList());
        for (var kit : visuals.allKits()) {
            // Arrays.asList, not List.of: a kit with no corner post is ordinary,
            // and List.of will not hold the null that says so.
            add(jobs, seen, Kind.TILE,
                    java.util.Arrays.asList(kit.getFloor(), kit.getWall(), kit.getCorner()));
        }
        add(jobs, seen, Kind.SOUND, looks.stream().map(look -> look.fireSound).toList());
        add(jobs, seen, Kind.SOUND, looks.stream().map(look -> look.dieSound).toList());
        // The hero panel's painted edges. Tiny files, and read at the worst
        // possible moment without this: the panel is built as the first floor
        // appears, which is the frame the player has been waiting through a
        // loading screen for.
        add(jobs, seen, Kind.TEXTURE, visuals.getPanelSkin().pieces().values().stream()
                .map(PanelSkin.Piece::texture).toList());
        // And the mouse pointers. Read now because the first one is wanted on the
        // frame the world appears, which is the frame that can least afford it.
        add(jobs, seen, Kind.TEXTURE, visuals.pointerImages());
        return List.copyOf(jobs);
    }

    private static void add(List<Job> jobs, Set<String> seen, Kind kind, List<String> paths) {
        for (var path : paths) {
            // A kit may have no corner post and most units have no sound, so the
            // nulls are ordinary rather than a sign of anything.
            if (path != null && seen.add(path)) {
                jobs.add(new Job(kind, path));
            }
        }
    }
}
