#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Ssgi.glsllib"

uniform sampler2D m_Raw;
uniform sampler2D m_History;
uniform sampler2D m_Velocity;
uniform int m_Frame;

varying vec2 texCoord;

// The share of the sum that this frame's rays are, once there is a history to trust: the picture is the last twenty frames' or so.
const float NEW = 0.08;
// How many standard deviations of this frame's 3x3 the history may be off the mean of it before it is pulled in.
const float GAMMA = 1.5;
// How far apart in depth, as a share of it, the history and the pixel can be and be the same surface.
const float SAME_SURFACE = 0.05;

void main() {
    ivec2 cell = ivec2(gl_FragCoord.xy);
    vec4 raw = texelFetch(m_Raw, cell, 0);
    if (raw.a <= 0.0) {
        gl_FragColor = vec4(0.0);
        return;
    }
    ivec2 limit = textureSize(m_Raw, 0) - 1;
    vec3 sum = vec3(0.0);
    vec3 squares = vec3(0.0);
    float count = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
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
    bool trusted = all(greaterThan(before, vec2(0.0))) && all(lessThan(before, vec2(1.0))) && history.a > 0.0
            && abs(history.a - raw.a) < SAME_SURFACE * raw.a;
    if (!trusted) {
        gl_FragColor = raw;
        return;
    }
    vec3 kept = clamp(history.rgb, mean - GAMMA * deviation, mean + GAMMA * deviation);
    gl_FragColor = vec4(mix(kept, raw.rgb, NEW), raw.a);
}
