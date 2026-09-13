package uz.duke.client3d;

import java.util.Set;

/**
 * One layer of an effect: one kind of thing happening, drawn from one texture.
 *
 * <p><b>An effect is several of these at once, and has to be.</b> A single kind
 * of particle looks like a single kind of particle — a burst of orange dots is a
 * diagram of an explosion. What reads as an explosion is five things landing in
 * the same instant and each doing its own job: a white flash that is gone before
 * it is seen, fire that blooms and dies, smoke that rises slower than the fire and
 * outlives it, sparks thrown further than either, and a ring on the floor saying
 * how far it reached. None of them is an explosion. Together they are.
 *
 * <p>So the game names layers in its settings file, in order, and a new effect is
 * a handful of blocks there. A new <em>type</em> of layer is code, and the types
 * are the constants below.
 *
 * <p><b>Colour is here and not in the texture.</b> Every texture is a white shape
 * on transparency; the layer says what colour it is born and what colour it dies,
 * so one smoke texture is every smoke in the game and one star is a spark, an
 * ember and a shard of ice.
 *
 * <p><b>Curves, not lines.</b> Size, colour and fade all change along a power
 * curve (see {@link #sizeEase}), because a thing that grows at a constant speed
 * is a thing being resized, and nothing in a fight moves like that.
 *
 * @param type        what kind of thing this layer is — one of {@link #TYPES}
 * @param texture     the shape, as an asset path; empty for a soft round dot
 * @param additive    whether it ADDS light — fire, magic, sparks, flashes — or
 *                    covers what is behind it, as smoke and dust do. Smoke drawn
 *                    additively brightens the floor it is meant to be hiding
 * @param count       how many particles; for a continuous layer, how many may be
 *                    alive at once
 * @param rate        how many a second a continuous layer lets out, or 0 for all
 *                    of them at once
 * @param delay       seconds after the moment before this layer starts — what
 *                    lets smoke rise out of a fire rather than beside it
 * @param seconds     how long a lasting layer lasts: an aura, a mark, a light, a
 *                    trail laid along a path. 0 means as long as the SKILL lasts,
 *                    so the number is written once, on the skill
 * @param lifeMin     the shortest a particle lives
 * @param lifeMax     and the longest; each picks its own between them
 * @param sizeStart   how big a particle is born
 * @param sizeEnd     and how big it dies
 * @param sizeEase    the curve between: 1 is a straight line, 2–4 grows fast and
 *                    settles, -2 to -4 holds and then swells
 * @param sizeJitter  how much bigger or smaller each one may be, as a fraction
 * @param colourStart the colour it is born, 0xRRGGBB
 * @param colourEnd   and the colour it dies
 * @param alphaStart  how solid it is born, 0 to 1
 * @param alphaEnd    and how solid it dies
 * @param colourEase  the curve colour and solidity change along
 * @param fadeIn      what fraction of its life it spends appearing
 * @param fadeOut     and disappearing. For an aura, the part of its run spent
 *                    blinking, which is how a shield says it is about to go
 * @param speedMin    how fast a particle leaves, at least
 * @param speedMax    and at most
 * @param direction   which way it leaves — one of {@link #DIRECTIONS}
 * @param spread      how far from that direction it may stray, in degrees
 * @param radius      how far from the point it may be born
 * @param height      how far above the floor it is born
 * @param gravity     how hard it is pulled down; negative rises, as smoke does
 * @param drag        how quickly the air slows it
 * @param stretch     how much a streak is drawn out along its speed
 * @param spin        how fast it turns, in degrees a second
 * @param turn        the angle it is born at, in degrees — what a slash is laid
 *                    facing its target by
 * @param turnJitter  how much further it may be turned at random; 360 for smoke,
 *                    0 for anything that has to point somewhere
 * @param pulseRate   how many times a second it breathes, or 0
 * @param pulseDepth  and how deep the breath is, 0 to 1
 * @param at          where it happens — one of {@link #PLACES}
 * @param lightColour a {@link #LIGHT} layer's colour
 * @param lightPower  and how hard it burns
 * @param lightRadius and how far it reaches
 * @param fall        how far above its spot a falling layer starts, brought down
 *                    over its seconds — slow and then sudden, as anything dropped
 *                    is. What a meteor is, and 0 for everything else
 * @param measure     what its size and radius are counted in: {@link #UNITS}, or
 *                    {@link #REACH} for a shape that has to be exactly as wide as
 *                    the skill reaches, however far that is
 * @param cover       how much of what is behind it it hides, 0 to 1: 0 for light,
 *                    1 for stuff, and between for fire, which has to read on a pale
 *                    floor as well as a dark one. Unsaid, it follows
 *                    {@code additive}
 * @param rise        a {@link #PILLAR}'s: how much of its life the end that moves
 *                    takes to cross the whole height, 0 to 1 -- up out of the floor,
 *                    or down onto it
 * @param riseEase    and the curve it crosses along. 2–4 is fast and settling,
 *                    which is how light arrives
 * @param follows     whether, happening on somebody, it goes where he goes for as
 *                    long as it lasts. An {@link #AURA} always does
 */
public record EffectLayer(
        String type,
        String texture,
        boolean additive,
        int count,
        float rate,
        float delay,
        float seconds,
        float lifeMin,
        float lifeMax,
        float sizeStart,
        float sizeEnd,
        float sizeEase,
        float sizeJitter,
        int colourStart,
        int colourEnd,
        float alphaStart,
        float alphaEnd,
        float colourEase,
        float fadeIn,
        float fadeOut,
        float speedMin,
        float speedMax,
        String direction,
        float spread,
        float radius,
        float height,
        float gravity,
        float drag,
        float stretch,
        float spin,
        float turn,
        float turnJitter,
        float pulseRate,
        float pulseDepth,
        String at,
        int lightColour,
        float lightPower,
        float lightRadius,
        float fall,
        String measure,
        float cover,
        float rise,
        float riseEase,
        boolean follows) {

    // ---- the types: a new one is code, the rest is the settings file ----

    /** Everything at once from one point, and gone: sparks, fire, smoke, dust. */
    public static final String BURST = "BURST";
    /**
     * Let out continuously behind something that moves — a projectile, or a man
     * running — and left where it was let out, so it hangs in the air behind.
     */
    public static final String TRAIL = "TRAIL";
    /** Lying on the floor and opening outward: the shape of how far it reached. */
    public static final String RING = "RING";
    /**
     * Around a creature and going where it goes, for as long as the skill lasts:
     * a shield, a whirlwind, a focus.
     */
    public static final String AURA = "AURA";
    /** A flash: one or a few large shapes that bloom and are gone. */
    public static final String IMPACT = "IMPACT";
    /** A cut: laid facing the way the blow went, and quick. */
    public static final String ARC = "ARC";
    /** A line from one place to another, facing the camera across it. */
    public static final String BEAM = "BEAM";
    /**
     * A column of light standing on the floor, turned round its own upright to face
     * the camera: coming down onto whoever it is on, or rising up out of the ground
     * under him. Height is how tall it stands; Direction, UP or DOWN, which way it
     * goes; Rise, how quickly.
     */
    public static final String PILLAR = "PILLAR";
    /** Lying on the floor and staying — a warning, a scorch — breathing if told to. */
    public static final String MARK = "MARK";
    /** A light, flaring and fading, or carried by what is burning. */
    public static final String LIGHT = "LIGHT";

    public static final Set<String> TYPES =
            Set.of(BURST, TRAIL, RING, AURA, IMPACT, ARC, BEAM, PILLAR, MARK, LIGHT);

    // ---- which way a particle leaves ----

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";
    /** Away from the middle, across the floor. */
    public static final String OUT = "OUT";
    /** Any way at all. */
    public static final String ALL = "ALL";
    /** The way the skill went: from the caster to where he aimed, or along a run. */
    public static final String FORWARD = "FORWARD";
    public static final String BACK = "BACK";
    /** Not at all: it is born where it stays. */
    public static final String NONE = "NONE";

    public static final Set<String> DIRECTIONS =
            Set.of(UP, DOWN, OUT, ALL, FORWARD, BACK, NONE);

    // ---- where it happens ----

    /** Where the moment was: the cast's spot, or where a projectile is or landed. */
    public static final String SPOT = "SPOT";
    /** Where a run or a blink began. */
    public static final String FROM = "FROM";
    /** And where it ended. */
    public static final String TO = "TO";
    /** At both ends, as two of it. */
    public static final String BOTH = "BOTH";
    /** Along the whole of a run, laid down in the order it was run. */
    public static final String PATH = "PATH";
    /** On whoever cast it. */
    public static final String CASTER = "CASTER";
    /** On every enemy the skill's own reach took in. */
    public static final String CAUGHT = "CAUGHT";

    public static final Set<String> PLACES =
            Set.of(SPOT, FROM, TO, BOTH, PATH, CASTER, CAUGHT);

    // ---- what a size is counted in ----

    /** World units: a size of 10 is ten units across wherever it is drawn. */
    public static final String UNITS = "UNITS";
    /**
     * The skill's reach: a size of 2 is exactly as wide as the skill reaches and a
     * radius of 1 is anywhere inside it. For the shapes whose whole meaning is how
     * far it went -- a warning, a nova's rim -- so they cannot be tuned apart from
     * the skill they describe.
     */
    public static final String REACH = "REACH";

    public static final Set<String> MEASURES = Set.of(UNITS, REACH);

    public EffectLayer {
        type = type == null ? BURST : type;
        texture = texture == null ? "" : texture;
        direction = direction == null ? ALL : direction;
        at = at == null ? SPOT : at;
        measure = measure == null ? UNITS : measure;
        cover = Float.isNaN(cover) ? (additive ? 0f : 1f) : Math.clamp(cover, 0f, 1f);
        count = Math.max(0, count);
        lifeMin = Math.max(0.01f, lifeMin);
        lifeMax = Math.max(lifeMin, lifeMax);
        rate = Math.max(0f, rate);
        delay = Math.max(0f, delay);
        seconds = Math.max(0f, seconds);
        fall = Math.max(0f, fall);
        rise = Math.clamp(rise, 0f, 1f);
    }

    /** Whether it lets particles out over time rather than all at once. */
    public boolean continuous() {
        return rate > 0f;
    }

    /** Whether it lies flat on the floor rather than facing the camera. */
    public boolean lying() {
        return RING.equals(type) || MARK.equals(type) || ARC.equals(type);
    }

    /** Whether it lasts for a stretch of time rather than for one particle's life. */
    public boolean lasting() {
        return AURA.equals(type) || MARK.equals(type) || LIGHT.equals(type)
                || TRAIL.equals(type) && PATH.equals(at);
    }

    /** Whether it draws anything at all — a light draws nothing and still counts. */
    public boolean draws() {
        return !LIGHT.equals(type) && count > 0;
    }

    /** A layer that says nothing yet: see {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A layer, one field at a time.
     *
     * <p>Forty-four values in a row is forty-one chances to put a size where
     * a speed goes, and nothing would notice until an effect looked wrong. The
     * defaults here are what a block that names nothing gets, and they live in
     * exactly one place: the settings file only ever says what it changes.
     */
    public static final class Builder {
        private String type = BURST;
        private String texture = "";
        private boolean additive = true;
        private int count = 8;
        private float rate = 0f;
        private float delay = 0f;
        private float seconds = 0f;
        private float lifeMin = 0.3f;
        private float lifeMax = 0.5f;
        private float sizeStart = 1f;
        private float sizeEnd = 1f;
        private float sizeEase = 1f;
        private float sizeJitter = 0f;
        private int colourStart = 0xFFFFFF;
        private int colourEnd = 0xFFFFFF;
        private float alphaStart = 1f;
        private float alphaEnd = 1f;
        private float colourEase = 1f;
        private float fadeIn = 0f;
        private float fadeOut = 0.35f;
        private float speedMin = 0f;
        private float speedMax = 0f;
        private String direction = ALL;
        private float spread = 0f;
        private float radius = 0f;
        private float height = 0f;
        private float gravity = 0f;
        private float drag = 0f;
        private float stretch = 0f;
        private float spin = 0f;
        private float turn = 0f;
        private float turnJitter = 360f;
        private float pulseRate = 0f;
        private float pulseDepth = 0f;
        private String at = SPOT;
        private int lightColour = 0xFFFFFF;
        private float lightPower = 0f;
        private float lightRadius = 0f;
        private float fall = 0f;
        private String measure = UNITS;
        /** Not said: follows additive. */
        private float cover = Float.NaN;
        private float rise = 0.2f;
        private float riseEase = 3f;
        private boolean follows = false;

        private Builder() {
        }

        public Builder type(String value) {
            this.type = value;
            return this;
        }

        public Builder texture(String value) {
            this.texture = value;
            return this;
        }

        public Builder additive(boolean value) {
            this.additive = value;
            return this;
        }

        public Builder count(int value) {
            this.count = value;
            return this;
        }

        public Builder rate(float value) {
            this.rate = value;
            return this;
        }

        public Builder delay(float value) {
            this.delay = value;
            return this;
        }

        public Builder seconds(float value) {
            this.seconds = value;
            return this;
        }

        public Builder lifeMin(float value) {
            this.lifeMin = value;
            return this;
        }

        public Builder lifeMax(float value) {
            this.lifeMax = value;
            return this;
        }

        public Builder sizeStart(float value) {
            this.sizeStart = value;
            return this;
        }

        public Builder sizeEnd(float value) {
            this.sizeEnd = value;
            return this;
        }

        public Builder sizeEase(float value) {
            this.sizeEase = value;
            return this;
        }

        public Builder sizeJitter(float value) {
            this.sizeJitter = value;
            return this;
        }

        public Builder colourStart(int value) {
            this.colourStart = value;
            return this;
        }

        public Builder colourEnd(int value) {
            this.colourEnd = value;
            return this;
        }

        public Builder alphaStart(float value) {
            this.alphaStart = value;
            return this;
        }

        public Builder alphaEnd(float value) {
            this.alphaEnd = value;
            return this;
        }

        public Builder colourEase(float value) {
            this.colourEase = value;
            return this;
        }

        public Builder fadeIn(float value) {
            this.fadeIn = value;
            return this;
        }

        public Builder fadeOut(float value) {
            this.fadeOut = value;
            return this;
        }

        public Builder speedMin(float value) {
            this.speedMin = value;
            return this;
        }

        public Builder speedMax(float value) {
            this.speedMax = value;
            return this;
        }

        public Builder direction(String value) {
            this.direction = value;
            return this;
        }

        public Builder spread(float value) {
            this.spread = value;
            return this;
        }

        public Builder radius(float value) {
            this.radius = value;
            return this;
        }

        public Builder height(float value) {
            this.height = value;
            return this;
        }

        public Builder gravity(float value) {
            this.gravity = value;
            return this;
        }

        public Builder drag(float value) {
            this.drag = value;
            return this;
        }

        public Builder stretch(float value) {
            this.stretch = value;
            return this;
        }

        public Builder spin(float value) {
            this.spin = value;
            return this;
        }

        public Builder turn(float value) {
            this.turn = value;
            return this;
        }

        public Builder turnJitter(float value) {
            this.turnJitter = value;
            return this;
        }

        public Builder pulseRate(float value) {
            this.pulseRate = value;
            return this;
        }

        public Builder pulseDepth(float value) {
            this.pulseDepth = value;
            return this;
        }

        public Builder at(String value) {
            this.at = value;
            return this;
        }

        public Builder lightColour(int value) {
            this.lightColour = value;
            return this;
        }

        public Builder lightPower(float value) {
            this.lightPower = value;
            return this;
        }

        public Builder lightRadius(float value) {
            this.lightRadius = value;
            return this;
        }

        public Builder fall(float value) {
            this.fall = value;
            return this;
        }

        public Builder measure(String value) {
            this.measure = value;
            return this;
        }

        public Builder cover(float value) {
            this.cover = value;
            return this;
        }

        public Builder rise(float value) {
            this.rise = value;
            return this;
        }

        public Builder riseEase(float value) {
            this.riseEase = value;
            return this;
        }

        public Builder follows(boolean value) {
            this.follows = value;
            return this;
        }

        public EffectLayer build() {
            return new EffectLayer(type, texture, additive, count, rate, delay, seconds, lifeMin, lifeMax, sizeStart, sizeEnd, sizeEase, sizeJitter, colourStart, colourEnd, alphaStart, alphaEnd, colourEase, fadeIn, fadeOut, speedMin, speedMax, direction, spread, radius, height, gravity, drag, stretch, spin, turn, turnJitter, pulseRate, pulseDepth, at, lightColour, lightPower, lightRadius, fall, measure, cover, rise, riseEase, follows);
        }
    }
}
