#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

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
uniform vec4 m_CascadeTexels;
uniform vec4 m_CascadeDepths;
uniform vec3 g_CameraPosition;
uniform float m_Density;
uniform float m_Falloff;
uniform float m_Reach;
uniform float m_SkyDistance;

varying vec2 texCoord;

// How the sun's light reaches the air along the view: the share of the fog's in-scatter along the ray, weighed by how much
// each stretch of the air shows the eye, that is not in a shadow. The fog's colour and the sun's lobe are put on it by HeightFog.frag.
const int STEPS = 32;
// A 4x4 ordered pattern: the blur takes all sixteen offsets back together.
const float BAYER[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
// Toward the sun by this many of a cascade's texels, for a point of air to clear the map's own coarse depth.
const float BIAS = 2.0;

float lit(sampler2D map, mat4 lightViewProjection, float texel, float depths, vec3 point) {
    vec3 coord = (lightViewProjection * vec4(point, 1.0)).xyz * 0.5 + 0.5;
    if (coord.z >= 1.0 || coord.z <= 0.0 || any(lessThan(coord.xy, vec2(0.0))) || any(greaterThan(coord.xy, vec2(1.0)))) {
        return 1.0;
    }
    return coord.z - BIAS * texel / depths > texture2D(map, coord.xy).r ? 0.0 : 1.0;
}

// The cascade a point falls in, as SunShadows.frag chooses it; past the last there are no shadows.
float sunReaches(vec3 point) {
    float depth = dot(m_ViewDepth, vec4(point, 1.0));
    if (depth > m_Splits.w) {
        return 1.0;
    }
    if (depth < m_Splits.x) {
        return lit(m_ShadowMap0, m_LightViewProjectionMatrix0, m_CascadeTexels.x, m_CascadeDepths.x, point);
    }
    if (depth < m_Splits.y) {
        return lit(m_ShadowMap1, m_LightViewProjectionMatrix1, m_CascadeTexels.y, m_CascadeDepths.y, point);
    }
    if (depth < m_Splits.z) {
        return lit(m_ShadowMap2, m_LightViewProjectionMatrix2, m_CascadeTexels.z, m_CascadeDepths.z, point);
    }
    return lit(m_ShadowMap3, m_LightViewProjectionMatrix3, m_CascadeTexels.w, m_CascadeDepths.w, point);
}

void main() {
    float depth = fetchTextureSample(m_DepthTexture, texCoord, 0).r;
    vec4 at = m_ViewProjectionMatrixInverse * vec4(vec3(texCoord, depth) * 2.0 - 1.0, 1.0);
    vec3 ray = at.xyz / at.w - g_CameraPosition;
    float away = length(ray);
    vec3 direction = ray / away;
    // The sky is as far as HeightFog.frag takes it to be.
    float seen = depth >= 1.0 ? m_SkyDistance : away;
    float reach = min(seen, m_Reach);

    ivec2 cell = ivec2(gl_FragCoord.xy) & 3;
    float jitter = (BAYER[cell.y * 4 + cell.x] + 0.5) / 16.0;
    float stride = reach / float(STEPS);
    float optical = 0.0;
    float reached = 0.0;
    float weights = 0.0;
    for (int i = 0; i < STEPS; i++) {
        vec3 point = g_CameraPosition + direction * ((float(i) + jitter) * stride);
        float thickness = m_Density * exp(min(-m_Falloff * point.y, 80.0)) * stride;
        float weight = thickness * exp(-optical - 0.5 * thickness);
        optical += thickness;
        reached += weight * sunReaches(point);
        weights += weight;
    }
    gl_FragColor = vec4(weights > 1e-8 ? reached / weights : 1.0, seen, 0.0, 1.0);
}
