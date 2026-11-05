#import "Common/ShaderLib/GLSLCompat.glsllib"

attribute vec3 inPosition;
varying vec2 ndc;

void main() {
    ndc = inPosition.xy * 2.0 - 1.0;
    gl_Position = vec4(ndc, 1.0, 1.0);
}
