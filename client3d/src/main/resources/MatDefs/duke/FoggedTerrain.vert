#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldMatrix;
uniform vec2 m_FogSize;

attribute vec3 inPosition;
attribute vec3 inNormal;

varying vec2 fogCoord;
varying vec3 worldNormal;
// Where this fragment stands, in full. The fog only needs x and z; a torch
// carried past a wall needs all three, because how far it is from the stone is
// the whole of how brightly the stone answers.
varying vec3 worldPos;

#ifdef HAS_COLORMAP
attribute vec2 inTexCoord;
varying vec2 texCoord;
#endif

void main() {
    vec4 world = g_WorldMatrix * vec4(inPosition, 1.0);

    // Where this vertex stands on the map, and only that: x and z, never y.
    // A wall is one place on the map from its foot to the top of it, and fog that
    // took the height into account would darken it halfway up -- which is what a
    // flat sheet hung over the world does, and why this is a projection instead.
    // v runs the other way because the fog image's first row is the map's far edge.
    fogCoord = vec2(world.x / m_FogSize.x, 1.0 - world.z / m_FogSize.y);

    worldNormal = normalize(mat3_sub(g_WorldMatrix) * inNormal);
    worldPos = world.xyz;

    #ifdef HAS_COLORMAP
    texCoord = inTexCoord;
    #endif

    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
