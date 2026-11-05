#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform mat4 g_ViewMatrix;
uniform mat4 g_ProjectionMatrix;
uniform mat4 g_WorldMatrix;

attribute vec3 inPosition;
attribute vec2 inTexCoord;

varying vec2 uv;

void main() {
    // Like the sky box: where the quad is seen is its direction from the origin, however far the camera has walked.
    vec3 direction = (g_WorldMatrix * vec4(inPosition, 1.0)).xyz;
    vec4 pos = g_ViewMatrix * vec4(direction, 0.0);
    pos.w = 1.0;
    gl_Position = g_ProjectionMatrix * pos;
    uv = inTexCoord;
}
