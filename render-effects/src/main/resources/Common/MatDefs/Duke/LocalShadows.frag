#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
uniform DEPTHTEXTURE m_DepthTexture;
uniform sampler2D m_Atlas;
uniform vec2 m_AtlasSize;
uniform mat4 m_ViewProjectionMatrixInverse;
uniform int m_Frame;
uniform vec2 g_ResolutionInverse;
uniform vec4 m_LightPos[SLOTS];
uniform vec4 m_LightAim[SLOTS];
uniform vec4 m_LightLook[SLOTS];
uniform vec4 m_LightCone[SLOTS];
uniform vec4 m_LightTile[SLOTS];
uniform mat4 m_FaceMatrix[SLOTS * 6];
uniform vec3 m_Fill;
uniform vec4 m_LightColour[SLOTS];

varying vec2 texCoord;

const int TAPS = 8;
// One turn of the golden angle: each tap of a spiral from the last.
const mat2 GOLDEN = mat2(-0.7373688, 0.6754903, -0.6754903, -0.7373688);
// The filter's reach in texels: modest, a light's edge is soft by the size of a lamp and not by its map's reach.
const float REACH = 1.6;
// Moved out along the face by this many texels at a grazing light.
const float NORMAL_OFFSET = 1.5;
// What the receiver may stand behind the map's depth by and still be lit, in texels, and for each of the filter's own
// reach that the plane tilts: a slope of 1 takes REACH texels of depth across the filter.
const float DEPTH_BIAS = 1.0;
const float MOST_SLOPE = 4.0;
// Below this share of a pixel's light a shadow is not worth the taps.
const float LEAST_SHARE = 0.02;

vec3 point;
vec2 spin;

vec3 worldAt(vec2 uv, float depth) {
    vec4 at = m_ViewProjectionMatrixInverse * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return at.xyz / at.w;
}

float depthAt(vec2 uv) {
    return fetchTextureSample(m_DepthTexture, uv, 0).r;
}

// The face under the pixel, toward the eye, each axis from the neighbour on the same surface.
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

// A cube's face by the axis a direction runs furthest along: +x, -x, +y, -y, +z, -z.
int faceOf(vec3 v) {
    vec3 a = abs(v);
    if (a.x >= a.y && a.x >= a.z) {
        return v.x > 0.0 ? 0 : 1;
    }
    if (a.y >= a.z) {
        return v.y > 0.0 ? 2 : 3;
    }
    return v.z > 0.0 ? 4 : 5;
}

// The distance along the camera of a depth the map holds.
float lineal(float z, float near, float far) {
    return 2.0 * near * far / (far + near - (2.0 * z - 1.0) * (far - near));
}

// How much of this light reaches the point, 0 to 1, by the filter over its map.
float visibility(int i, vec3 normal, vec3 toLight) {
    vec4 pos = m_LightPos[i];
    vec4 aim = m_LightAim[i];
    vec4 look = m_LightLook[i];
    vec4 tile = m_LightTile[i];
    bool spot = m_LightCone[i].y > 0.5;
    vec3 from = point - pos.xyz;
    int face = spot ? 0 : faceOf(from);
    float along = spot ? dot(from, aim.xyz) : abs(from[face / 2]);
    // The size of one texel of the map where the point is.
    float texel = look.z * max(along, look.w);
    float cosine = dot(normal, toLight);
    float across = sqrt(max(1.0 - cosine * cosine, 0.0));
    vec3 shifted = point + normal * (NORMAL_OFFSET * texel * across);
    vec4 clip = m_FaceMatrix[i * 6 + face] * vec4(shifted, 1.0);
    if (clip.w <= 0.0) {
        return 1.0;
    }
    vec2 local = clip.xy / clip.w;
    if (local.x < 0.0 || local.x > 1.0 || local.y < 0.0 || local.y > 1.0) {
        return 1.0;
    }
    vec3 shiftedFrom = shifted - pos.xyz;
    float receiver = spot ? dot(shiftedFrom, aim.xyz) : abs(shiftedFrom[face / 2]);
    vec2 origin = tile.xy + (spot ? vec2(0.0) : vec2(float(face - face / 3 * 3), float(face / 3)) * tile.zw);
    vec2 inset = 0.5 / m_AtlasSize;
    vec2 lowest = origin + inset;
    vec2 highest = origin + tile.zw - inset;
    vec2 centre = origin + local * tile.zw;
    float slope = min(across / max(cosine, 0.05), MOST_SLOPE);
    float bias = texel * (DEPTH_BIAS + REACH * slope);
    vec2 radius = REACH / m_AtlasSize;
    float lit = 0.0;
    vec2 turn = spin;
    for (int k = 0; k < TAPS; k++) {
        turn = GOLDEN * turn;
        vec2 at = clamp(centre + turn * radius * sqrt((float(k) + 0.5) / float(TAPS)), lowest, highest);
        float blocker = lineal(texture2D(m_Atlas, at).r, look.w, pos.w);
        lit += receiver - bias <= blocker ? 1.0 : 0.0;
    }
    return lit / float(TAPS);
}

void main() {
    vec4 color = getColor(m_Texture, texCoord);
    float depth = depthAt(texCoord);
    // Nothing drawn there: no shadow to fall on it.
    if (depth == 1.0) {
        gl_FragColor = color;
        return;
    }
    point = worldAt(texCoord, depth);
    float dither = fract(52.9829189 * fract(dot(gl_FragCoord.xy + 5.588238 * float(m_Frame), vec2(0.06711056, 0.00583715))));
    spin = vec2(cos(dither * 6.2831853), sin(dither * 6.2831853));
    vec3 normal = vec3(0.0);
    bool known = false;
    vec3 scale = vec3(1.0);
    for (int i = 0; i < SLOTS; i++) {
        vec4 look = m_LightLook[i];
        if (look.y <= 0.0) {
            continue;
        }
        vec4 pos = m_LightPos[i];
        vec3 toLight = pos.xyz - point;
        float gap = length(toLight);
        if (gap >= pos.w) {
            continue;
        }
        if (!known) {
            normal = faceAt(depth);
            known = true;
        }
        toLight /= gap;
        float cosine = dot(normal, toLight);
        if (cosine <= 0.0) {
            continue;
        }
        // How much the light has left at this distance, as the lit materials work it out (their SRGB variant).
        float reach = (1.0 - gap / pos.w) / (1.0 + gap * gap / pos.w);
        vec4 cone = m_LightCone[i];
        if (cone.y > 0.5) {
            float outer = m_LightAim[i].w;
            float inside = clamp((dot(-toLight, m_LightAim[i].xyz) - outer) / (cone.x - outer), 0.0, 1.0);
            reach *= inside * inside * inside * inside;
        }
        // Each channel's share of the light on the surface: all of it where nothing else lights it, little in a sunny yard.
        vec3 energy = m_LightColour[i].rgb * (reach * cosine);
        vec3 share = energy / (energy + m_Fill);
        if (max(share.r, max(share.g, share.b)) < LEAST_SHARE) {
            continue;
        }
        scale *= 1.0 - share * (look.y * (1.0 - visibility(i, normal, toLight)));
    }
    gl_FragColor = vec4(color.rgb * scale, color.a);
}
