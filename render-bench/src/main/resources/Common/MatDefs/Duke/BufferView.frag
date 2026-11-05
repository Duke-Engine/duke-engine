#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_Buffer;
uniform vec2 m_NearFar;
varying vec2 texCoord;

void main() {
    vec4 value = texture2D(m_Buffer, texCoord);
    vec3 colour;
    #if MODE == 0
        colour = value.xyz * 0.5 + 0.5;
    #elif MODE == 1
        // The square root of the share of the way to the far plane: most of a scene stands in its first tenth.
        float z = m_NearFar.x * m_NearFar.y / (m_NearFar.y - value.r * (m_NearFar.y - m_NearFar.x));
        colour = vec3(sqrt(z / m_NearFar.y));
    #elif MODE == 2
        colour = value.aaa;
    #else
        // Grey stands still; a thousandth of the screen a frame is half way to full colour.
        colour = vec3(0.5 + value.rg * 500.0, 0.5);
    #endif
    // The window turns linear to sRGB: undone, so a pixel of the shot reads as the buffer does.
    gl_FragColor = vec4(pow(clamp(colour, 0.0, 1.0), vec3(2.2)), 1.0);
}
