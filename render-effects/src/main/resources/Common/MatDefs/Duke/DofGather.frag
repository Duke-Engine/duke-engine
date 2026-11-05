#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_Texture;
uniform sampler2D m_Tiles;

const int TAPS = 48;
// The angle between taps that spreads them evenly over a disc (the golden angle).
const float GOLDEN = 2.39996323;
// A tile is this many pixels of the half size picture; the widest disc (DofCoc.glsllib's MAX_DIAMETER) is a quarter of its diameter in them.
const int TILE = 8;
const float MAX_RADIUS = 12.0;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// Scatter as gather: every pixel round this one spreads its colour over a disc of its own radius (spread), so this one takes
// what the discs that reach it hold, each pixel's share being its colour over the disc's area. The taps are spread over a disc as
// wide as the widest in the neighbourhood (the tiles' 3x3), so that a pixel in focus beside a blurred one still sees it.
// The far field gathers what lies beyond the focus plane, the near field what is in front of it and also says how much of the
// pixel it covers (the near field's edge is a soft one, over what is in focus). Sharp pixels spread over nothing (share 0),
// which keeps the far field from running onto what is in focus: the pixel there will not read it (DofComposite).
void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    ivec2 size = textureSize(m_Texture, 0);
    ivec2 tile = p / TILE;
    ivec2 tiles = textureSize(m_Tiles, 0);
    float widest = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            widest = max(widest, texelFetch(m_Tiles, clamp(tile + ivec2(x, y), ivec2(0), tiles - 1), 0).r);
        }
    }
    float radius = min(widest * 0.25, MAX_RADIUS);
    vec4 centre = texelFetch(m_Texture, p, 0);
#ifdef NEAR
    if (radius < 0.5) {
        gl_FragColor = vec4(0.0);
        return;
    }
    vec3 sum = vec3(0.0);
    float weights = 0.0;
    float covered = 0.0;
#else
    if (radius < 0.5) {
        gl_FragColor = vec4(centre.rgb, 1.0);
        return;
    }
    float own = max(centre.a * 0.25, 1.0);
    float first = 1.0 / (own * own * (1.0 + luma(centre.rgb)));
    vec3 sum = centre.rgb * first;
    float weights = first;
#endif
    for (int i = 0; i < TAPS; i++) {
        float away = radius * sqrt((float(i) + 0.5) / float(TAPS));
        float angle = float(i) * GOLDEN;
        vec4 s = texture2D(m_Texture, (vec2(p) + 0.5 + away * vec2(cos(angle), sin(angle))) / vec2(size));
#ifdef NEAR
        float spread = max(-s.a * 0.25, 0.0);
#else
        float spread = max(s.a * 0.25, 0.0);
#endif
        float share = clamp(spread - away + 1.0, 0.0, 1.0) / (max(spread, 1.0) * max(spread, 1.0));
#ifdef NEAR
        covered += share;
#endif
        float weight = share / (1.0 + luma(s.rgb));
        sum += s.rgb * weight;
        weights += weight;
    }
#ifdef NEAR
    gl_FragColor = weights > 0.0 ? vec4(sum / weights, clamp(covered * radius * radius / float(TAPS), 0.0, 1.0)) : vec4(0.0);
#else
    gl_FragColor = vec4(sum / weights, 1.0);
#endif
}
