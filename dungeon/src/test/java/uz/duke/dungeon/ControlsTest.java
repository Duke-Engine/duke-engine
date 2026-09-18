package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.client3d.Hotkeys;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * The keys the game claims, and what each of them asks the player to point at.
 *
 * <p>Why this is worth a test: there are two ways to cast a skill — press its
 * letter, or click its slot on the hero's bar — and they have to be the same
 * thing. They are, structurally: the click ends up at the client's own
 * {@code pressHotkey}, the same method a keypress reaches, so both walk into the
 * <em>one</em> binding registered here, both arm a skill that needs aiming, and
 * both end in {@code postCommand}. Nothing applies an effect on the render
 * thread.
 *
 * <p>What a test can hold still is the other half of that: that there is exactly
 * one binding per skill, that it came out of the data file, and that what it asks
 * to be pointed at follows from what the skill does. If a second, parallel way of
 * casting were ever added, it would have to come through here too.
 */
class ControlsTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();

    /**
     * ★ The four orders, and what each of them asks to be pointed at.
     *
     * <p>A, S, D under a resting hand — attack, stop, defend — with walking on the
     * letter left over because it is the one order nobody reaches for the keyboard
     * to give.
     *
     * <p>The claim worth making is the first one: <b>attack takes either</b>. A
     * key that asked only for a creature could not be pointed into a room the
     * player has not walked into, which is exactly when he wants to say it — and
     * the failure would not look like a missing feature, it would look like a
     * click that did nothing.
     */
    @Test
    void theFourOrdersAskForWhatTheyNeed() {
        var keys = Main.controls(SHIPPED);

        assertEquals(Hotkeys.Aim.UNIT_OR_GROUND, keys.aimOf('A'),
                "attack must take a creature OR a piece of floor");
        assertEquals(Hotkeys.Aim.NOW, keys.aimOf('S'), "stop acts the moment it is pressed");
        assertEquals(Hotkeys.Aim.NOW, keys.aimOf('D'), "defend acts the moment it is pressed");
        assertEquals(Hotkeys.Aim.OPEN_GROUND, keys.aimOf('F'),
                "walking is pointed at floor he could stand on and has seen");
    }

    /** And none of the four collides with a skill. */
    @Test
    void theOrdersAndTheSkillsDoNotShareALetter() {
        var keys = Main.controls(SHIPPED);

        for (var skill : SHIPPED.skills()) {
            for (char order : new char[] {'A', 'S', 'D', 'F'}) {
                assertNotEquals(order, skill.key(), skill.heroTemplate() + "'s "
                        + skill.key() + " is on an order's letter, so one of the two is"
                        + " silently unreachable");
            }
        }
    }

    @Test
    void everySkillInTheFileGetsAKey() {
        var keys = Main.controls(SHIPPED).claimedKeys();

        for (var skill : SHIPPED.skills()) {
            assertTrue(keys.contains(skill.key()),
                    skill.key() + " is in the file but bound to nothing");
        }
        assertFalse(keys.isEmpty());
    }

    /**
     * What a key asks for follows from the effect, not from the key.
     *
     * <p>A strike is aimed at a creature whatever its numbers say, and there is no
     * useful sense in which one hero's dash is aimed and another's is not — so the
     * effect is what knows, and this checks that the binding took its word for it.
     */
    @Test
    void whatAKeyAsksForFollowsFromWhatTheSkillDoes() {
        var controls = Main.controls(SHIPPED);

        // The played hero's four. Both heroes cast on the same four keys, so the
        // file has two skills on Q and only one of them is bound.
        for (var skill : SHIPPED.skillsFor(SHIPPED.playedHero())) {
            var aim = controls.aimOf(skill.key());
            assertNotNull(aim, "no binding for " + skill.key());
            assertEquals(switch (skill.effect().aim()) {
                case UNIT -> Hotkeys.Aim.UNIT;
                case GROUND -> Hotkeys.Aim.GROUND;
                case OPEN_GROUND -> Hotkeys.Aim.OPEN_GROUND;
                case SELF -> Hotkeys.Aim.NOW;
            }, aim, skill.key() + " asks for the wrong thing");
        }
    }

    /**
     * ★ And it follows whoever is CHOSEN, not whoever the file starts with.
     *
     * <p>The bug this is here for, reported from a chair as "the mage's Q does
     * nothing — no damage and no effect". Everything the client knows about a
     * skill is keyed by its letter, and all of it used to be settled once at
     * startup out of {@code DefaultHero}. So picking any hero but that one got
     * you his skills wearing the default hero's aims: the rogue's Q wants a
     * creature, the mage's wants a direction, and a fireball handed a creature
     * has nowhere to fly. It does not misfire — {@code SkillBook} REFUSES it and
     * leaves the cooldown unspent, so the key is simply dead.
     *
     * <p>Asked of every hero in the file rather than of the mage, because the
     * mage is only the one somebody noticed: the knight's Q wants a patch of
     * floor and was equally dead, and the fourth hero would have been too.
     */
    @Test
    void whatAKeyAsksForFollowsWhoeverWasChosen() {
        for (int row = 0; row < SHIPPED.heroes().size(); row++) {
            var him = SHIPPED.heroes().get(row).name();
            var visuals = uz.duke.client3d.Visuals.create();
            var controls = Main.controls(SHIPPED);

            // Through the menu row, not through the helper behind it. Asking the
            // helper would pass whether or not anything ever calls it, which is
            // exactly the state this was found in.
            Main.whoToPlay(Dungeon.newSession(21L, SHIPPED), SHIPPED, visuals, controls)
                    .options().get(row).taken().run();

            for (var skill : SHIPPED.skillsFor(him)) {
                assertEquals(switch (skill.effect().aim()) {
                    case UNIT -> Hotkeys.Aim.UNIT;
                    case GROUND -> Hotkeys.Aim.GROUND;
                    case OPEN_GROUND -> Hotkeys.Aim.OPEN_GROUND;
                    case SELF -> Hotkeys.Aim.NOW;
                }, controls.aimOf(skill.key()),
                        him + "'s " + skill.key() + " asks for the wrong thing, so pressing"
                                + " it hands the skill a target it cannot use and it refuses");
                var ring = visuals.getSkillRange(skill.key());
                assertNotNull(ring, him + "'s " + skill.key() + " has no ring to draw");
                assertEquals(Main.rangeOf(skill, SHIPPED.ringSelfRadius()), ring,
                        him + "'s " + skill.key() + " is drawn as somebody else's skill");
            }
        }
    }

    /**
     * Every hero casts on the same four letters, and that is load-bearing.
     *
     * <p>The letters a game claims are settled once, at startup, before anybody
     * has chosen anything — the client needs them to know which of its own
     * controls to give up. Re-pointing a key at a different hero's skill changes
     * what it ASKS for and not whether it was claimed, so a hero wanting a fifth
     * letter would get a key the client had already taken for itself and a skill
     * that could never be cast.
     *
     * <p>So this is a failing test rather than a dead key on the day somebody
     * gives a hero a T. The fix then is to claim the union of every hero's keys
     * up front; there is no reason to write that until there is a hero who needs
     * it.
     */
    @Test
    void everyHeroCastsOnTheSameFourLetters() {
        var first = keysOf(SHIPPED.heroes().get(0).name());
        for (var hero : SHIPPED.heroes()) {
            assertEquals(first, keysOf(hero.name()), hero.name()
                    + " casts on different letters from " + SHIPPED.heroes().get(0).name()
                    + ", and only the ones claimed at startup can ever be pressed");
        }
    }

    private static java.util.List<Character> keysOf(String hero) {
        return SHIPPED.skillsFor(hero).stream().map(skill -> skill.key()).sorted().toList();
    }

    /**
     * A hero the file invented gets his keys the same way, with no Java at all.
     *
     * <p>Two lines of file: who is being played, and what he can do. The first was
     * not needed while there was one hero and is the whole point now — keys belong
     * to whoever walks into the dungeon, because two heroes cast on the same four
     * letters and only one of them can have Q.
     */
    @Test
    void aSecondHerosKeysComeFromTheFileToo() {
        var settings = DungeonSettings.parse("""
                World Dungeon
                  Run = Loop
                    DefaultHero = Rogue
                  End
                End
                DungeonSkill Rogue Z
                  Effect = DASH
                  Distance = 40
                  CooldownFrames = 60
                End
                """);

        assertTrue(Main.controls(settings).claimedKeys().contains('Z'),
                "a skill nobody wrote Java for should still have a key");
        assertEquals(Hotkeys.Aim.OPEN_GROUND, Main.controls(settings).aimOf('Z'));
    }

    /**
     * And the hero who is <em>not</em> being played gets none of them.
     *
     * <p>The fault this is really about. Both of the shipped heroes cast on Q, W,
     * E and R, and what a key asks the player to point at follows from the skill
     * behind it — so binding every skill in the file would leave Q asking for
     * whatever the last hero read wanted. The archer's Q wants a creature and the
     * knight's wants a patch of floor, and the wrong one of those is not a small
     * wrongness: the click is refused and the skill never goes off.
     */
    @Test
    void theHeroWhoIsNotPlayedGetsNoKeys() {
        var settings = DungeonSettings.parse("""
                World Dungeon
                  Run = Loop
                    DefaultHero = Rogue
                  End
                End
                DungeonSkill Rogue Z
                  Effect = DASH
                  Distance = 40
                  CooldownFrames = 60
                End
                DungeonSkill Bard Y
                  Effect = AREA_DAMAGE
                  Radius = 20
                  CooldownFrames = 60
                End
                """);

        var controls = Main.controls(settings);

        assertTrue(controls.claimedKeys().contains('Z'));
        assertFalse(controls.claimedKeys().contains('Y'),
                "a hero nobody is playing took a key off the one who is");
    }

    /** The shipped file's two heroes really do share their four letters. */
    @Test
    void bothShippedHeroesCastOnTheSameFourKeys() {
        var archer = SHIPPED.skillsFor("Rogue").stream().map(s -> s.key()).sorted().toList();
        var knight = SHIPPED.skillsFor("Knight").stream().map(s -> s.key()).sorted().toList();

        assertEquals(archer, knight,
                "if they stopped sharing keys, binding only the played hero's would be"
                        + " a precaution against nothing -- worth knowing either way");
        assertFalse(archer.isEmpty(), "the archer lost his skills");
    }

    /**
     * A lane is drawn as a reach and a direction, and nothing else.
     *
     * <p>The lane takes the shot's own width rather than the blast's — a fireball
     * bursts five times wider than the ball is, and drawing the lane at the burst
     * claimed the shot would sweep a band the width of a room.
     *
     * <p>And it carries no area at all, which is the half worth pinning. A circle
     * where the blast lands cannot be drawn honestly before the cast: the shot
     * bursts wherever it STOPS, at the first body or the first wall, so a circle
     * at the far end of its reach is right only when the shot hits nothing the
     * whole way — the one case nobody is aiming for.
     */
    @Test
    void aLaneIsAReachAndADirectionAndNothingElse() {
        var settings = DungeonSettings.load();
        var fireball = settings.skills().stream()
                .filter(skill -> skill.effect() == uz.duke.dungeon.skill.SkillEffect.SKILLSHOT)
                .findFirst().orElseThrow(() -> new AssertionError("nothing is a skillshot"));

        var drawn = uz.duke.dungeon.Main.rangeOf(fireball, settings.ringSelfRadius());

        assertEquals(uz.duke.client3d.SkillRange.Shape.DOWN_A_LANE, drawn.shape());
        assertEquals(fireball.hitWidth(), drawn.width(), 0.001f, "the lane is the shot");
        assertEquals(fireball.range(), drawn.reach(), 0.001f, "and the ring is his reach");
        assertEquals(0f, drawn.area(), 0.001f,
                "a blast circle is back, and it is a promise the shot cannot keep");
        assertTrue(fireball.radius() > 0f,
                "the blast itself is still real, and still hurts -- it is only not drawn");
    }

}
