#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
uniform sampler2D m_Velocity;
uniform sampler2D m_Depth;
uniform sampler2D m_Tiles;
// The size of the picture in pixels, how much of a frame the shutter is open, and row 2 of the projection (m22, m23).
uniform vec2 m_Pixels;
uniform float m_Shutter;
uniform vec2 m_ProjectionZ;

varying vec2 texCoord;

// A tile of the tile maximum is this many pixels; a streak is at most this long, which is what the 3x3 tiles round a pixel reach
// (half a streak to either side of a pixel, and a tile is 16).
const int TILE = 16;
const float LONGEST = 32.0;
const int TAPS = 16;

// How far a pixel moves while the shutter is open, in pixels (the whole streak: it reaches half of it to each side).
vec2 streak(vec2 velocity) {
    vec2 v = velocity * m_Pixels * m_Shutter;
    float l = length(v);
    return l > LONGEST ? v * (LONGEST / l) : v;
}

float distanceOf(float depth) {
    return depth >= 1.0 ? 1.0e5 : m_ProjectionZ.y / (depth * 2.0 - 1.0 + m_ProjectionZ.x);
}

// How much a is in front of b, or level with it: 1, to nothing at an extent beyond.
float inFront(float a, float b) {
    return clamp(1.0 - (a - b) / (0.1 + 0.03 * min(a, b)), 0.0, 1.0);
}

// After McGuire et al. 2012, "A Reconstruction Filter for Plausible Motion Blur": a pixel takes the colour of the pixels
// along the longest streak round it that reach it, a thing that moves over what is behind it blurring onto it, nearer pixels
// counting before farther ones. The streaks are told apart from a pixel's own by its velocity, so a pixel that stands still
// keeps its own colour wherever no streak reaches it (that share of the window is its own, and a still picture is exact).
float cone(float away, float radius) {
    return clamp(1.0 - away / max(radius, 1.0e-4), 0.0, 1.0);
}

float cylinder(float away, float radius) {
    return 1.0 - smoothstep(0.95 * radius, 1.05 * radius + 1.0e-4, away);
}

void main() {
    vec4 colour = getColor(m_Texture, texCoord);
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 tiles = textureSize(m_Tiles, 0);
    ivec2 tile = pixel / TILE;
    vec2 along = vec2(0.0);
    float longest = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 s = streak(texelFetch(m_Tiles, clamp(tile + ivec2(x, y), ivec2(0), tiles - 1), 0).rg);
            float l = dot(s, s);
            if (l > longest) {
                longest = l;
                along = s;
            }
        }
    }
    // Nothing near moves as much as half a pixel: the picture as it is.
    if (longest < 0.25) {
        gl_FragColor = colour;
        return;
    }
    vec2 own = streak(texelFetch(m_Velocity, pixel, 0).rg);
    float ownReach = 0.5 * length(own);
    float ownDistance = distanceOf(texelFetch(m_Depth, pixel, 0).r);
    float moving = clamp(ownReach * 2.0, 0.0, 1.0);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float total = 1.0 / max(2.0 * ownReach, 1.0);
    vec3 sum = colour.rgb * total;
    ivec2 size = ivec2(m_Pixels);
    for (int i = 0; i < TAPS; i++) {
        float t = (float(i) + jitter) / float(TAPS) - 0.5;
        ivec2 at = clamp(ivec2(gl_FragCoord.xy + along * t), ivec2(0), size - 1);
        float away = length(vec2(at - pixel));
        float otherReach = 0.5 * length(streak(texelFetch(m_Velocity, at, 0).rg));
        float otherDistance = distanceOf(texelFetch(m_Depth, at, 0).r);
        float covers = inFront(otherDistance, ownDistance) * cone(away, otherReach);
        float weight = covers + inFront(ownDistance, otherDistance) * cone(away, ownReach)
                + 2.0 * cylinder(away, otherReach) * cylinder(away, ownReach);
        // A pixel that stands still is itself wherever nothing reaches it.
        float own_ = (1.0 - covers) * (1.0 - moving);
        sum += getColor(m_Texture, (vec2(at) + 0.5) / m_Pixels).rgb * weight + colour.rgb * own_;
        total += weight + own_;
    }
    gl_FragColor = vec4(sum / total, colour.a);
}
