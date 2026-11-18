#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Ssgi.glsllib"

// GLRenderer binds each outFragData[i] to colour target i: the sum, and how many frames it holds.
#if __VERSION__ >= 130
  out vec4 outFragData[2];
  #define OUT_SUM outFragData[0]
  #define OUT_COUNT outFragData[1]
#else
  #define OUT_SUM gl_FragData[0]
  #define OUT_COUNT gl_FragData[1]
#endif

uniform sampler2D m_Raw;
uniform sampler2D m_History;
uniform sampler2D m_HistoryCount;
uniform sampler2D m_Velocity;
uniform int m_Frame;

varying vec2 texCoord;

// The share of the sum that this frame's rays are, once there is a history to trust: the picture is the last fifty frames' or so.
// Two rays a pixel are few: at 0.08 a face lit mostly by bounced light crawled with their noise from frame to frame.
const float NEW = 0.02;
// How many standard deviations of this frame's 5x5 the history may be off the mean of it before it is pulled in. A 3x3 of two
// rays a pixel was too few: its bounds were low whenever none of its rays found a lit thing, and the sum, clipped to them,
// settled darker than the light it sums; after every restart the picture darkened back to that over a second.
const float GAMMA = 3.0;
// How far apart in depth, as a share of it, the history and the pixel can be and be the same surface.
const float SAME_SURFACE = 0.05;
// Frames of a young sum that take this frame's 5x5 for the pixel's own two rays.
const float YOUNG = 9.0;

void main() {
    ivec2 cell = ivec2(gl_FragCoord.xy);
    vec4 raw = texelFetch(m_Raw, cell, 0);
    if (raw.a <= 0.0) {
        OUT_SUM = vec4(0.0);
        OUT_COUNT = vec4(0.0);
        return;
    }
    ivec2 limit = textureSize(m_Raw, 0) - 1;
    vec3 sum = vec3(0.0);
    vec3 squares = vec3(0.0);
    float count = 0.0;
    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            vec4 tap = texelFetch(m_Raw, clamp(cell + ivec2(x, y), ivec2(0), limit), 0);
            if (tap.a > 0.0 && abs(tap.a - raw.a) < SAME_SURFACE * raw.a) {
                sum += tap.rgb;
                squares += tap.rgb * tap.rgb;
                count += 1.0;
            }
        }
    }
    vec3 mean = sum / count;
    vec3 deviation = sqrt(max(squares / count - mean * mean, 0.0));

    ivec2 pixel = ssgiSource(cell, m_Frame);
    vec2 uv = (vec2(pixel) + 0.5) / vec2(textureSize(m_Velocity, 0));
    vec2 before = uv - texelFetch(m_Velocity, pixel, 0).rg;
    vec4 history = texture2D(m_History, before);
    // Fresh targets hold whatever was in memory, NaN too, which fails every comparison: none of it is trusted.
    float held = texture2D(m_HistoryCount, before).r;
    bool trusted = held >= 1.0 && all(greaterThan(before, vec2(0.0))) && all(lessThan(before, vec2(1.0))) && history.a > 0.0
            && abs(history.a - raw.a) < SAME_SURFACE * raw.a;
    // A surface new to the view starts from its 5x5's mean, many pixels' rays and not one's two: its own two alone are
    // noise the sum would keep for its fifty frames, and what came back into view crawled with it before it settled.
    if (!trusted) {
        OUT_SUM = vec4(mean, raw.a);
        OUT_COUNT = vec4(1.0);
        return;
    }
    // A young sum is an even mean of the frames it holds, not a slow blend that keeps its first noisy frame for long.
    float frames = min(held + 1.0, 1.0 / NEW);
    vec3 fresh = mix(mean, raw.rgb, clamp((frames - 1.0) / (YOUNG - 1.0), 0.0, 1.0));
    vec3 kept = clamp(history.rgb, mean - GAMMA * deviation, mean + GAMMA * deviation);
    OUT_SUM = vec4(mix(kept, fresh, 1.0 / frames), raw.a);
    OUT_COUNT = vec4(frames);
}
