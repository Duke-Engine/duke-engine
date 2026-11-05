#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
uniform DEPTHTEXTURE m_DepthTexture;
uniform mat4 m_ViewProjectionMatrixInverse;
uniform vec3 g_CameraPosition;
uniform float m_Density;
uniform float m_Falloff;
uniform float m_SkyDistance;
uniform vec3 m_ToSun;
uniform vec3 m_Sun;
uniform vec3 m_Air;
#ifdef SHAFTS
    uniform sampler2D m_Shafts;
#endif

varying vec2 texCoord;

const float PI = 3.14159265;
// How forward the fog scatters the sun (Henyey-Greenstein): a wide glow round the sun, hardly any off to the side.
const float FORWARD = 0.6;
// How far apart in distance, as a share of it, two pixels can be and still count as one surface: as ShaftsBlur.frag.
const float SAME_SURFACE = 0.2;

// (1 - e^-x) / x, whose limit at 0 is 1: the series where the quotient would lose its digits.
float stretch(float x) {
    return abs(x) < 0.01 ? 1.0 - x * 0.5 + x * x / 6.0 - x * x * x / 24.0 : (1.0 - exp(-x)) / x;
}

// The density along a ray, integrated: density * e^(-falloff * y) at height y. A ray of the given length from the eye at
// eyeY, rising by dirY a unit, has atEye * length * stretch(falloff * dirY * length) of it. Keep HeightFogFilter.opticalDepth the same.
float opticalDepth(float eyeY, float dirY, float away) {
    float atEye = m_Density * exp(min(-m_Falloff * eyeY, 80.0));
    return atEye * away * stretch(max(m_Falloff * dirY * away, -30.0));
}

#ifdef SHAFTS
// The share of the sun that reaches the air, from the four half size pixels round this one, bilinear and weighed by how
// near their distance is to this pixel's, so that a house's edge is not lit through by the sky behind it.
float sunReaching(float away) {
    ivec2 size = textureSize(m_Shafts, 0);
    vec2 at = texCoord * vec2(size) - 0.5;
    ivec2 base = ivec2(floor(at));
    vec2 across = at - vec2(base);
    float sum = 0.0;
    float total = 0.0;
    for (int y = 0; y < 2; y++) {
        for (int x = 0; x < 2; x++) {
            vec2 tap = texelFetch(m_Shafts, clamp(base + ivec2(x, y), ivec2(0), size - 1), 0).rg;
            float weight = (x == 0 ? 1.0 - across.x : across.x) * (y == 0 ? 1.0 - across.y : across.y);
            weight *= max(0.02, 1.0 - abs(tap.g - away) / (SAME_SURFACE * away));
            sum += tap.r * weight;
            total += weight;
        }
    }
    return sum / total;
}
#endif

void main() {
    vec4 colour = getColor(m_Texture, texCoord);
    float depth = fetchTextureSample(m_DepthTexture, texCoord, 0).r;
    vec4 at = m_ViewProjectionMatrixInverse * vec4(vec3(texCoord, depth) * 2.0 - 1.0, 1.0);
    vec3 ray = at.xyz / at.w - g_CameraPosition;
    float away = depth >= 1.0 ? m_SkyDistance : length(ray);
    vec3 direction = normalize(ray);

    float through = exp(-opticalDepth(g_CameraPosition.y, direction.y, away));
    float cosine = dot(direction, m_ToSun);
    float phase = (1.0 - FORWARD * FORWARD) / (4.0 * PI * pow(1.0 + FORWARD * FORWARD - 2.0 * FORWARD * cosine, 1.5));
    float sunlit = 1.0;
    #ifdef SHAFTS
        sunlit = sunReaching(away);
    #endif
    // The sun's colour is the radiance a unit surface facing it returns, its irradiance over pi.
    vec3 scattered = m_Air + m_Sun * (PI * phase * sunlit);
    gl_FragColor = vec4(colour.rgb * through + scattered * (1.0 - through), colour.a);
}
