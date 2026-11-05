#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldMatrix;

attribute vec3 inPosition;
attribute vec3 inNormal;

varying vec3 wPosition;
varying vec3 wNormal;
#ifdef BUFFERS
  uniform mat4 m_PreviousViewProjection;
  uniform mat4 m_PreviousWorldMatrix;
  varying vec4 vClip;
  varying vec4 vPreviousClip;
#endif

void main() {
    vec4 position = vec4(inPosition, 1.0);
    gl_Position = g_WorldViewProjectionMatrix * position;
    wPosition = (g_WorldMatrix * position).xyz;
    // shortcut: a uniform scale only, a squashed mesh would lean its normal wrong.
    wNormal = normalize(mat3(g_WorldMatrix) * inNormal);
    #ifdef BUFFERS
        vClip = gl_Position;
        vPreviousClip = m_PreviousViewProjection * m_PreviousWorldMatrix * position;
    #endif
}
