package uz.duke.dungeon.content;

import java.util.List;

/**
 * One moment the game has a sound for.
 *
 * <p>The client raises moments by name and knows nothing about files; this is the
 * other half of that bargain, written in {@code ini/sounds/}. Adding a sound is a
 * block; adding a variation is a line inside one. There is no Java to write for
 * either, which is the point — a dungeon's noises are not an engine's business.
 *
 * <p>Names are dotted where the game wants to be particular: {@code died.Boss}
 * beside {@code died}, and the client asks for the first and takes the second.
 *
 * @param channel     which knob turns it down — Effects, Voice, Ui or Music
 * @param positional  whether it happens somewhere in the world, or merely happens
 * @param gain        its own loudness against the rest of its channel, because
 *     four packs recorded by four people are not mastered to each other
 * @param gapSeconds  the least time between two of these, or zero. A footstep
 *     needs a stride between it and the next one, or a walk is a buzz
 * @param label       what to call it on a settings screen, for the music a
 *     player picks between. Null for everything else, which is never named
 */
public record SoundArt(String name, String channel, boolean positional, float gain,
        float gapSeconds, List<String> files, String label) {

    public SoundArt {
        files = List.copyOf(files);
    }
}
