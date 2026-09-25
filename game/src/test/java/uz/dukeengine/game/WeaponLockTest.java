package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.event.WeaponFired;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.message.OrderSource;
import uz.dukeengine.rts.module.Weapon;
import uz.dukeengine.rts.module.WeaponSet;
import uz.dukeengine.rts.module.WeaponSlot;
import uz.dukeengine.rts.module.WeaponUpdate;

/**
 * A weapon locked to one slot by an order, and slots only a lock picks — the reference's weapon lock
 * ({@code Object::setWeaponLock}) and a set's {@code AutoChooseSources}: a sniper's crew-killing shot, which no source
 * picks by itself, fired only when a button locks it.
 */
class WeaponLockTest {

    private static Weapon weapon(String name, float damage, int reload) {
        return new Weapon(name, damage, 40f, reload, reload, DamageType.NORMAL, 0f, true, List.of(), 0, 0, true,
                DeathType.NORMAL);
    }

    private record Field(DukeGame game, int me, GameObject sniper, GameObject target, List<String> fired) {

        void order(GameMessage message) {
            game.postCommand(message);
        }

        void attack(int slot) {
            order(new GameMessage.AttackObject(me, List.of(sniper.getId()), target.getId(), false,
                    OrderSource.PLAYER, slot));
        }

        /** The weapons it fired over {@code frames} frames, the order given before them taking hold on the second. */
        List<String> run(int frames) {
            fired.clear();
            for (int frame = 0; frame < frames; frame++) {
                game.runHeadless(1);
                for (var event : game.getSnapshot().events()) {
                    if (event instanceof WeaponFired shot && shot.shooter().equals(sniper.getId())) {
                        fired.add(shot.weapon());
                    }
                }
            }
            return fired.subList(Math.min(2, fired.size()), fired.size()).stream().distinct().toList();
        }
    }

    /** A sniper whose rifle any source picks and whose second slot, dealing more, none does. */
    private static Field field(List<OrderSource> secondSources) {
        var sets = List.of(new WeaponSet(List.of(), List.of(new WeaponSlot("Rifle"),
                new WeaponSlot("CrewShot", List.of(), true, secondSources))));
        var game = DukeGame.create("locks").loadUnits(DukeGame.STARTER_UNITS).map(40, 40)
                .addWeapons(List.of(weapon("Rifle", 10f, 1), weapon("CrewShot", 50f, 1)))
                .addUnits(List.of(RtsTemplate.named("Sniper").visionRange(80f).module(new ActiveBody.Data(100f))
                        .module(WeaponUpdate.Data.sets(sets)).build(),
                        RtsTemplate.named("Tank").visionRange(10f).module(new ActiveBody.Data(100_000f)).build()));
        var usa = game.addPlayer("USA", Color.BLUE);
        var china = game.addPlayer("China", Color.RED);
        game.enemies(usa, china).localPlayer(usa);
        game.spawn("Sniper", usa, 100f, 100f);
        game.spawn("Tank", china, 120f, 100f);
        game.runHeadless(1);
        var sniper = find(game, "Sniper");
        return new Field(game, usa.getIndex(), sniper, find(game, "Tank"), new ArrayList<>());
    }

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }

    @Test
    void aSlotNoSourcePicksIsPassedOverAndFiredWhileLockedToIt() {
        var field = field(List.of(OrderSource.NONE));
        field.attack(-1);
        assertEquals(List.of("Rifle"), field.run(5), "told to attack, the rifle: the crew shot is no source's");

        field.attack(1);
        assertEquals(List.of("CrewShot"), field.run(5), "locked to the second slot, it fires the second");
        assertEquals(1, field.sniper().findModule(WeaponUpdate.class).getLockedSlot());
    }

    @Test
    void aLockUntilTheAttackEndsLeavesTheNextAttackToWeighItsSlotsFreely() {
        var field = field(List.of());
        field.attack(0);
        assertEquals(List.of("Rifle"), field.run(5), "locked to the rifle, though the other deals more");

        field.attack(-1);
        assertEquals(List.of("CrewShot"), field.run(5), "the next attack weighs its slots freely again");
        assertEquals(-1, field.sniper().findModule(WeaponUpdate.class).getLockedSlot());
    }

    @Test
    void aPermanentLockOutlastsAnAttackAndAMoveLetsATemporaryOneGo() {
        var field = field(List.of());
        var weapon = field.sniper().findModule(WeaponUpdate.class);
        assertTrue(weapon.lock(0, WeaponUpdate.Lock.PERMANENTLY));
        field.attack(-1);
        assertEquals(List.of("Rifle"), field.run(5), "switched to the rifle for good");
        assertTrue(weapon.lock(1, WeaponUpdate.Lock.TEMPORARILY), "a temporary lock leaves a permanent one");
        assertEquals(0, weapon.getLockedSlot());

        weapon.unlock(WeaponUpdate.Lock.PERMANENTLY);
        field.attack(0);
        field.run(2);
        field.order(new GameMessage.MoveTo(field.me(), List.of(field.sniper().getId()),
                field.sniper().getPosition()));
        field.run(3);
        assertEquals(-1, weapon.getLockedSlot(), "a move is the end of the attack its lock was for");
    }

    @Test
    void theGamesOwnOrderPicksOnlyTheSlotsTheGameMay() {
        var field = field(List.of(OrderSource.GAME));
        field.attack(-1);
        assertEquals(List.of("Rifle"), field.run(5), "the player's order may not pick the second slot");

        field.order(new GameMessage.AttackObject(field.me(), List.of(field.sniper().getId()),
                field.target().getId(), false, OrderSource.GAME, -1));
        assertEquals(List.of("CrewShot"), field.run(5), "the game's may, and it deals more");
    }
}
