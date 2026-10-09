package uz.dukeengine.rpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.fight.ArrowUpdate;
import uz.dukeengine.rpg.fight.Arrows;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;

/** The rpg's blows name who dealt them, and a soul drink — its own or a worn thing's — counts his kills. */
class BlowsAndSoulsTest {

    private static Skill skill(String owner, SkillEffect effect, float damage, float radius, int boost, int tick) {
        var d = Skill.DEFAULTS;
        return new Skill(owner, 'Q', effect, damage, d.damagePerLevel(), radius, d.range(), d.distance(),
                d.hitWidth(), boost, d.boostPerLevel(), d.durationFrames(), tick, d.slowFrames(),
                d.cooldownFrames(), d.cooldownPerLevel(), d.maxRank(), d.levelPerRank(), d.windUpFrames(),
                d.manaCost(), d.manaCostPerLevel(), d.projectile(), d.icon(), d.look(), d.castAnim(),
                d.castSeconds(), d.name(), d.blurb(), d.projectileSpeed(), d.heal(), d.healBelowPercent(),
                d.summons(), d.maxSummoned(), d.summonExperiencePercent(), d.stunFrames(), d.manaRegen());
    }

    /** Five souls a step of five percent, up to twelve. */
    private static final Skill DRINK = skill("Medallion", SkillEffect.SOUL_DRINK, 12f, 0f, 5, 2);

    private static RpgWorld world(Map<String, List<Skill>> skills) {
        return new RpgWorld(skills, Seal.NONE)
                .template("Hero", new ActiveBody.Data(100f), new SkillBook.Data())
                .template("Skeleton", new ActiveBody.Data(5f), new SkillBook.Data())
                .template("Arrow", new ArrowUpdate.Data());
    }

    private static List<ObjectDied> deaths(RpgWorld world) {
        var died = new ArrayList<ObjectDied>();
        world.onDied(died::add);
        return died;
    }

    @Test
    void anArrowsKillNamesTheArcherAndHisSide() {
        var world = world(Map.of());
        var died = deaths(world);
        var archer = world.put("Hero", world.heroes, 0f, 0f);
        var skeleton = world.put("Skeleton", world.monsters, 40f, 0f);

        assertTrue(Arrows.loose(archer, skeleton, 50f, DamageType.NORMAL, "Arrow", 200f, 5f));
        world.run(60);

        assertEquals(1, died.size(), "the skeleton fell to the arrow");
        assertEquals(archer.getId(), died.getFirst().killer());
        assertEquals(world.heroes, died.getFirst().killerPlayerIndex());
    }

    @Test
    void aSkillsKillNamesTheCasterAndHisSide() {
        var world = world(Map.of("Hero", List.of(skill("Hero", SkillEffect.AREA_DAMAGE, 50f, 60f, 0, 0))));
        var died = deaths(world);
        var hero = world.put("Hero", world.heroes, 0f, 0f);
        world.put("Skeleton", world.monsters, 20f, 0f);

        assertTrue(hero.findModule(SkillBook.class).cast('Q', 1));
        world.run(30);

        assertEquals(1, died.size(), "the skeleton fell to the blast");
        assertEquals(hero.getId(), died.getFirst().killer());
        assertEquals(world.heroes, died.getFirst().killerPlayerIndex());
    }

    private static ObjectDied killedBy(GameObject killer, int victimSide) {
        return new ObjectDied(0, new uz.dukeengine.core.thing.ObjectId(999), "Skeleton", victimSide, null, null,
                killer.getId(), 0f, killer.getPlayerIndex());
    }

    @Test
    void aSoulDrinkCountsItsBearersKillsOfEnemiesAndAddsByStepsUpToItsCap() {
        var world = world(Map.of("Hero", List.of(DRINK)));
        var hero = world.put("Hero", world.heroes, 0f, 0f);
        var book = hero.findModule(SkillBook.class);

        for (int kill = 0; kill < 5; kill++) {
            SkillBook.reaped(world, killedBy(hero, world.monsters));
        }
        SkillBook.reaped(world, killedBy(hero, world.heroes));
        assertEquals(5, book.getSouls(), "an enemy's soul each, and none of his own side");
        assertEquals(10, book.soulPercent(), "two steps of five");
        assertEquals(1.10f, book.damageMultiplier(), 1e-6f);

        book.setSouls(40);
        assertEquals(12, book.soulPercent(), "no more than its cap");
        assertFalse(book.cast('Q', 1), "never cast");
    }

    @Test
    void aWornThingLendsItsSoulDrinkWhileWornAndTheSoulsStayWithTheBook() {
        var world = world(Map.of());
        var hero = world.put("Hero", world.heroes, 0f, 0f);
        var book = hero.findModule(SkillBook.class);
        var bag = new LootBag(List.of("Tumor"), 4, LootBag.JOIN, LootBag.TOP_LEVEL);
        book.lendFrom(bag);
        var medallion = new Loot("medallion", "Medalyon", "", "", 0, 1, 1, "", "", 0, 0, 1, ItemUse.NONE, "",
                "Tumor", List.of(DRINK));
        assertNull(book.soulDrink(), "nothing worn, nothing lent");

        bag.take(medallion, 0, 0);
        assertNotNull(book.soulDrink(), "worn: lent");
        for (int kill = 0; kill < 4; kill++) {
            SkillBook.reaped(world, killedBy(hero, world.monsters));
        }
        assertEquals(10, book.soulPercent());

        bag.shift(bag.wornPlace("Tumor"));
        assertNull(book.soulDrink(), "taken off: gone");
        assertEquals(1f, book.damageMultiplier(), 1e-6f);
        SkillBook.reaped(world, killedBy(hero, world.monsters));
        assertEquals(4, book.getSouls(), "kept with the book, and no more drunk without it");
    }
}
