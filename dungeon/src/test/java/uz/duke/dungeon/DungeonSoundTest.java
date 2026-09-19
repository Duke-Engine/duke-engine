package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * What the dungeon says it sounds like, and whether the files are really there.
 *
 * <p>Every way this goes wrong is silent. A misspelt path plays nothing; a cue
 * named for a creature that has since been renamed plays the general one instead;
 * a channel written {@code Voice} in one block and {@code voice} in another sorts
 * itself under two knobs. None of it throws and none of it shows on a screen —
 * which is the argument for holding it still here.
 */
class DungeonSoundTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** The file describes sounds at all, which is the thing most likely to break. */
    @Test
    void theShippedFileDescribesSounds() {
        assertFalse(SETTINGS.sounds().isEmpty(), "the dungeon should have a voice");
        assertTrue(SETTINGS.voiceGapSeconds() > 0f, "and she should pause between lines");
    }

    /**
     * Every file every cue names is really on the classpath.
     *
     * <p>The link nobody checks. The audio loads, the file names some audio, and
     * nothing says those are the same audio — so a typo is a moment that makes no
     * sound, at run time, with a line in a log nobody is reading.
     */
    @Test
    void everyFileNamedIsShipped() {
        for (var cue : SETTINGS.sounds()) {
            for (var file : cue.files()) {
                assertNotNull(DungeonSoundTest.class.getClassLoader().getResource(file),
                        cue.name() + " names " + file + ", which is not shipped");
            }
        }
    }

    /**
     * And every one of them really opens.
     *
     * <p>A step past "the file is there": a resource that exists and an audio file
     * jME can read are two different claims, and what sits between them is a wrong
     * extension, a sample rate nothing supports, or a file that arrived truncated.
     * All of those are silent at run time and a line in a log nobody reads.
     *
     * <p>No device is opened. Decoding needs none — the samples only reach a sound
     * card when something plays them — which is what lets a broken sound fail a
     * build rather than fail in front of a player.
     */
    @Test
    void everyFileNamedReallyOpens() {
        var assets = new com.jme3.asset.DesktopAssetManager(true);
        for (var cue : SETTINGS.sounds()) {
            for (var file : cue.files()) {
                var audio = assets.loadAudio(file);
                assertNotNull(audio, file + " is there but will not open");
                assertTrue(audio.getDuration() > 0f, file + " opens but has nothing in it");
            }
        }
    }

    /**
     * Anything meant to come from somewhere is mono.
     *
     * <p>Not a preference. OpenAL will not place a stereo sound — it is already
     * two places at once — and being asked to throws, which crashed the game the
     * first time an arrow was loosed. The packs ship a mixture, so the positional
     * ones were down-mixed; this is what stops the next file being added back in
     * stereo and taking the game down at the first shot.
     */
    @Test
    void everythingPlacedInTheWorldIsMono() {
        var assets = new com.jme3.asset.DesktopAssetManager(true);
        for (var cue : SETTINGS.sounds()) {
            if (!cue.positional()) {
                continue; // a menu click is nowhere, and may be as wide as it likes
            }
            for (var file : cue.files()) {
                assertEquals(1, assets.loadAudio(file).getChannels(),
                        file + " is placed in the world but is not mono — OpenAL"
                                + " refuses that, and refusing it is a crash");
            }
        }
    }

    /** And every channel named is one of the four the client has a knob for. */
    @Test
    void everyChannelNamedIsOneTheClientHas() {
        for (var cue : SETTINGS.sounds()) {
            boolean known = false;
            for (var channel : uz.duke.client3d.SoundBank.Channel.values()) {
                known |= channel.name().equalsIgnoreCase(cue.channel());
            }
            assertTrue(known, cue.name() + " is on channel '" + cue.channel()
                    + "', which is not one of the four");
        }
    }

    /**
     * The moments heard most have more than one recording.
     *
     * <p>Not a matter of taste. The bow goes hundreds of times in a run and the
     * hero walks the whole floor; one unvarying sample stops being a bow and
     * becomes a click, and stops being a walk and becomes a buzz.
     */
    @Test
    void whatIsHeardMostIsHeardSeveralWays() {
        for (var name : new String[] {"arrow_fired", "walking.Hero"}) {
            var cue = SETTINGS.sounds().stream()
                    .filter(c -> c.name().equals(name)).findFirst().orElseThrow();
            assertTrue(cue.files().size() > 1,
                    name + " has one recording and is heard all day");
        }
    }

    /** A walk has a stride in it, or it is not a walk. */
    @Test
    void theFootstepHasAStride() {
        var cue = SETTINGS.sounds().stream()
                .filter(c -> c.name().equals("walking.Hero")).findFirst().orElseThrow();

        assertTrue(cue.gapSeconds() > 0f, "without a gap the client plays one per frame");
    }

    /** A game may say nothing about sound, and then it is silent rather than broken. */
    @Test
    void aFileWithNoSoundsAtAllIsSilentRatherThanBroken() {
        var settings = DungeonSettings.parse("""
                World Dungeon
                  Generation = Test
                    MinRooms = 3
                  End
                End
                """);

        assertEquals(java.util.List.of(), settings.sounds());
    }

    /** What a block says is what comes out of it. */
    @Test
    void aBlockIsReadTheWayItIsWritten() {
        var settings = DungeonSettings.parse("""
                World Dungeon
                  Sounds = Settings
                    VoiceGapSeconds = 2.5
                  End
                End
                """, """
                Sound
                  Name = vo.move
                  Channel = Voice
                  Positional = No
                  Gain = 0.8
                  GapSeconds = 0.4
                  Files = [audio/voice/move_1.ogg, audio/voice/move_2.ogg]
                End
                """);

        assertEquals(2.5f, settings.voiceGapSeconds(), 0.001f);
        var cue = settings.sounds().get(0);
        assertEquals("vo.move", cue.name());
        assertEquals("Voice", cue.channel());
        assertFalse(cue.positional());
        assertEquals(0.8f, cue.gain(), 0.001f);
        assertEquals(0.4f, cue.gapSeconds(), 0.001f);
        assertEquals(java.util.List.of("audio/voice/move_1.ogg", "audio/voice/move_2.ogg"),
                cue.files(), "each file is the whole path its line writes");
    }

    /**
     * A creature with its own death still falls back for the ones without.
     *
     * <p>Checked through the client's own lookup rather than by reading the file,
     * because the fallback is the client's rule and this is the game's side of it.
     */
    @Test
    void aCreatureWithoutItsOwnDeathTakesTheGeneralOne() {
        var bank = uz.duke.client3d.SoundBank.create();
        for (var cue : SETTINGS.sounds()) {
            bank.cue(cue.name(), uz.duke.client3d.SoundBank.Channel.EFFECTS,
                    cue.positional(), cue.gain(), cue.gapSeconds(), cue.files());
        }
        var built = bank.build();

        assertNotNull(built.find("died.Boss"), "the boss was given its own");
        assertNotNull(built.find("died.SomethingNobodyWrote"),
                "and everything else falls through to the general one");
    }
}
