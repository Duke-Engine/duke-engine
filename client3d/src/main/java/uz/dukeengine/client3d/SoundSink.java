package uz.dukeengine.client3d;

import com.jme3.math.Vector3f;

/**
 * Where a sound actually goes.
 *
 * <p>One interface for one reason: the rules about noise — which file of several,
 * how long since the last, how loud after four knobs — are worth checking, and
 * none of them needs a sound card. Behind this they can be checked in a build; in
 * front of it {@link AudioSink} hands them to jME.
 *
 * <p>It is also the answer to headless. A machine with no audio device gets a
 * sink that does nothing, and everything above carries on unaware — rather than
 * every caller learning to ask whether there is a speaker.
 */
interface SoundSink {

    /**
     * Play it once.
     *
     * @param at  where it happened, or null for something that merely happened —
     *     a menu click has no position, and giving it one would make it quieter
     *     when the camera moved
     */
    void play(String assetPath, float gain, Vector3f at);

    /** Start the loop, or stop it when the path is null. */
    void music(String assetPath, float gain);

    /** The music is already playing and should now be louder or quieter. */
    void musicGain(float gain);

    /** A sink for a machine that cannot make a sound, and for tests. */
    SoundSink SILENT = new SoundSink() {
        @Override
        public void play(String assetPath, float gain, Vector3f at) {
        }

        @Override
        public void music(String assetPath, float gain) {
        }

        @Override
        public void musicGain(float gain) {
        }
    };
}
