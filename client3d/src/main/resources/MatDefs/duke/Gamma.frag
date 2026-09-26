#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_Frame;
uniform sampler2D m_Ramp;

varying vec2 texCoord;

// A channel's value, one of 256, as the ramp shows it: read at the middle of its texel.
float shown(float value) {
    return texture2D(m_Ramp, vec2((floor(value * 255.0 + 0.5) + 0.5) / 256.0, 0.5)).r;
}

void main() {
    vec4 drawn = texture2D(m_Frame, texCoord);
    gl_FragColor = vec4(shown(drawn.r), shown(drawn.g), shown(drawn.b), 1.0);
}
