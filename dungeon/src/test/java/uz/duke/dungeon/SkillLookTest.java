package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.skill.SkillBook;

/**
 * What every skill looks like going off, checked against what the client can
 * actually draw.
 *
 * <p>Two files that have to agree and that nothing makes agree. A skill names a
 * {@code Effect} block by a string; a block that is not there is a skill
 * with no effect, and a block that is there but describes nothing is a skill with
 * an effect that draws nothing. Neither refuses at run time — both are a skill
 * that goes off in silence, which reads as a bug in the skill rather than as a
 * missing line in a data file, and so is looked for in the wrong place.
 *
 * <p>Nothing here says anything looks good. What it pins is the handful of ways
 * this arrangement fails quietly, plus two shapes worth holding still: an effect
 * is SHORT, and the knock is SMALL.
 */
class SkillLookTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** The skills a player casts: a hero's. A monster's has no key, no card and no look. */
    private static java.util.List<uz.duke.dungeon.skill.Skill> playersSkills() {
        var heroes = SETTINGS.heroes().stream().map(uz.duke.dungeon.content.HeroLook::name)
                .collect(java.util.stream.Collectors.toSet());
        return SETTINGS.skills().stream().filter(skill -> heroes.contains(skill.heroTemplate()))
                .toList();
    }

    /**
     * How long an effect may run.
     *
     * <p>A ceiling rather than a target, and a low one: the whole of a cast is
     * over before the next thing the player does. An effect he has to wait out is
     * one he learns to stop looking at, and the way this goes wrong is somebody
     * being pleased with a ring and slowing it down until it is furniture.
     *
     * <p>A standing MARK is exempt — it is not an effect so much as a promise, and
     * its whole point is the wait.
     */
    private static final float LONGEST_SECONDS = 0.8f;

    /** And how hard the camera may be knocked. See {@link #theKnockIsSmall}. */
    private static final float HARDEST_KNOCK = 2f;

    // ---- the two files agree ----

    /** Every skill in the shipped file says what it looks like. */
    @Test
    void everySkillSaysWhatItLooksLike() {
        assertFalse(SETTINGS.skills().isEmpty(), "the shipped file describes no skills at all");
        for (var skill : playersSkills()) {
            assertTrue(skill.hasLook(), skill.heroTemplate() + "'s " + skill.key()
                    + " goes off in silence: no Look line, so nothing is drawn for it");
        }
    }

    /** And what it names is a block that is really there. */
    @Test
    void everyLookNamedIsABlockThatExists() {
        var described = SETTINGS.effects().stream().map(look -> look.name()).toList();
        for (var skill : playersSkills()) {
            assertTrue(described.contains(skill.look()), skill.heroTemplate() + "'s "
                    + skill.key() + " looks like " + skill.look() + ", which nothing describes");
        }
    }

    /** And that block draws something, rather than being a name with nothing behind it. */
    @Test
    void everyLookNamedDrawsSomething() {
        var layered = SETTINGS.effectLayers().stream().map(DungeonSettings.EffectLayerArt::effect)
                .collect(java.util.stream.Collectors.toSet());
        for (var skill : playersSkills()) {
            assertTrue(layered.contains(skill.look()), skill.heroTemplate() + "'s " + skill.key() + " names "
                    + skill.look() + ", which has no layers and so draws nothing");
        }
    }

    // ---- and the two shapes worth holding still ----

    /** The knock is over before the next thing the player does. */
    @Test
    void everyKnockIsOverQuickly() {
        for (var look : SETTINGS.effects()) {
            assertTrue(look.shakeSeconds() <= LONGEST_SECONDS,
                    look.name() + " shakes the camera for " + look.shakeSeconds() + "s");
        }
    }

    /**
     * The knock is felt rather than seen.
     *
     * <p>A ceiling because this is the one number on the page with a real cost if
     * it is got wrong: a shake that can be SEEN is a shake a player asks you to
     * turn off, and past a point it is one that cannot be played through at all.
     * Every block in the shipped file is well under this; what the test is for is
     * the day somebody decides a meteor should really land.
     */
    @Test
    void theKnockIsSmall() {
        for (var look : SETTINGS.effects()) {
            assertTrue(look.shakePower() <= HARDEST_KNOCK, look.name() + " knocks the camera "
                    + look.shakePower() + " units, and past " + HARDEST_KNOCK
                    + " it is an earthquake rather than an impact");
        }
    }

    /** Nothing that opens a ring draws it in black. */
    @Test
    void everyRingIsSomeColour() {
        var looks = playersSkills().stream().map(uz.duke.dungeon.skill.Skill::look).collect(java.util.stream.Collectors.toSet());
        for (var layer : SETTINGS.effectLayers()) {
            if (!looks.contains(layer.effect()) || !"RING".equals(layer.fields().get("type"))) {
                continue;
            }
            assertFalse("0".equals(layer.fields().get("colourStart")), layer.effect() + "'s " + layer.name()
                    + " is drawn in black, which in a dark room is drawn not at all");
        }
    }

    // ---- a skill that goes on happening goes on being drawn ----

    /**
     * The whirlwind is drawn each time it lands, not only when it is pressed.
     *
     * <p>It turns for four seconds and lands eight times off one press. Drawn
     * once, the player has four seconds of a man spinning with no way to tell
     * whether the skill is still going except by counting.
     */
    @Test
    void aLastingSkillIsDrawnEachTimeItLands() {
        var arena = knightInARoom();
        var book = knightIn(arena).findModule(SkillBook.class);

        assertTrue(book.cast('R', 5), "the premise: his ultimate went off");
        int first = book.getCastMarkFrame();
        arena.runHeadless(skillOf("Knight", 'R').tickFrames() + 2);

        assertTrue(book.getCastMarkFrame() > first,
                "it landed again and asked for nothing to be drawn, so the turn is silent");
        assertEquals(1, book.getCastMarks().size(), "and it is still one ring, round him");
    }

    /**
     * And it does not thereby cancel the order the player gave.
     *
     * <p>The trap this is here for, and it was a real one. {@code HeroBrain}
     * reads {@code getLastCastFrame} to decide whether a cast has superseded a
     * move order; the first version of the above moved THAT number on at every
     * landing, so a knight told to walk somewhere and then given his ultimate
     * stopped dead, eight times, without anybody touching the mouse. The frame
     * the client draws by and the frame the brain listens to are two numbers.
     */
    @Test
    void beingDrawnAgainIsNotThePlayerSpeakingAgain() {
        var arena = knightInARoom();
        var book = knightIn(arena).findModule(SkillBook.class);

        book.cast('R', 5);
        int spokeAt = book.getLastCastFrame();
        arena.runHeadless(skillOf("Knight", 'R').tickFrames() * 3);

        assertEquals(spokeAt, book.getLastCastFrame(),
                "the whirlwind spoke for him, so his walk order was cancelled mid-ultimate");
    }

    // ---- what a mark is drawn ON ----

    /**
     * ★ The guard's disc belongs to the knight, not to the flagstone.
     *
     * <p>A place is right for nearly everything a cast draws: a nova went off HERE
     * and the floor goes on being the floor after the man walks away. The guard is
     * the one exception in the game — it is a condition he is IN for four seconds
     * — so a disc pinned to the stone he cast it from is left behind by his first
     * step and tells the player the stone is protected.
     *
     * <p>All that is claimed here is that the client is TOLD. What is made of it
     * is entirely the client's affair, and nothing about the fight changes either
     * way.
     */
    @Test
    void aGuardBelongsToTheKnightRatherThanToTheFloor() {
        var arena = knightInARoom();
        var knight = knightIn(arena);
        var book = knight.findModule(SkillBook.class);

        assertTrue(book.cast('E', 1), "the premise: his guard went off");

        assertEquals(knight.getId(), book.getCastMarks().getFirst().on(),
                "his guard was marked as the floor's, so nothing can keep it under him");
    }

    /**
     * And a charge leaves one of each: dust where he pushed off, and himself.
     *
     * <p>Both halves of the distinction in a single cast, which is why it is worth
     * a test of its own. The near end is where his boot struck and has nothing to
     * do with him afterwards; the far end is him. Getting it backwards drags a
     * puff of dust along behind a running man and leaves him arriving in silence.
     */
    @Test
    void aChargeLeavesItsDustBehindAndBringsHimselfAlong() {
        var arena = knightInARoom();
        var knight = knightIn(arena);
        var book = knight.findModule(SkillBook.class);

        assertTrue(book.cast('W', 1), "the premise: he charged");

        var marks = book.getCastMarks();
        assertEquals(2, marks.size(), "a dash is drawn at both ends of the run");
        assertEquals(ObjectId.INVALID, marks.get(0).on(),
                "the dust he kicked up is set to follow him about");
        assertEquals(knight.getId(), marks.get(1).on(),
                "the end he arrived at is not marked as his");
    }

    private static GameObject knightIn(uz.duke.game.DukeGame arena) {
        return arena.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Knight"))
                .findFirst().orElseThrow();
    }

    private static uz.duke.game.DukeGame knightInARoom() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                boolean edge = x == 0 || y == 0 || x == 39 || y == 29;
                text.append(edge ? '#' : '.');
            }
            text.append('\n');
        }
        var world = Dungeon.world(text.toString(), SETTINGS, Content.units());
        world.game().spawn("Knight", world.hero(), 150f, 150f);
        world.game().runHeadless(1);
        return world.game();
    }

    private static uz.duke.dungeon.skill.Skill skillOf(String hero, char key) {
        return SETTINGS.skillsFor(hero).stream().filter(skill -> skill.key() == key)
                .findFirst().orElseThrow();
    }

    // ---- and none of it reaches the fight ----

    /**
     * The look a skill names cannot change what the skill does.
     *
     * <p>The same claim {@code ProjectileEffectTest} makes about the burning, and
     * it has to be made again here because the {@code Look} line is on the SKILL
     * block rather than on an effect block — it travels in the same record as the
     * damage and the cooldown, and a record the simulation reads is a record that
     * can change what happens, with no way to tell by looking.
     */
    @Test
    void theLookCannotReachTheSimulation() {
        var with = DungeonSettings.load();
        var without = DungeonSettings.parse(withoutTheLooks(Content.data()));

        assertTrue(playersSkills().stream().allMatch(skill -> skill.hasLook()));
        assertTrue(without.skills().stream().noneMatch(skill -> skill.hasLook()),
                "the stripped file still names looks");
        assertEquals(signature(with), signature(without),
                "a floor played differently once the skills were drawn with nothing");
    }

    /** The same file with every skill's Look line cut out of it. */
    private static String withoutTheLooks(String file) {
        var kept = new StringBuilder();
        for (var line : file.split("\n", -1)) {
            if (!line.trim().startsWith("Look = ")) {
                kept.append(line).append('\n');
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
