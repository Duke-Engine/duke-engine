package uz.duke.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.GeneratedDungeon;
import uz.duke.dungeon.level.GrowableBody;
import uz.duke.dungeon.loot.LootDrop;
import uz.duke.dungeon.loot.LootTable;
import uz.duke.game.DukeGame;
import uz.duke.game.GamePlayer;
import uz.duke.rts.module.ExperienceModule;

/**
 * Puts a generated floor into the world, and makes its inhabitants as dangerous
 * as their depth says they should be.
 *
 * <p>Shared by the first floor and every one after it, because they are the same
 * act: the only difference between the dungeon a run opens on and the one that
 * follows a dead boss is the number. Two copies of this would drift, and the one
 * that drifted would be the deeper floors nobody tests as often.
 *
 * <p>Depth is applied to the individual, not to the template. The same Runner
 * appears on every floor — a template says what a thing is, and this says where
 * it was found. All three effects use seams the engine already offers rather than
 * new engine features: a growable body for health, a damage modifier module for
 * damage, and a replaced experience module for what killing it is worth.
 */
public final class Spawner {

    private Spawner() {
    }

    /** Everything a floor puts in the world, and the boss it hangs its exit on. */
    public record Placed(GameObject hero, GameObject boss, List<GameObject> monsters) {
    }

    /**
     * Lay out a floor: the hero where the generator put him, its monsters scaled
     * to {@code depth}, and the boss in the furthest room.
     */
    public static Placed place(DukeGame game, GamePlayer heroPlayer, GamePlayer dungeonPlayer,
            GeneratedDungeon dungeon, DungeonSettings settings, int depth) {
        return place(game, heroPlayer, dungeonPlayer, dungeon, settings, depth, null);
    }

    /**
     * The same, with a table saying what the inhabitants leave behind.
     *
     * <p>Hung on each monster as it is placed rather than written into its
     * creature block, beside the depth bonus and for the same reason: a template
     * says what a thing is, and what it leaves depends on where it was met.
     */
    public static Placed place(DukeGame game, GamePlayer heroPlayer, GamePlayer dungeonPlayer,
            GeneratedDungeon dungeon, DungeonSettings settings, int depth, LootTable drops) {
        return place(game, heroPlayer, dungeonPlayer, dungeon, settings, depth, drops,
                settings.playedHero());
    }

    /**
     * The same, told which hero to put in it.
     *
     * <p>Told rather than looked up, because once a player may choose there is no
     * single answer in the file to look up: {@code DefaultHero} is who plays when
     * nobody was asked, and a menu is somebody being asked. The overloads above
     * keep the file's answer, which is what a headless run and a test want.
     */
    public static Placed place(DukeGame game, GamePlayer heroPlayer, GamePlayer dungeonPlayer,
            GeneratedDungeon dungeon, DungeonSettings settings, int depth, LootTable drops,
            String heroTemplate) {
        var logic = game.getLogic();
        // Whoever was chosen, or whoever the file names when nobody was asked --
        // see DefaultHero. The word used to be here, so a second hero could be
        // described in full and still never walk into a dungeon.
        var hero = logic.spawn(logic.getThingFactory().findTemplate(heroTemplate),
                at(logic, dungeon.hero()), heroPlayer.getIndex());

        var monsters = new ArrayList<GameObject>();
        for (var monster : dungeon.monsters()) {
            var spawned = spawn(game, dungeonPlayer, monster.kind(), at(logic, monster.at()));
            if (spawned != null) {
                scale(spawned, settings.monsterHealthAt(depth), settings.monsterDamageAt(depth),
                        settings.experienceAt(depth));
                dropsFrom(spawned, drops, settings, depth, false);
                monsters.add(spawned);
            }
        }

        // Whatever stands about in the rooms. Spawned like anything else and then
        // left alone: they have a shape, so the engine bakes them into the
        // navigation grid and bodies stop at them, and no body, so neither brain
        // will ever pick one as something to hit.
        for (var prop : dungeon.props()) {
            spawn(game, dungeonPlayer, prop.kind(), at(logic, prop.at()));
        }

        var boss = spawn(game, dungeonPlayer, dungeon.boss().kind(), at(logic, dungeon.boss().at()));
        if (boss != null) {
            scale(boss, settings.bossHealthAt(depth), settings.bossDamageAt(depth),
                    settings.experienceAt(depth));
            dropsFrom(boss, drops, settings, depth, true);
        }
        return new Placed(hero, boss, List.copyOf(monsters));
    }

    private static GameObject spawn(DukeGame game, GamePlayer owner, String kind, Coord3D where) {
        var template = game.getLogic().getThingFactory().findTemplate(kind);
        return template == null ? null
                : game.getLogic().spawn(template, where, owner.getIndex());
    }

    /**
     * Make one monster worth its depth.
     *
     * <p>Each multiplier is computed from the depth in one step rather than
     * compounded floor by floor, so the tenth floor is the same whether it was
     * reached by playing or asked for directly.
     */
    private static void scale(GameObject monster, float health, float damage, float experience) {
        if (monster.getBody() instanceof GrowableBody body && health > 1f) {
            body.growMaxHealth(body.getMaxHealth() * (health - 1f));
        }
        if (damage != 1f) {
            monster.addModule(new DepthBonus(monster, damage));
        }
        // What killing it is worth is fixed by its template, and the template is
        // the same on every floor — so the module is swapped for one that says a
        // deeper number, in the place the old one held.
        //
        // The replacement carries no ranks, which is not a loss: monsters here are
        // written with `ExperienceRequired = 0 0 0`, meaning they count experience
        // and never promote. Giving one a ladder would need that ladder read back
        // off the old module, which the engine does not expose — worth knowing
        // before writing a monster that levels up.
        var worth = monster.findModule(ExperienceModule.class);
        if (worth != null && experience != 1f) {
            var scaled = new ExperienceModule.Data(
                    Math.round(worth.getExperienceValue() * experience), List.of(), false);
            monster.replaceModule(worth, new ExperienceModule(monster, scaled));
        }
    }

    /** Give a monster something to leave behind, if the game asked for loot at all. */
    private static void dropsFrom(GameObject monster, LootTable drops, DungeonSettings settings,
            int depth, boolean boss) {
        if (drops != null && !settings.lootTemplate().isBlank()) {
            monster.addModule(new LootDrop(monster, drops, settings.lootTemplate(), depth, boss));
        }
    }

    /**
     * Where the generator put something, standing on the floor that is under it.
     *
     * <p>A dungeon has storeys now, and a monster spawned at zero on the second
     * one is a monster sunk to the waist in its own floor until it takes a step.
     */
    private static Coord3D at(uz.duke.core.GameLogic logic, GeneratedDungeon.Placement placement) {
        var ground = new Coord3D(placement.x(), placement.y(), 0f);
        return new Coord3D(placement.x(), placement.y(), logic.groundHeight(ground));
    }
}
