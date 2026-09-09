package uz.duke.dungeon;

import uz.duke.client3d.Duke3D;
import uz.duke.client3d.Hotkeys;
import uz.duke.client3d.Shell;
import uz.duke.client3d.Tileset;
import uz.duke.client3d.Visuals;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.content.HeroLook;
import uz.duke.dungeon.skill.CastSkill;

/**
 * Opens the dungeon in the engine's 3D client.
 *
 * <p>No visuals are bound. Every creature falls back to a coloured primitive,
 * lit and cast into a world the camera looks across rather than straight down —
 * which is the whole difference between a diagram of a game and a game.
 *
 * <p>The front menu is this game's, not the client's. A dungeon has a run to
 * begin and a way out, and nothing else: it is one player against the dungeon,
 * so it does not offer to host a LAN game — which the client used to, on the
 * grounds that the monsters count as a second player.
 *
 * <p>Controls are the engine's own: left-click the hero to select him,
 * right-click the floor to walk or a skeleton to attack it.
 *
 * <p>Each launch draws a different dungeon. The seed is the one thing here the
 * clock touches — and it is outside the simulation, choosing <em>which</em>
 * deterministic dungeon to play rather than reaching into how one is built. From
 * that seed on, generation and the run loop are a pure, reproducible chain.
 */
public final class Main {

    private Main() {
    }

    /** One movement, from the file that holds it, under the name the game uses. */
    private static void animation(Visuals.UnitVisual unit, String assetPath, String clipName) {
        if (assetPath != null) {
            unit.animationFrom(assetPath, clipName);
        }
    }

    public static void main(String[] args) {
        var settings = DungeonSettings.load();
        Duke3D.launch(Dungeon.create(System.nanoTime(), settings), looks(settings), Shell.create()
                .entry(Shell.Entry.PLAY, "Enter the dungeon")
                .entry(Shell.Entry.SETTINGS)
                .entry(Shell.Entry.QUIT), controls(settings));
    }

    /**
     * The keys that cast skills, taken from the same file that says what the
     * skills are.
     *
     * <p>Read out of the settings rather than written here, so adding a fifth
     * skill — or a second hero with different keys — binds its key by being in the
     * file. A press does one thing: post the command. Deciding whether the skill
     * is ready, unlocked, or aimed at anything is the simulation's, on its own
     * thread, on a frame boundary.
     */
    private static Hotkeys controls(DungeonSettings settings) {
        var keys = Hotkeys.create();
        for (var skill : settings.skills()) {
            char key = skill.key();
            keys.on(key, game -> game.postCommand(new CastSkill(game.getLocalPlayerIndex(), key)));
        }
        return keys;
    }

    /**
     * What each kind of monster looks like, taken from the same file that says how
     * it behaves.
     *
     * <p>With no models yet, colour and size are the only things telling one
     * monster from another — and telling a runner from a brute is a decision the
     * player has to make in the second before they reach him. Player colour cannot
     * do it: every monster belongs to the same side, so they would all be one
     * shade of red, on screen and on the minimap alike.
     */
    private static Visuals looks(DungeonSettings settings) {
        var visuals = Visuals.create();
        for (var kind : settings.monsters()) {
            var look = settings.lookOf(kind);
            visuals.unit(kind.name(), unit -> {
                // The colour is set either way: it is what the minimap dot is
                // drawn in, and what the creature falls back to if its model is
                // missing. A shape in the right colour beats nothing on screen.
                unit.colour(kind.awtColour()).scale(kind.scale());
                if (!look.hasModel()) {
                    return;
                }
                unit.model(look.model())
                        .texture(look.texture())
                        .tint(look.awtTint())
                        .scale(look.modelScale())
                        .facing(look.facing())
                        .idle(look.idle())
                        .walk(look.walk())
                        .attack(look.attack());
                unit.die(settings.deathClip());
                if (settings.animationLibrary() != null) {
                    unit.animationsFrom(settings.animationLibrary());
                }
            });
        }

        // The hero comes from a different kit on a different skeleton, and his
        // animations arrive one movement per file — so he is described his own
        // way rather than squeezed into the monsters'.
        var hero = settings.hero();
        if (hero.hasModel()) {
            visuals.unit("Hero", unit -> {
                unit.model(hero.model())
                        .scale(hero.modelScale())
                        .facing(hero.facing())
                        .idle(HeroLook.IDLE)
                        .walk(HeroLook.WALK)
                        .attack(HeroLook.ATTACK)
                        .die(HeroLook.DEATH);
                if (hero.texture() != null) {
                    unit.texture(hero.texture());
                }
                animation(unit, hero.idleFrom(), HeroLook.IDLE);
                animation(unit, hero.walkFrom(), HeroLook.WALK);
                animation(unit, hero.attackFrom(), HeroLook.ATTACK);
                animation(unit, hero.deathFrom(), HeroLook.DEATH);
            });
        }
        // Arrows are units like any other — they are in the world, so the client
        // draws them without being told anything special, and the simulation
        // turns them so they point the way they are flying.
        var arrow = settings.arrowLook();
        visuals.unit(settings.arrowTemplate(), unit -> {
            unit.colour(arrow.awtTint()); // the minimap dot, and the fallback shape
            if (arrow.hasModel()) {
                unit.modelPart(arrow.model(), arrow.part())
                        .scale(arrow.scale())
                        .facing(arrow.facing())
                        // Not decoration: the tint is what gets it a material this
                        // client can light. Without one it keeps the loader's PBR
                        // and the arrow is a black splinter.
                        .tint(arrow.awtTint());
            } else {
                unit.scale(0.28f);
            }
        });

        // The floor is black until he walks it. Named rather than given a
        // distance: the radius is the hero's own VisionRange from creatures.ini,
        // which is also what the engine's fog uses to decide whether a monster is
        // on screen — so the ground he uncovers and the things he can see are the
        // same number, and re-tuning one cannot leave the other behind.
        visuals.discoveredBy("Hero");

        // The floor is a modular kit, laid out by the client from the same grid
        // the pathfinder uses. Named in dungeon.ini rather than here, so swapping
        // the kit — or dropping back to plain blocks — is an edit, not a rebuild.
        var art = settings.tiles();
        if (art.floor() != null) {
            visuals.tiles(Tileset.create()
                    .floor(art.floor())
                    .wall(art.wall())
                    .corner(art.corner())
                    .tileSize(art.tileSize()));
        }
        return visuals;
    }
}
