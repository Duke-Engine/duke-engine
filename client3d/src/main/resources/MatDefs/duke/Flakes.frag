#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_Picture;
#ifdef COLOUR
uniform vec4 m_Colour;
#endif
#ifdef STRETCH
uniform float m_Stretch;
#endif

void main() {
    vec2 at = gl_PointCoord;
    #ifdef STRETCH
        // A streak: only the middle 1/stretch of the square across, the picture spread over it.
        float half = 0.5 / max(m_Stretch, 1.0);
        if (abs(at.x - 0.5) > half) {
            discard;
        }
        at.x = (at.x - 0.5) / (2.0 * half) + 0.5;
    #endif
    vec4 colour = texture2D(m_Picture, at);
    #ifdef COLOUR
        colour *= m_Colour;
    #endif
    gl_FragColor = colour;
}
