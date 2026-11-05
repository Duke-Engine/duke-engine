#import "Common/ShaderLib/GLSLCompat.glsllib"
// The world's matrices, or each instance's where a piece is drawn instanced (ground cover, E100).
#import "Common/ShaderLib/Instancing.glsllib"

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

#if defined(VERTEX_ALPHA) || defined(VERTEX_LIGHT) || defined(VERTEX_SHADE)
attribute vec4 inColor;
#endif
#ifdef VERTEX_SHADE
varying vec3 vertexShade;
#endif
#ifdef VERTEX_ALPHA
varying float vertexAlpha;
#endif
#ifdef VERTEX_LIGHT
varying vec3 vertexLight;
#endif

#ifdef BLENDED
attribute vec4 inTexCoord2;
attribute vec4 inTexCoord3;
varying vec4 blend0;
varying vec4 blend1;
#endif

void main() {
    vec4 world = TransformWorld(vec4(inPosition, 1.0));

    // Where this vertex stands on the map, and only that: x and z, never y.
    // A wall is one place on the map from its foot to the top of it, and fog that
    // took the height into account would darken it halfway up -- which is what a
    // flat sheet hung over the world does, and why this is a projection instead.
    // v runs the other way because the fog image's first row is the map's far edge.
    fogCoord = vec2(world.x / m_FogSize.x, 1.0 - world.z / m_FogSize.y);

    #ifdef INSTANCING
    worldNormal = normalize(TransformWorldNormal(inNormal));
    #else
    worldNormal = normalize(mat3_sub(g_WorldMatrix) * inNormal);
    #endif
    worldPos = world.xyz;

    #ifdef HAS_COLORMAP
    texCoord = inTexCoord;
    #endif

    #ifdef VERTEX_ALPHA
    vertexAlpha = inColor.a;
    #endif
    #ifdef VERTEX_LIGHT
    vertexLight = inColor.rgb;
    #endif
    #ifdef VERTEX_SHADE
    vertexShade = inColor.rgb;
    #endif

    #ifdef BLENDED
    blend0 = inTexCoord2;
    blend1 = inTexCoord3;
    #endif

    gl_Position = TransformWorldViewProjection(vec4(inPosition, 1.0));
}
