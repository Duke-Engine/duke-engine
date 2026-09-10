package uz.duke.dungeon;

import uz.duke.client3d.Duke3D;
import uz.duke.client3d.EdgeScroll;
import uz.duke.client3d.Fog;
import uz.duke.client3d.Hotkeys;
import uz.duke.client3d.Shell;
import uz.duke.client3d.Tileset;
import uz.duke.client3d.Visuals;
import uz.duke.core.thing.ObjectId;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.content.HeroLook;
import uz.duke.dungeon.power.ChoosePower;
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

    /**
     * An arrow's look. Two creatures use it — his ordinary shot and the one Q
     * looses — differing only in the numbers, which is why it is one method.
     */
    private static void arrow(Visuals visuals, String template, DungeonSettings.ArrowLook look) {
        visuals.unit(template, unit -> {
            unit.colour(look.awtTint()); // the minimap dot, and the fallback shape
            if (!look.hasModel()) {
                unit.scale(0.28f);
                return;
            }
            unit.modelPart(look.model(), look.part())
                    .scale(look.scale())
                    .facing(look.facing())
                    // Drawing only: the simulation is flat, so height is not a
                    // position but the line the shot is drawn along.
                    .yOffset(look.height())
                    // Not decoration: the tint is what gets it a material this
                    // client can light. Without one it keeps the loader's PBR and
                    // the arrow is a black splinter.
                    .tint(look.awtTint());
        });
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
     * is ready, unlocked, or able to reach what it was aimed at is the
     * simulation's, on its own thread, on a frame boundary.
     *
     * <p>Which of the three ways a key is bound follows from what the skill does,
     * because the effect is what knows: a strike is pointed at a creature, a dash
     * at a spot, and the two that go off around the caster are pointed at nothing.
     * The client keeps the press-then-click; this only says what to send once the
     * player has chosen.
     */
    static Hotkeys controls(DungeonSettings settings) {
        var keys = Hotkeys.create();
        // The level-up cards. The client draws them and reports which was taken;
        // which power that is, and what it is worth, is settled in the simulation
        // when the command comes round — the same road a keypress travels.
        keys.onChoose((game, index) -> game.postCommand(new ChoosePower(
                game.getLocalPlayerIndex(), index, offeredId(game))));
        for (var skill : settings.skills()) {
            char key = skill.key();
            switch (skill.effect().aim()) {
                case UNIT -> keys.onUnit(key, (game, id) -> game.postCommand(new CastSkill(
                        game.getLocalPlayerIndex(), key, new ObjectId(id), null)));
                case GROUND -> keys.onGround(key, (game, spot) -> game.postCommand(new CastSkill(
                        game.getLocalPlayerIndex(), key, null, spot)));
                case SELF -> keys.on(key, game -> game.postCommand(
                        new CastSkill(game.getLocalPlayerIndex(), key)));
            }
        }
        return keys;
    }

    /**
     * Which offer the player is answering, read back out of the line the game
     * itself wrote.
     *
     * <p>The client knows the number — it is drawing the screen — but handing it
     * back through the callback would have made a general seam carry one game's
     * field. Reading it here keeps the client's side of the bargain to "the
     * player took the second card", which is all it can honestly claim to know.
     */
    private static int offeredId(uz.duke.game.DukeGame game) {
        var status = game.getSnapshot().status();
        int at = status.indexOf("|offer=");
        if (at < 0) {
            return -1;
        }
        var field = status.substring(at + "|offer=".length());
        int comma = field.indexOf(',');
        try {
            return Integer.parseInt(comma < 0 ? field : field.substring(0, comma));
        } catch (NumberFormatException broken) {
            return -1;
        }
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
        // What a dead monster leaves lying about. No chest in the kit, so it is
        // a box in torch colour -- which is what a thing worth walking over to
        // has to be, whatever it is eventually modelled as.
        visuals.unit("Chest", unit -> unit.colour(new java.awt.Color(0xE8A33D)).scale(0.5f));

        // Arrows are units like any other — they are in the world, so the client
        // draws them without being told anything special, and the simulation
        // turns them so they point the way they are flying.
        arrow(visuals, settings.arrowTemplate(), settings.arrowLook());
        // The one Q looses: the same shaft, drawn bigger and in torch colour, so
        // the shot the player chose to spend is not mistaken for the ones he gets
        // for free.
        arrow(visuals, settings.heavyArrowTemplate(), settings.heavyArrowLook());

        // The floor is black until he walks it. Named rather than given a
        // distance: the radius is the hero's own VisionRange from creatures.ini,
        // which is also what the engine's fog uses to decide whether a monster is
        // on screen — so the ground he uncovers and the things he can see are the
        // same number, and re-tuning one cannot leave the other behind.
        visuals.discoveredBy("Hero");

        // What the dark is worth: whether stone stops sight, how dim a room he
        // has left should be, and what colour nothing is. All of it drawing, and
        // all of it in the file — see DungeonFog in dungeon.ini.
        // Shoving the camera with the cursor, on top of the keys — see
        // DungeonCamera in dungeon.ini.
        visuals.edgeScroll(new EdgeScroll(settings.edgeScrollMargin(),
                settings.edgeScrollSpeedPercent()));

        visuals.fog(new Fog(settings.fogLineOfSight(),
                settings.fogUnseenPercent() / 100f, settings.fogRememberedPercent() / 100f,
                settings.fogVisiblePercent() / 100f, settings.fogSoftenCells(),
                settings.fogOpenPerSecond(), settings.fogTextureSize(), settings.fogTint()));

        // The floor is a modular kit, laid out by the client from the same grid
        // the pathfinder uses. Named in dungeon.ini rather than here, so swapping
        // the kit — or dropping back to plain blocks — is an edit, not a rebuild.
        var art = settings.tiles();
        if (art.floor() != null) {
            visuals.tiles(Tileset.create()
                    .floor(art.floor())
                    .wall(art.wall())
                    .corner(art.corner())
                    .tileSize(art.tileSize())
                    .wallHeight(art.wallHeight()));
        }
        return visuals;
    }
}
