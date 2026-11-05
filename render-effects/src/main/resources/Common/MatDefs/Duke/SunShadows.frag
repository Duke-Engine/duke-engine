#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
uniform DEPTHTEXTURE m_DepthTexture;
uniform sampler2D m_ShadowMap0;
uniform sampler2D m_ShadowMap1;
uniform sampler2D m_ShadowMap2;
uniform sampler2D m_ShadowMap3;
uniform mat4 m_LightViewProjectionMatrix0;
uniform mat4 m_LightViewProjectionMatrix1;
uniform mat4 m_LightViewProjectionMatrix2;
uniform mat4 m_LightViewProjectionMatrix3;
uniform mat4 m_ViewProjectionMatrixInverse;
uniform vec4 m_ViewDepth;
uniform vec4 m_Splits;
uniform float m_SplitStart;
uniform vec4 m_CascadeTexels;
uniform vec4 m_CascadeDepths;
uniform vec3 m_LightDir;
uniform vec3 m_LightRight;
uniform vec3 m_LightUp;
uniform vec3 m_ToSun;
uniform float m_ShadowIntensity;
uniform vec4 m_ShadowColor;
uniform vec2 g_ResolutionInverse;
#ifdef SOFT
    uniform float m_SunTangent;
#endif
#ifdef FADE
    uniform vec2 m_FadeInfo;
#endif
#ifdef SURFACE
    uniform sampler2D m_Surface;
    uniform vec3 m_SunInView;
#endif
#ifdef CONTACT
    uniform sampler2D m_SolidDepth;
    uniform mat4 m_ViewProjection;
#endif

varying vec2 texCoord;

const float MAP = SHADOWMAP_SIZE;
const int SEARCH = 12;
const int FILTER = 16;
// One turn of the golden angle: each sample of a spiral from the last, cheaper than a sine a sample.
const mat2 GOLDEN = mat2(-0.7373688, 0.6754903, -0.6754903, -0.7373688);
// Moved out along the face by this many texels at a grazing light, and toward the light by this many.
const float NORMAL_OFFSET = 2.0;
const float DEPTH_BIAS = 0.6;
// The steepest a receiver's plane is believed, as its cosine to the light; and how far it may part from the normal it is shaded by.
const float LEAST_COSINE = 0.08;
const float MOST_PARTED = 0.35;
// A penumbra's radius in texels: never sharper, never wider. Wider, a dozen samples start to miss a spear's shadow, and
// what the search misses at one pixel and finds at the next is a scatter of dots over the ground.
const float SHARPEST = 1.25;
const float WIDEST = 12.0;
// The share of a cascade that fades into the next.
const float BLEND = 0.15;
// Over this much of the cosine to the sun, a surface turns from lit to facing away.
const float TERMINATOR = 0.1;
// How far a contact shadow reaches, as a share of the distance from the eye: some 14 pixels at 1080p and a 45 degree view
// whatever the world's scale, so its steps cannot jump a pixel and so leave dots round anything thin.
const float CONTACT_REACH = 0.015;
const int CONTACT_STEPS = 12;

vec3 point;
float viewDepth;
vec3 face;
float toLight;
bool plane;
vec2 spin;
float dither;

vec3 worldAt(vec2 uv, float depth) {
    vec4 at = m_ViewProjectionMatrixInverse * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return at.xyz / at.w;
}

float depthAt(vec2 uv) {
    return fetchTextureSample(m_DepthTexture, uv, 0).r;
}

// The face under the pixel, toward the eye. Each axis from the neighbour on the same surface, so a silhouette has none of
// the other surface's slope. The maps and their bias want the real face; the normal buffer has a normal map's bumps on it.
vec3 faceAt(float depth) {
    vec2 x = vec2(g_ResolutionInverse.x, 0.0);
    vec2 y = vec2(0.0, g_ResolutionInverse.y);
    float left = depthAt(texCoord - x);
    float right = depthAt(texCoord + x);
    float down = depthAt(texCoord - y);
    float up = depthAt(texCoord + y);
    vec3 across = abs(right - depth) < abs(left - depth) ? worldAt(texCoord + x, right) - point : point - worldAt(texCoord - x, left);
    vec3 rising = abs(up - depth) < abs(down - depth) ? worldAt(texCoord + y, up) - point : point - worldAt(texCoord - y, down);
    return normalize(cross(across, rising));
}

// 1 where this map's texel at uv stands no nearer the sun than the receiver's plane does there. Compared at the texel's
// centre, where the map's depth was drawn: on a flat receiver that leaves nothing for a slope-scaled bias to hide.
float depthOver(sampler2D map, vec2 uv, vec3 coord, vec2 slope, float bias) {
    vec2 centre = (floor(uv * MAP) + 0.5) / MAP;
    return coord.z + dot(slope, centre - coord.xy) - bias - texture2D(map, centre).r;
}

float cascade(sampler2D map, mat4 lightViewProjection, float texel, float depths) {
    float across = sqrt(max(1.0 - toLight * toLight, 0.0));
    vec4 clip = lightViewProjection * vec4(point + face * (NORMAL_OFFSET * texel * across), 1.0);
    vec3 coord = clip.xyz * 0.5 + 0.5;
    if (coord.z >= 1.0) {
        return 1.0;
    }
    float world = texel * MAP;
    vec2 slope = plane ? vec2(dot(face, m_LightRight), dot(face, m_LightUp)) / toLight * world / depths : vec2(0.0);
    float bias = DEPTH_BIAS * texel / depths;
    float radius = SHARPEST / MAP;

    #ifdef SOFT
        // The sun's disc seen from the receiver, as far as a blocker could stand: the nearest cover could lie that wide.
        float reach = clamp(coord.z * depths * m_SunTangent / world, SHARPEST / MAP, WIDEST / MAP);
        float over = 0.0;
        float blockers = 0.0;
        vec2 turn = spin;
        for (int i = 0; i < SEARCH; i++) {
            turn = GOLDEN * turn;
            float gap = depthOver(map, coord.xy + turn * reach * sqrt((float(i) + 0.5) / float(SEARCH)), coord, slope, bias);
            if (gap > 0.0) {
                over += gap;
                blockers += 1.0;
            }
        }
        if (blockers == 0.0) {
            return 1.0;
        }
        if (blockers == float(SEARCH)) {
            return 0.0;
        }
        radius = clamp(over / blockers * depths * m_SunTangent / world, SHARPEST / MAP, reach);
    #endif

    float lit = 0.0;
    vec2 turn2 = spin;
    for (int i = 0; i < FILTER; i++) {
        turn2 = GOLDEN * turn2;
        lit += depthOver(map, coord.xy + turn2 * radius * sqrt((float(i) + 0.5) / float(FILTER)), coord, slope, bias) > 0.0 ? 0.0 : 1.0;
    }
    return lit / float(FILTER);
}

float cascadeNumber(int number) {
    if (number == 0) {
        return cascade(m_ShadowMap0, m_LightViewProjectionMatrix0, m_CascadeTexels.x, m_CascadeDepths.x);
    }
    if (number == 1) {
        return cascade(m_ShadowMap1, m_LightViewProjectionMatrix1, m_CascadeTexels.y, m_CascadeDepths.y);
    }
    if (number == 2) {
        return cascade(m_ShadowMap2, m_LightViewProjectionMatrix2, m_CascadeTexels.z, m_CascadeDepths.z);
    }
    return cascade(m_ShadowMap3, m_LightViewProjectionMatrix3, m_CascadeTexels.w, m_CascadeDepths.w);
}

float fromTheMaps() {
    if (viewDepth > m_Splits.w) {
        return 1.0;
    }
    int number = viewDepth < m_Splits.x ? 0 : viewDepth < m_Splits.y ? 1 : viewDepth < m_Splits.z ? 2 : 3;
    float lit = cascadeNumber(number);
    if (number < 3) {
        float start = number == 0 ? m_SplitStart : m_Splits[number - 1];
        float end = m_Splits[number];
        float into = (viewDepth - end) / (BLEND * (end - start)) + 1.0;
        if (into > 0.0) {
            lit = mix(lit, cascadeNumber(number + 1), into);
        }
    }
    #ifdef FADE
        lit = mix(lit, 1.0, clamp((viewDepth - m_FadeInfo.x) * m_FadeInfo.y, 0.0, 1.0));
    #endif
    return lit;
}

#ifdef CONTACT
// Walks a short way toward the sun over the scene's depth. What stands in the way must rise above this surface's plane,
// so the surface and what curves away from it never shadow themselves; and must stand no further in front of the ray
// than the ray is long, so a thing far in front of the ground never shadows the ground behind it on the screen.
float contactLight() {
    float reach = CONTACT_REACH * viewDepth;
    float lit = 1.0;
    for (int i = 0; i < CONTACT_STEPS; i++) {
        float along = (float(i) + dither) / float(CONTACT_STEPS);
        vec3 ray = point + m_ToSun * (reach * along);
        vec4 clip = m_ViewProjection * vec4(ray, 1.0);
        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            break;
        }
        vec3 there = worldAt(uv, texture2D(m_SolidDepth, uv).r);
        float infront = dot(m_ViewDepth, vec4(ray, 1.0)) - dot(m_ViewDepth, vec4(there, 1.0));
        if (infront > 0.0 && infront < reach && dot(face, there - point) > 0.03 * reach) {
            lit = along * along;
            break;
        }
    }
    return lit;
}
#endif

float light(float depth) {
    point = worldAt(texCoord, depth);
    viewDepth = dot(m_ViewDepth, vec4(point, 1.0));
    face = faceAt(depth);
    toLight = -dot(face, m_LightDir);
    // Interleaved gradient noise (Jimenez 2014): evenly spread, so it never bands. shortcut: a fine grain is left in wide
    // penumbras and a thin caster's shadow comes out dashed, turn it with the frame once TAA (3.9) can average it away.
    dither = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    spin = vec2(cos(dither * 6.2831853), sin(dither * 6.2831853));

    // Toward the sun, as shaded: the smooth normal, not the face, where there is one.
    float cosine = toLight;
    #ifdef ONCE
        cosine = dot(face, m_ToSun);
        #ifdef SURFACE
            vec3 smoothed = texture2D(m_Surface, texCoord).xyz;
            if (dot(smoothed, smoothed) > 0.25) {
                cosine = dot(smoothed, m_SunInView);
            }
        #endif
    #endif
    // On a spear or a finger the depths give no plane worth tilting a filter a metre wide by: it would lift the receiver out of a house's shadow.
    plane = toLight > LEAST_COSINE && abs(cosine - toLight) < MOST_PARTED;
    // Turned from the light on both counts it gets none, whatever the map says: its grazing depths are all the map would add.
    float lit = toLight > 0.0 || cosine > 0.0 ? fromTheMaps() : 1.0;
    #ifdef ONCE
        // The line where a round thing turns from the sun comes off the normal it is shaded with, not off the map.
        lit = min(lit, smoothstep(0.0, TERMINATOR, cosine));
        #ifdef CONTACT
            if (lit > 0.004) {
                lit = min(lit, contactLight());
            }
        #endif
    #endif
    return lit;
}

// shortcut: with MSAA each pixel takes its first sample's shadow, an edge's other samples follow it; shade per sample if a
// game's edges show it, at that many times the cost.
void main() {
    vec4 color = getColor(m_Texture, texCoord);
    float depth = depthAt(texCoord);
    // Nothing drawn there: no shadow to fall on it.
    if (depth == 1.0) {
        gl_FragColor = color;
        return;
    }
    float lit = light(depth) * m_ShadowIntensity + (1.0 - m_ShadowIntensity);
    // Lit, as drawn; in shadow, multiplied by the colour.
    gl_FragColor = vec4(color.rgb * mix(m_ShadowColor.rgb, vec3(1.0), lit), color.a);
}
