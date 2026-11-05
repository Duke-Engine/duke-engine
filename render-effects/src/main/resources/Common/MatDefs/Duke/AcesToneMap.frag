#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
uniform float m_Exposure;
#ifdef AUTO_EXPOSURE
uniform sampler2D m_Adapted;
#endif
varying vec2 texCoord;

// Narkowicz's fit of the ACES film curve: https://knarkowicz.wordpress.com/2016/01/06/aces-filmic-tone-mapping-curve/
vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec4 colour = getColor(m_Texture, texCoord);
    float exposure = m_Exposure;
    #ifdef AUTO_EXPOSURE
        exposure *= texture2D(m_Adapted, vec2(0.5)).g;
    #endif
    gl_FragColor = vec4(aces(colour.rgb * exposure), colour.a);
}
