#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec3 m_Colour;
uniform float m_Radius;

varying vec2 uv;

void main() {
    // Cut out rather than blended: the disc is a few pixels across, and the bloom over it softens the edge anyway.
    if (length(uv) > m_Radius) {
        discard;
    }
    gl_FragColor = vec4(m_Colour, 1.0);
}
