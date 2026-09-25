#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldMatrix;
uniform vec2 m_FogSize;

attribute vec3 inPosition;
attribute vec4 inColor;
attribute vec2 inTexCoord;

varying vec2 fogCoord;
varying vec2 place;
varying vec2 along;
varying float shore;

void main() {
    vec4 world = g_WorldMatrix * vec4(inPosition, 1.0);
    fogCoord = vec2(world.x / m_FogSize.x, 1.0 - world.z / m_FogSize.y);
    // Where it lies on the map, for standing water; across and down a river, for running.
    place = world.xz;
    along = inTexCoord;
    // How much of it the shore leaves: none where the ground meets the surface, all of it past the fade depth.
    shore = inColor.a;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
