#import "Common/ShaderLib/GLSLCompat.glsllib"

// GLRenderer binds each outFragData[i] to colour target i; the compat library's outFragColor stays unused.
#if __VERSION__ >= 130
  out vec4 outFragData[2];
  #define OUT_SURFACE outFragData[0]
  #define OUT_MOTION outFragData[1]
#else
  #define OUT_SURFACE gl_FragData[0]
  #define OUT_MOTION gl_FragData[1]
#endif

uniform float m_Roughness;
#ifdef DISCARD_ALPHA
  uniform float m_AlphaDiscardThreshold;
  varying float vAlpha;
  #ifdef BASECOLORMAP
    uniform sampler2D m_BaseColorMap;
  #endif
#endif
#ifdef NORMALMAP
  uniform sampler2D m_NormalMap;
  varying vec4 vTangent;
  #ifdef NORMALSCALE
    uniform float m_NormalScale;
  #endif
#endif
#ifdef USE_PACKED_MR
  uniform sampler2D m_MetallicRoughnessMap;
#elif defined(ROUGHNESSMAP)
  uniform sampler2D m_RoughnessMap;
#endif
#ifdef SPECGLOSSPIPELINE
  uniform float m_Glossiness;
  #ifdef USE_PACKED_SG
    uniform sampler2D m_SpecularGlossinessMap;
  #elif defined(GLOSSINESSMAP)
    uniform sampler2D m_GlossinessMap;
  #endif
#endif

varying vec2 texCoord;
varying vec3 vNormal;
varying vec4 vClip;
varying vec4 vPreviousClip;

// Reads a surface as PBRLighting.frag does. shortcut: a parallax map's offset is left out, follow it when a material has one.
void main() {
    #ifdef DISCARD_ALPHA
        float alpha = vAlpha;
        #ifdef BASECOLORMAP
            alpha *= texture2D(m_BaseColorMap, texCoord).a;
        #endif
        if (alpha < m_AlphaDiscardThreshold) {
            discard;
        }
    #endif

    vec3 normal = normalize(vNormal);
    #ifdef NORMALMAP
        vec3 tangent = normalize(vTangent.xyz);
        mat3 tbn = mat3(tangent, vTangent.w * cross(normal, tangent), normal);
        vec3 mapped = texture2D(m_NormalMap, texCoord).xyz * vec3(2.0, NORMAL_TYPE * 2.0, 2.0) - vec3(1.0, NORMAL_TYPE * 1.0, 1.0);
        #ifdef NORMALSCALE
            mapped *= vec3(m_NormalScale, m_NormalScale, 1.0);
        #endif
        normal = normalize(tbn * mapped);
    #endif
    if (!gl_FrontFacing) {
        normal = -normal;
    }

    float roughness = max(m_Roughness, 1e-4);
    #ifdef USE_PACKED_MR
        roughness *= texture2D(m_MetallicRoughnessMap, texCoord).g;
    #elif defined(ROUGHNESSMAP)
        roughness *= texture2D(m_RoughnessMap, texCoord).r;
    #endif
    #ifdef SPECGLOSSPIPELINE
        #ifdef USE_PACKED_SG
            roughness = 1.0 - texture2D(m_SpecularGlossinessMap, texCoord).a * m_Glossiness;
        #elif defined(GLOSSINESSMAP)
            roughness = 1.0 - texture2D(m_GlossinessMap, texCoord).r * m_Glossiness;
        #else
            roughness = 1.0 - m_Glossiness;
        #endif
    #endif

    OUT_SURFACE = vec4(normal, roughness);
    OUT_MOTION = vec4((vClip.xy / vClip.w - vPreviousClip.xy / max(vPreviousClip.w, 1e-4)) * 0.5, 0.0, 1.0);
}
