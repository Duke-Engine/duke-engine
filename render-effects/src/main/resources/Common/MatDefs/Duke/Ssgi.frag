#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"
#import "Common/ShaderLib/Ssgi.glsllib"

uniform COLORTEXTURE m_Texture;
uniform sampler2D m_Depth;
uniform sampler2D m_Surface;
uniform mat4 m_Projection;
uniform mat4 m_ProjectionInverse;
uniform float m_Near;
uniform float m_Reach;
uniform int m_Frame;

varying vec2 texCoord;

// Rays a frame from each half-resolution pixel, and the steps each is marched in.
const int RAYS = 2;
const int STEPS = 12;
// The first step is this many pixels away at least: a nearer hit is the surface's own flatness.
const float FIRST_STEP = 3.0;
// What a ray passes behind is a hit within a step's depth span, and this much of its depth over.
const float THICKNESS = 0.04;
// A hit brighter than this many times the picture's white (a sun glint, a lamp) counts as that bright: one pixel must not light a room.
const float LIMIT = 6.0;
// The share of the reach where a hit's light begins to fade out.
const float FADE_FROM = 0.6;

vec3 viewAt(vec2 uv, float depth) {
    vec4 at = m_ProjectionInverse * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return at.xyz / at.w;
}

// A direction in the hemisphere of n, more of them near n (cosine weighted), from two numbers in 0..1. Duff et al. 2017's basis.
vec3 cosineDirection(vec2 u, vec3 n) {
    float radius = sqrt(u.x);
    float phi = 6.2831853 * u.y;
    vec3 local = vec3(radius * cos(phi), radius * sin(phi), sqrt(max(0.0, 1.0 - u.x)));
    float s = n.z >= 0.0 ? 1.0 : -1.0;
    float a = -1.0 / (s + n.z);
    float b = n.x * n.y * a;
    vec3 t = vec3(1.0 + s * n.x * n.x * a, s * b, -s * n.x);
    vec3 bt = vec3(b, s + n.y * n.y * a, -n.y);
    return normalize(t * local.x + bt * local.y + n * local.z);
}

// Marches the ray over the depth in pixels (as Ssr.frag does: 1/w interpolated, so a step is as long on the screen as the next),
// steps growing with the square of their number: the surfaces near the pixel light it most and are found finest. True, with
// the pixel and the share of the reach where the ray passes behind what the depth holds; false where it leaves the picture or
// finds nothing.
bool trace(vec3 from, vec3 direction, float jitter, out ivec2 hitPixel, out float hitShare) {
    vec2 size = vec2(textureSize(m_Depth, 0));
    float reach = m_Reach;
    if (from.z + direction.z * reach > -m_Near) {
        reach = (-m_Near - from.z) / direction.z;
    }
    vec3 to = from + direction * reach;
    vec4 h0 = m_Projection * vec4(from, 1.0);
    vec4 h1 = m_Projection * vec4(to, 1.0);
    float k0 = 1.0 / h0.w;
    float k1 = 1.0 / h1.w;
    vec3 q0 = from * k0;
    vec3 q1 = to * k1;
    vec2 s0 = (h0.xy * k0 * 0.5 + 0.5) * size;
    vec2 s1 = (h1.xy * k1 * 0.5 + 0.5) * size;
    vec2 d = s1 - s0;
    float last = 1.0;
    if (s1.x > size.x - 1.0) last = min(last, (size.x - 1.0 - s0.x) / d.x);
    else if (s1.x < 1.0) last = min(last, (1.0 - s0.x) / d.x);
    if (s1.y > size.y - 1.0) last = min(last, (size.y - 1.0 - s0.y) / d.y);
    else if (s1.y < 1.0) last = min(last, (1.0 - s0.y) / d.y);
    float pixels = max(abs(d.x), abs(d.y)) * last;
    if (pixels < FIRST_STEP) {
        return false;
    }
    float zBefore = from.z;
    for (int i = 0; i < STEPS; i++) {
        float s = (float(i) + jitter) / float(STEPS);
        float t = (FIRST_STEP + (pixels - FIRST_STEP) * s * s) / pixels * last;
        float rayZ = (q0.z + (q1.z - q0.z) * t) / (k0 + (k1 - k0) * t);
        vec2 at = s0 + d * t;
        float depth = texelFetch(m_Depth, ivec2(at), 0).r;
        if (depth < 1.0) {
            float seen = viewAt(at / size, depth).z;
            float behind = seen - rayZ;
            if (behind > 0.0 && behind < abs(rayZ - zBefore) + THICKNESS * -rayZ) {
                hitPixel = ivec2(at);
                hitShare = t * reach / m_Reach;
                return true;
            }
        }
        zBefore = rayZ;
    }
    return false;
}

void main() {
    ivec2 pixel = ssgiSource(ivec2(gl_FragCoord.xy), m_Frame);
    vec2 size = vec2(textureSize(m_Depth, 0));
    float depth = texelFetch(m_Depth, pixel, 0).r;
    vec4 surface = texelFetch(m_Surface, pixel, 0);
    if (depth >= 1.0 || dot(surface.xyz, surface.xyz) < 0.5) {
        gl_FragColor = vec4(0.0);
        return;
    }
    vec3 normal = normalize(surface.xyz);
    vec3 position = viewAt((vec2(pixel) + 0.5) / size, depth);
    // Off the surface a little, farther where depth is coarser, so that it does not find itself.
    vec3 origin = position + normal * (0.01 - position.z * 0.004);
    vec2 where = vec2(pixel);
    vec2 offset = vec2(ssgiNoise(where), ssgiNoise(where + vec2(47.0, 17.0)));
    vec3 light = vec3(0.0);
    for (int ray = 0; ray < RAYS; ray++) {
        int number = m_Frame * RAYS + ray + 1;
        // R2 (Roberts 2018), turned about by a different amount in each pixel.
        vec2 u = fract(vec2(0.7548776662, 0.5698402910) * float(number) + offset);
        vec3 direction = cosineDirection(u, normal);
        float jitter = ssgiNoise(where + 5.588238 * float(number));
        ivec2 hit;
        float share;
        if (!trace(origin, direction, jitter, hit, share)) {
            continue;
        }
        vec3 hitNormal = texelFetch(m_Surface, hit, 0).xyz;
        // A ray that finds the back of a surface has found a part of the scene it cannot see.
        float facing = 1.0 - smoothstep(0.0, 0.3, dot(hitNormal, direction));
        vec3 colour = getColor(m_Texture, (vec2(hit) + 0.5) / size).rgb;
        colour *= min(1.0, LIMIT / max(max(colour.r, max(colour.g, colour.b)), 1e-4));
        light += colour * facing * (1.0 - smoothstep(FADE_FROM, 1.0, share));
    }
    gl_FragColor = vec4(light / float(RAYS), -position.z);
}
