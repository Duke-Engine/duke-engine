#import "Common/ShaderLib/GLSLCompat.glsllib"

#if defined(HAS_COLORMAP) && defined(ALPHA_DISCARD)
uniform sampler2D m_ColorMap;
uniform float m_AlphaDiscard;
varying vec2 texCoord;
#endif

void main() {
    #if defined(HAS_COLORMAP) && defined(ALPHA_DISCARD)
    if (texture2D(m_ColorMap, texCoord).a < m_AlphaDiscard) {
        discard;
    }
    #endif
    gl_FragColor = vec4(1.0);
}
