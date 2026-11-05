#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_Texture;
uniform int m_Block;
#ifdef VELOCITY
uniform vec2 m_Pixels;
#endif

// The largest value in a block x block square of the picture, one per output pixel. A disc: the widest of the discs
// (alpha of the prefiltered picture); a velocity: the longest one, kept as it is.
void main() {
    ivec2 size = textureSize(m_Texture, 0);
    ivec2 from = ivec2(gl_FragCoord.xy) * m_Block;
#ifdef VELOCITY
    vec2 best = vec2(0.0);
    float longest = 0.0;
    for (int y = 0; y < m_Block; y++) {
        for (int x = 0; x < m_Block; x++) {
            vec2 v = texelFetch(m_Texture, min(from + ivec2(x, y), size - 1), 0).rg;
            vec2 pixels = v * m_Pixels;
            float length2 = dot(pixels, pixels);
            if (length2 > longest) {
                longest = length2;
                best = v;
            }
        }
    }
    gl_FragColor = vec4(best, 0.0, 1.0);
#else
    float widest = 0.0;
    for (int y = 0; y < m_Block; y++) {
        for (int x = 0; x < m_Block; x++) {
            widest = max(widest, abs(texelFetch(m_Texture, min(from + ivec2(x, y), size - 1), 0).a));
        }
    }
    gl_FragColor = vec4(widest, 0.0, 0.0, 1.0);
#endif
}
