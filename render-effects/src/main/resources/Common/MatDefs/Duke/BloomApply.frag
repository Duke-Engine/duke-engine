#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
uniform sampler2D m_Bloom;
uniform float m_Strength;
varying vec2 texCoord;

void main() {
    vec4 colour = getColor(m_Texture, texCoord);
    vec3 bloom = texture2D(m_Bloom, texCoord).rgb;
    #ifdef ONLY_BLOOM
        // Squeezed into 0..1, then the window's sRGB undone, so a pixel of the shot reads as the squeeze does.
        gl_FragColor = vec4(pow(bloom / (bloom + 1.0), vec3(2.2)), 1.0);
    #else
        // No threshold: every pixel gives a little of the light around it, and a flat field comes out as it went in.
        gl_FragColor = vec4(mix(colour.rgb, bloom, m_Strength), colour.a);
    #endif
}
