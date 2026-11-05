#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Instancing.glsllib"

attribute vec3 inPosition;
#if defined(HAS_COLORMAP) && defined(ALPHA_DISCARD)
attribute vec2 inTexCoord;
varying vec2 texCoord;
#endif

void main() {
    #if defined(HAS_COLORMAP) && defined(ALPHA_DISCARD)
    texCoord = inTexCoord;
    #endif
    gl_Position = TransformWorldViewProjection(vec4(inPosition, 1.0));
}
