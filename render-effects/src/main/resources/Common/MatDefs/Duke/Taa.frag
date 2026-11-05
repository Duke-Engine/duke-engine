#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
uniform sampler2D m_History;
uniform sampler2D m_Depth;
uniform sampler2D m_Velocity;
uniform float m_Fresh;

varying vec2 texCoord;

// The share of the new frame in the picture when the history is trusted, and the most it grows to as the picture moves
// (history resampled over a move is soft, and what moves fast has less of it to be right about).
const float BLEND = 0.1;
const float BLEND_MOVING = 0.35;
const float BLEND_PER_PIXEL = 0.05;
// How many standard deviations of the neighbourhood the history may stand away from its mean before it is pulled back.
const float GAMMA = 1.25;
const float BRIGHTEST = 60000.0;

// Y, Co, Cg: a luma that can be taken apart from the colour, which the clip below is the tighter for.
vec3 toYCoCg(vec3 c) {
    return vec3(0.25 * c.r + 0.5 * c.g + 0.25 * c.b, 0.5 * c.r - 0.5 * c.b, -0.25 * c.r + 0.5 * c.g - 0.25 * c.b);
}

vec3 fromYCoCg(vec3 c) {
    float t = c.x - c.z;
    return vec3(t + c.y, c.x + c.z, t - c.y);
}

// Karis: a bright pixel counts for less (the weight is 1 / (1 + luma)), so that one lit pixel of a thin edge cannot flicker
// the whole picture, and a mean of values is the mean of what the eye will see. Undone below.
vec3 squash(vec3 c) {
    c = clamp(c, 0.0, BRIGHTEST);
    return c / (1.0 + dot(c, vec3(0.25, 0.5, 0.25)));
}

vec3 unsquash(vec3 t) {
    return t / max(1.0 - dot(t, vec3(0.25, 0.5, 0.25)), 1e-3);
}

// Catmull-Rom by five bilinear taps (the corners left out), exact at a texel's centre, sharper than one bilinear tap.
vec3 history(vec2 uv) {
    vec2 size = vec2(textureSize(m_History, 0));
    vec2 position = uv * size;
    vec2 centre = floor(position - 0.5) + 0.5;
    vec2 f = position - centre;
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);
    vec2 w12 = w1 + w2;
    vec2 p0 = (centre - 1.0) / size;
    vec2 p3 = (centre + 2.0) / size;
    vec2 p12 = (centre + w2 / w12) / size;
    vec4 sum = vec4(texture2D(m_History, vec2(p12.x, p0.y)).rgb, 1.0) * (w12.x * w0.y)
            + vec4(texture2D(m_History, vec2(p0.x, p12.y)).rgb, 1.0) * (w0.x * w12.y)
            + vec4(texture2D(m_History, vec2(p12.x, p12.y)).rgb, 1.0) * (w12.x * w12.y)
            + vec4(texture2D(m_History, vec2(p3.x, p12.y)).rgb, 1.0) * (w3.x * w12.y)
            + vec4(texture2D(m_History, vec2(p12.x, p3.y)).rgb, 1.0) * (w12.x * w3.y);
    return max(sum.rgb / sum.a, 0.0);
}

// Pulls q toward the middle of the box until it stands on its face; inside, it stays.
vec3 clipTo(vec3 low, vec3 high, vec3 q) {
    vec3 middle = 0.5 * (high + low);
    vec3 extent = 0.5 * (high - low) + 1e-5;
    vec3 away = q - middle;
    vec3 scaled = abs(away) / extent;
    float outside = max(scaled.x, max(scaled.y, scaled.z));
    return outside > 1.0 ? middle + away / outside : q;
}

void main() {
    vec4 colour = getColor(m_Texture, texCoord);
    vec2 size = vec2(textureSize(m_Depth, 0));
    vec2 texel = 1.0 / size;

    // The 3x3 round the pixel: its colours' mean and spread, and the nearest surface in it, whose motion is the pixel's
    // (at an edge the motion of the thing in front, which is what the edge is made of).
    vec3 centre = toYCoCg(squash(colour.rgb));
    vec3 sum = vec3(0.0);
    vec3 sumSquares = vec3(0.0);
    vec3 low = vec3(1e9);
    vec3 high = vec3(-1e9);
    float nearest = 2.0;
    vec2 nearestAt = texCoord;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 at = texCoord + vec2(float(x), float(y)) * texel;
            vec3 c = (x == 0 && y == 0) ? centre : toYCoCg(squash(getColor(m_Texture, at).rgb));
            sum += c;
            sumSquares += c * c;
            low = min(low, c);
            high = max(high, c);
            float d = texture2D(m_Depth, at).r;
            if (d < nearest) {
                nearest = d;
                nearestAt = at;
            }
        }
    }
    vec2 velocity = texture2D(m_Velocity, nearestAt).rg;
    vec2 before = texCoord - velocity;
    if (m_Fresh > 0.5 || any(lessThan(before, vec2(0.0))) || any(greaterThan(before, vec2(1.0)))) {
        gl_FragColor = colour;
        return;
    }

    vec3 mean = sum / 9.0;
    vec3 spread = sqrt(max(sumSquares / 9.0 - mean * mean, 0.0));
    vec3 past = clipTo(max(mean - GAMMA * spread, low), min(mean + GAMMA * spread, high),
            toYCoCg(squash(history(before))));
    float share = clamp(BLEND + length(velocity * size) * BLEND_PER_PIXEL, BLEND, BLEND_MOVING);
    gl_FragColor = vec4(min(unsquash(fromYCoCg(mix(past, centre, share))), BRIGHTEST), colour.a);
}
