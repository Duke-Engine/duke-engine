package uz.dukeengine.rpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.fight.ArrowUpdate;

/** The knight's four: a wave that passes through, a charge that carries, blood that hardens, a pull to one point. */
class KnightSkillsTest {

    private record Numbers(float damage, float radius, float range, float distance, float hitWidth, int boost,
            int boostPerLevel, int stunFrames, float heal, String projectile, float speed) {
    }

    private static Skill skill(SkillEffect effect, Numbers n) {
        var d = Skill.DEFAULTS;
        return new Skill("Knight", 'Q', effect, n.damage(), 0f, n.radius(), n.range(), n.distance(), n.hitWidth(),
                n.boost(), n.boostPerLevel(), d.durationFrames(), d.tickFrames(), d.slowFrames(), d.cooldownFrames(),
                d.cooldownPerLevel(), d.maxRank(), d.levelPerRank(), 0, 0, 0, n.projectile(), d.icon(), d.look(),
                d.castAnim(), d.castSeconds(), d.name(), d.blurb(), n.speed(), n.heal(), d.healBelowPercent(),
                d.summons(), d.maxSummoned(), d.summonExperiencePercent(), n.stunFrames(), d.manaRegen());
    }

    private static RpgWorld world(Skill knights, Seal seal) {
        return new RpgWorld(Map.of("Knight", List.of(knights)), seal)
                .template("Knight", new ActiveBody.Data(100f), new SkillBook.Data())
                .template("Skeleton", new ActiveBody.Data(100f), new StatusUpdate.Data())
                .template("Wave", new ArrowUpdate.Data());
    }

    private static boolean stunned(GameObject thing) {
        return thing.hasStatus(ObjectStatus.DISABLED);
    }

    @Test
    void aWavePassesThroughHurtingEachEnemyOnItsLineOnce() {
        var world = world(skill(SkillEffect.WAVE, new Numbers(30f, 0f, 0f, 150f, 12f, 0, 0, 0, 0f, "Wave", 200f)),
                Seal.NONE);
        var knight = world.put("Knight", world.heroes, 0f, 0f);
        var near = world.put("Skeleton", world.monsters, 40f, 0f);
        var far = world.put("Skeleton", world.monsters, 90f, 0f);
        var aside = world.put("Skeleton", world.monsters, 40f, 60f);
        world.run(1);

        assertTrue(knight.findModule(SkillBook.class).cast('Q', 1, null, new Coord3D(200f, 0f, 0f)));
        world.run(60);

        assertEquals(70f, near.getBody().getHealth(), 1e-3f, "the first it crossed, once");
        assertEquals(70f, far.getBody().getHealth(), 1e-3f, "and the one behind it: it passed through");
        assertEquals(100f, aside.getBody().getHealth(), "off its line, untouched");
    }

    @Test
    void aCriticalWaveAndChargeTellTheirHurtsCritical() {
        for (var effect : List.of(SkillEffect.WAVE, SkillEffect.SKEWER)) {
            var world = world(skill(effect, new Numbers(30f, 10f, 0f, 150f, 12f, 0, 0, 0, 0f, "Wave", 200f)),
                    Seal.NONE);
            var knight = world.put("Knight", world.heroes, 0f, 0f);
            world.put("Skeleton", world.monsters, 40f, 0f);
            var critical = new uz.dukeengine.rpg.hero.CriticalStrike(knight);
            knight.addModule(critical);
            critical.figures(100, 50);
            world.run(1);
            world.drainEvents();

            assertTrue(knight.findModule(SkillBook.class).cast('Q', 1, null, new Coord3D(150f, 0f, 0f)));
            var hurts = new java.util.ArrayList<uz.dukeengine.core.event.ObjectHurt>();
            for (int frame = 0; frame < 60; frame++) {
                world.run(1);
                world.drainEvents().forEach(event -> {
                    if (event instanceof uz.dukeengine.core.event.ObjectHurt hurt) {
                        hurts.add(hurt);
                    }
                });
            }
            assertFalse(hurts.isEmpty(), effect + " hurt something");
            assertTrue(hurts.stream().allMatch(uz.dukeengine.core.event.ObjectHurt::critical), effect + ": " + hurts);
        }
    }

    @Test
    void aShieldChargeCarriesWhatItMeetsAndDropsItStunnedAtHisFront() {
        var world = world(skill(SkillEffect.SKEWER, new Numbers(20f, 10f, 0f, 120f, 0f, 0, 0, 30, 0f, "", 0f)),
                Seal.NONE);
        var knight = world.put("Knight", world.heroes, 0f, 0f);
        var first = world.put("Skeleton", world.monsters, 30f, 0f);
        var second = world.put("Skeleton", world.monsters, 60f, 0f);
        world.run(1);

        assertTrue(knight.findModule(SkillBook.class).cast('Q', 1, null, new Coord3D(120f, 0f, 0f)));

        float stoppedAt = knight.getPosition().x();
        assertTrue(stoppedAt > 100f, "he drove forward: " + stoppedAt);
        for (var carried : List.of(first, second)) {
            assertTrue(carried.getPosition().x() > stoppedAt, "dropped at his front: " + carried.getPosition());
            assertEquals(80f, carried.getBody().getHealth(), 1e-3f, "hurt once");
            assertTrue(stunned(carried));
        }
        assertNotEquals(first.getPosition(), second.getPosition(), "spread, not one in the other");
    }

    @Test
    void aChargeAtASpeedCarriesWhatItCatchesFrameByFrameAndDropsThemWhenItEnds() {
        var world = world(skill(SkillEffect.SKEWER, new Numbers(20f, 10f, 0f, 120f, 0f, 0, 0, 30, 0f, "", 60f)),
                Seal.NONE);
        var knight = world.put("Knight", world.heroes, 0f, 0f);
        var first = world.put("Skeleton", world.monsters, 30f, 0f);
        var second = world.put("Skeleton", world.monsters, 90f, 0f);
        world.run(1);
        assertTrue(knight.findModule(SkillBook.class).cast('Q', 1, null, new Coord3D(120f, 0f, 0f)));

        world.run(30); // a second: sixty units
        assertEquals(60f, knight.getPosition().x(), 1f, "half way, at its speed");
        assertTrue(first.getPosition().x() > knight.getPosition().x(), "the first held at his front as he goes");
        assertEquals(80f, first.getBody().getHealth(), 1e-3f, "hurt once, as it was caught");
        assertEquals(100f, second.getBody().getHealth(), "the second not reached yet");

        world.run(40);
        assertEquals(120f, knight.getPosition().x(), 1f, "the whole way");
        for (var carried : List.of(first, second)) {
            assertTrue(carried.getPosition().x() > knight.getPosition().x(), "dropped at his front");
            assertEquals(80f, carried.getBody().getHealth(), 1e-3f, "each hurt once");
            assertTrue(stunned(carried));
        }
    }

    @Test
    void aChargeWhoseBearerFallsEndsAndDropsWhatItCarried() {
        var world = world(skill(SkillEffect.SKEWER, new Numbers(20f, 10f, 0f, 120f, 0f, 0, 0, 30, 0f, "", 60f)),
                Seal.NONE);
        var knight = world.put("Knight", world.heroes, 0f, 0f);
        var caught = world.put("Skeleton", world.monsters, 30f, 0f);
        world.run(1);
        assertTrue(knight.findModule(SkillBook.class).cast('Q', 1, null, new Coord3D(120f, 0f, 0f)));
        world.run(20);
        float fellAt = knight.getPosition().x();

        knight.getBody().damage(1000f, DamageType.NORMAL);
        world.run(10);

        assertEquals(fellAt, knight.getPosition().x(), 1e-3f, "no further once fallen");
        assertTrue(stunned(caught), "dropped, stunned, where it ended");
        assertTrue(Math.abs(caught.getPosition().x() - fellAt) < 30f, "at his front: " + caught.getPosition());
    }

    @Test
    void dragonBloodTurnsAsideBlowsAndMendsByItsRank() {
        var world = world(skill(SkillEffect.DRAGON_BLOOD, new Numbers(0f, 0f, 0f, 0f, 0f, 20, 10, 0, 3f, "", 0f)),
                Seal.NONE);
        var knight = world.put("Knight", world.heroes, 0f, 0f);
        var book = knight.findModule(SkillBook.class);
        book.setRank('Q', 2);
        world.run(1);
        assertFalse(book.cast('Q', 2), "never cast");

        knight.getBody().damage(50f, DamageType.NORMAL);
        assertEquals(65f, knight.getBody().getHealth(), 1e-3f, "thirty percent turned aside at the second rank");

        world.run(30);
        assertEquals(71f, knight.getBody().getHealth(), 1e-3f, "six a second: three each rank");
    }

    @Test
    void aPullBringsEveryEnemyInReachToOnePointInFrontOfHimHurtAndStunnedButNotAcrossASeal() {
        Seal wall = (w, a, b) -> (a.y() < 30f) != (b.y() < 30f);
        var world = world(skill(SkillEffect.PULL, new Numbers(10f, 60f, 40f, 0f, 0f, 0, 0, 20, 0f, "", 0f)),
                wall);
        var knight = world.put("Knight", world.heroes, 0f, 0f);
        var left = world.put("Skeleton", world.monsters, -40f, 0f);
        var below = world.put("Skeleton", world.monsters, 0f, -45f);
        var beyond = world.put("Skeleton", world.monsters, 0f, 45f);
        var outside = world.put("Skeleton", world.monsters, 200f, 0f);
        world.run(1);

        assertTrue(knight.findModule(SkillBook.class).cast('Q', 1));

        var point = new Coord3D(40f, 0f, 0f);
        for (var pulled : List.of(left, below)) {
            assertTrue(pulled.getPosition().distance(point) < 25f, "pulled to the point: " + pulled.getPosition());
            assertEquals(90f, pulled.getBody().getHealth(), 1e-3f);
            assertTrue(stunned(pulled));
        }
        assertNotEquals(left.getPosition(), below.getPosition(), "spread, not one in the other");
        assertEquals(new Coord3D(0f, 45f, 0f), beyond.getPosition(), "across the seal: left where it was");
        assertEquals(100f, beyond.getBody().getHealth());
        assertEquals(new Coord3D(200f, 0f, 0f), outside.getPosition(), "out of reach");
    }

    @Test
    void aChargeCarryingThreeRunsItsWholeDistanceAndLaysThemAheadOfHim() {
        var world = world(skill(SkillEffect.SKEWER, new Numbers(5f, 10f, 0f, 150f, 0f, 0, 0, 30, 0f, "", 60f)),
                Seal.NONE);
        var knight = world.put("Knight", world.heroes, 0f, 0f);
        var carried = List.of(world.put("Skeleton", world.monsters, 30f, 0f),
                world.put("Skeleton", world.monsters, 45f, 2f), world.put("Skeleton", world.monsters, 60f, -2f));
        world.run(1);
        assertTrue(knight.findModule(SkillBook.class).cast('Q', 1, null, new Coord3D(150f, 0f, 0f)));

        world.run(100);
        assertEquals(150f, knight.getPosition().x(), 1f, "the whole way, carrying three");
        for (var each : carried) {
            assertTrue(each.getPosition().x() > knight.getPosition().x(), "ahead of him: " + each.getPosition());
            assertTrue(stunned(each));
        }
    }

    @Test
    void theWaveTheChargeAndThePullAreMarked() {
        for (var effect : List.of(SkillEffect.WAVE, SkillEffect.SKEWER, SkillEffect.PULL)) {
            var d = Skill.DEFAULTS;
            var plain = skill(effect, new Numbers(5f, 30f, 20f, 60f, 10f, 0, 0, 0, 0f, "Wave", 0f));
            var looked = new Skill(plain.heroTemplate(), plain.key(), plain.effect(), plain.damage(),
                    plain.damagePerLevel(), plain.radius(), plain.range(), plain.distance(), plain.hitWidth(),
                    plain.boostPercent(), plain.boostPerLevel(), plain.durationFrames(), plain.tickFrames(),
                    plain.slowFrames(), plain.cooldownFrames(), plain.cooldownPerLevel(), plain.maxRank(),
                    plain.levelPerRank(), plain.windUpFrames(), plain.manaCost(), plain.manaCostPerLevel(),
                    plain.projectile(), d.icon(), "knight_look", d.castAnim(), d.castSeconds(), d.name(), d.blurb(),
                    effect == SkillEffect.WAVE ? 100f : 0f, plain.heal(), plain.healBelowPercent(), d.summons(),
                    d.maxSummoned(), d.summonExperiencePercent(), plain.stunFrames(), d.manaRegen());
            var world = world(looked, Seal.NONE);
            var knight = world.put("Knight", world.heroes, 0f, 0f);
            world.run(1);
            var book = knight.findModule(SkillBook.class);
            assertTrue(book.cast('Q', 1, null, new Coord3D(60f, 0f, 0f)), effect.name());
            assertFalse(book.getCastMarks().isEmpty(), effect + " is marked");
        }
    }

    @Test
    void aPassiveTheGameSaysIsAtRankNoughtIsNotHeld() {
        var world = world(skill(SkillEffect.DRAGON_BLOOD, new Numbers(0f, 0f, 0f, 0f, 0f, 20, 10, 0, 3f, "", 0f)),
                Seal.NONE);
        var knight = world.put("Knight", world.heroes, 0f, 0f);
        knight.findModule(SkillBook.class).setRank('Q', 0);
        world.run(1);

        knight.getBody().damage(50f, DamageType.NORMAL);
        assertEquals(50f, knight.getBody().getHealth(), 1e-3f, "unlearnt: nothing turned aside");
    }
}
