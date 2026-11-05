#import "Common/ShaderLib/GLSLCompat.glsllib"

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

uniform mat4 m_Reprojection;
varying vec2 ndc;

void main() {
    vec4 previous = m_Reprojection * vec4(ndc, 1.0, 1.0);
    OUT_SURFACE = vec4(0.0, 0.0, 0.0, 1.0);
    OUT_REFLECT = vec4(0.0);
    OUT_ALBEDO = vec4(0.0);
    OUT_MOTION = vec4((ndc - previous.xy / max(previous.w, 1e-4)) * 0.5, 0.0, 1.0);
}
