#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_Lower;
uniform sampler2D m_Same;
uniform vec2 m_Texel;
varying vec2 texCoord;

// How much of the wider level goes in. A lerp, not a sum, so that the levels together weigh 1 and a flat field stays as it is.
const float SPREAD = 0.6;

void main() {
    vec2 t = m_Texel;
    // 3x3 tent of bilinear taps, one wider texel in radius.
    vec3 tent = texture2D(m_Lower, texCoord).rgb * 4.0
            + (texture2D(m_Lower, texCoord + vec2(0.0, t.y)).rgb + texture2D(m_Lower, texCoord - vec2(0.0, t.y)).rgb
            + texture2D(m_Lower, texCoord + vec2(t.x, 0.0)).rgb + texture2D(m_Lower, texCoord - vec2(t.x, 0.0)).rgb) * 2.0
            + texture2D(m_Lower, texCoord + t).rgb + texture2D(m_Lower, texCoord - t).rgb
            + texture2D(m_Lower, texCoord + vec2(t.x, -t.y)).rgb + texture2D(m_Lower, texCoord + vec2(-t.x, t.y)).rgb;
    gl_FragColor = vec4(mix(texture2D(m_Same, texCoord).rgb, tent / 16.0, SPREAD), 1.0);
}
