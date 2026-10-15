#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Instancing.glsllib"
#import "Common/ShaderLib/Skinning.glsllib"

attribute vec3 inPosition;

void main() {
    vec4 position = vec4(inPosition, 1.0);
    #ifdef NUM_BONES
        Skinning_Compute(position);
    #endif
    gl_Position = TransformWorldViewProjection(position);
}
