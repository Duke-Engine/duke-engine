#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Water.glsllib"

// What SceneBuffers.frag writes, for a water surface: the waves' normal, a low roughness, the 2% a clear dielectric sends back
// head on, and no diffuse colour (the flag 0.5 in the albedo's alpha says it is water: SceneBuffers#albedo).
#if __VERSION__ >= 130
  out vec4 outFragData[4];
  #define OUT_SURFACE outFragData[0]
  #define OUT_MOTION outFragData[1]
  #define OUT_REFLECT outFragData[2]
  #define OUT_ALBEDO outFragData[3]
#else
  #define OUT_SURFACE gl_FragData[0]
  #define OUT_MOTION gl_FragData[1]
  #define OUT_REFLECT gl_FragData[2]
  #define OUT_ALBEDO gl_FragData[3]
#endif

uniform mat4 g_ViewMatrix;
uniform vec3 g_CameraPosition;
uniform float m_Time;
uniform float m_Roughness;

varying vec3 wPosition;
varying vec3 wNormal;
varying vec4 vClip;
varying vec4 vPreviousClip;

void main() {
    vec3 normal = waterNormal(normalize(wNormal), wPosition, normalize(g_CameraPosition - wPosition), m_Time);
    OUT_SURFACE = vec4(normalize(mat3(g_ViewMatrix) * normal), m_Roughness);
    OUT_REFLECT = vec4(vec3(0.02), 1.0);
    OUT_ALBEDO = vec4(0.0, 0.0, 0.0, 0.5);
    OUT_MOTION = vec4((vClip.xy / vClip.w - vPreviousClip.xy / max(vPreviousClip.w, 1e-4)) * 0.5, 0.0, 1.0);
}
