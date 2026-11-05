#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_Texture;
uniform sampler2D m_Previous;
uniform float m_BrighterRate;
uniform float m_DarkerRate;
varying vec2 texCoord;

// The eye is held between these mean log luminances (2^-4.5 .. 2^-0.5), which asks two stops either way of the exposure: a
// night is lifted no further than that and stays a night, a noon is pulled no further down.
const float LOG_MIN = -4.5;
const float LOG_MAX = -0.5;
// The luminance the eye takes for mid grey: a picture of this average gets the exposure as given.
const float KEY = 0.18;

// red: the log luminance the eye holds; green: what that asks of the exposure; blue: what the picture measures now.
void main() {
    ivec2 size = textureSize(m_Texture, 0);
    float sum = 0.0;
    float weights = 0.0;
    for (int y = 0; y < size.y; y++) {
        for (int x = 0; x < size.x; x++) {
            // The middle of the picture counts most, the edge a fifth as much.
            vec2 d = (vec2(x, y) + 0.5) / vec2(size) - 0.5;
            float weight = mix(0.2, 1.0, exp(-dot(d, d) * 8.0));
            sum += weight * texelFetch(m_Texture, ivec2(x, y), 0).r;
            weights += weight;
        }
    }
    float measured = clamp(sum / weights, LOG_MIN, LOG_MAX);
    float previous = texelFetch(m_Previous, ivec2(0), 0).r;
    float rate = measured > previous ? m_BrighterRate : m_DarkerRate;
    // At rate 1 the old value is not read, for it may be nothing yet.
    float eye = rate >= 1.0 ? measured : previous + (measured - previous) * rate;
    gl_FragColor = vec4(eye, KEY / exp2(eye), measured, 1.0);
}
