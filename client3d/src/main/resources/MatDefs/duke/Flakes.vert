#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform mat4 g_WorldViewProjectionMatrix;
uniform vec3 g_CameraPosition;
uniform float m_Size;
uniform float m_ViewHeight;
uniform float m_Least;
uniform float m_Most;

attribute vec3 inPosition;

void main() {
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
    // The reference's point scale: its size times the view's height over its distance from the eye.
    float away = max(distance(g_CameraPosition, inPosition), 0.001);
    gl_PointSize = clamp(m_Size * m_ViewHeight / away, m_Least, m_Most);
}
