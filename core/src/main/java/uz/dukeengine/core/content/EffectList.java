package uz.dukeengine.core.content;

import java.util.List;
import uz.dukeengine.core.data.Link;

/**
 * Several things at once, by one name — the reference game's {@code FXList}: its entries all play together
 * where the list is played. Wherever a game names an effect it may name one of these instead.
 *
 * <p><b>Why.</b> A moment used to name one thing, a layered {@link Effect} or a {@link ParticleSystem}, and an
 * RTS's moments are rarely one thing. Measured in the RTS this was taken from: of its 431 lists, 331 start
 * particle systems and 241 of those more than one — a flash, a cloud and a shower of sparks, each at its own
 * offset — beside 321 sounds, 140 camera shakes, 65 light pulses, 45 scorch marks and 18 tracers. A game that
 * could name one of them drew the flash and lost the rest.
 *
 * <p>The client plays every entry, drawing only: nothing here reaches the simulation, and every number an entry
 * draws comes from the client's own random numbers. <b>Space is Z-up</b> and <b>time is frames</b> of the
 * game's 30 a second, as in {@link ParticleSystem}. A <b>range</b> is {@code [min, max]}, a value drawn between
 * the two each time; one number is that number, and none is 0.
 *
 * @param entries what plays, in the order written
 */
public record EffectList(String name, List<Entry> entries) {

    /** What a block leaves out. */
    static final EffectList DEFAULTS = new EffectList(null, List.of());

    public EffectList {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** One thing a list plays. */
    public sealed interface Entry permits ParticleSystem, Sound, LightPulse, Shake, Scorch, Tracer, AtBone {
    }

    /**
     * A particle system, or {@code count} of them, where the list plays.
     *
     * @param offset               {@code [x, y, z]} from where the list plays, turned the way the thing faces
     * @param radius               each copy a distance drawn from this from the middle, at an angle of its own
     * @param height               added to the height it starts at
     * @param createAtGroundHeight on the ground under where it would have been, instead of at a height
     * @param initialDelay         frames before it starts, drawn from this, instead of the system's own delay;
     *                             none keeps the system's own
     * @param rotateX              radians it is turned about its own x, before y, before z
     * @param orientToObject       turned the way the thing, or the bone, it plays at is turned; otherwise it is
     *                             drawn unturned, as the system is written
     * @param attachToObject       riding the thing it plays at — its place and turn — until the thing is gone
     * @param ricochet             faced away from whoever caused it, as a shot glancing off, instead of the way
     *                             the thing faces: its offset turned so, and — with {@code orientToObject} — the
     *                             system too, as the reference turns it
     * @param useCallersRadius     a sphere or cylinder system taking the radius the list was played with — a
     *                             blast's — as the radius it lets particles out within
     */
    public record ParticleSystem(@Link(uz.dukeengine.core.content.ParticleSystem.class) String name, int count,
            List<Float> offset, List<Float> radius, List<Float> height, boolean createAtGroundHeight,
            List<Float> initialDelay, float rotateX, float rotateY, float rotateZ, boolean orientToObject,
            boolean attachToObject, boolean ricochet, boolean useCallersRadius) implements Entry {

        static final ParticleSystem DEFAULTS = new ParticleSystem(null, 1, List.of(), List.of(), List.of(), false,
                List.of(), 0f, 0f, 0f, false, false, false, false);

        public ParticleSystem {
            offset = offset == null ? List.of() : List.copyOf(offset);
            radius = radius == null ? List.of() : List.copyOf(radius);
            height = height == null ? List.of() : List.copyOf(height);
            initialDelay = initialDelay == null ? List.of() : List.copyOf(initialDelay);
        }
    }

    /** A sound, heard where the list plays: a cue of the game's sound bank, by name. */
    public record Sound(@Link(uz.dukeengine.core.content.Sound.class) String name) implements Entry {
    }

    /**
     * A light that swells and dies away where the list plays.
     *
     * @param colour      what it shines, packed {@code 0xRRGGBB}
     * @param radius      how far it reaches
     * @param radiusShare how far it reaches as a share of the size of the thing it plays at — 1.5 is half as far
     *                    again as the thing is wide — instead of {@code radius}, where there is a thing
     * @param riseFrames  frames it takes to reach its brightest
     * @param fallFrames  frames it takes from there to go out
     */
    public record LightPulse(int colour, float radius, float radiusShare, int riseFrames, int fallFrames)
            implements Entry {
    }

    /** The camera knocked, harder the nearer it is looking — the reference's six strengths. */
    public record Shake(Strength strength) implements Entry {

        static final Shake DEFAULTS = new Shake(Strength.NORMAL);

        public Shake {
            strength = strength == null ? Strength.NORMAL : strength;
        }

        public enum Strength {
            SUBTLE, NORMAL, STRONG, SEVERE, CINE_EXTREME, CINE_INSANE
        }
    }

    /**
     * A mark burned into the ground, kept; past the most the client keeps, the oldest is let go of.
     *
     * @param pictures the picture it is drawn with, whole paths from the resource root; with more than one, one
     *                 of them each time, at random
     * @param radius   how far across it reaches from where the list plays
     */
    public record Scorch(List<String> pictures, float radius) implements Entry {

        public Scorch {
            pictures = pictures == null ? List.of() : List.copyOf(pictures);
        }
    }

    /**
     * A streak flying from where the list plays toward the other end — what was shot at.
     *
     * @param speed       how far it goes a frame; 0 crosses the whole way at once
     * @param length      how long it is
     * @param width       how wide it is
     * @param colour      what it is drawn, packed {@code 0xRRGGBB}
     * @param probability the share of the times the list plays that draw one: 0.3 is three in ten
     * @param decayAt     how long it lives, as a share of the frames it takes to get there — fading away over
     *                    them, so at 0.5 it is gone halfway
     */
    public record Tracer(float speed, float length, float width, int colour, float probability, float decayAt)
            implements Entry {

        static final Tracer DEFAULTS = new Tracer(0f, 10f, 1f, 0xFFFFFF, 1f, 1f);
    }

    /**
     * Another list played at every bone of the thing's model by this name: {@code NAME01}, {@code NAME02} …
     * in turn, or {@code NAME} itself where the model numbers none.
     *
     * @param orientToBone the list turned the way each bone is turned, rather than the way the thing is
     */
    public record AtBone(@Link(EffectList.class) String effect, String bone, boolean orientToBone) implements Entry {

        static final AtBone DEFAULTS = new AtBone(null, null, true);
    }
}
