#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
varying vec2 texCoord;

const int TAPS = 6;
// The log of a pixel's luminance is kept between these (2^-10 .. 2^5): a lamp or a sun disc is not worth more of the
// average than that, and nor is black.
const float LOG_MIN = -10.0;
const float LOG_MAX = 5.0;

// The mean log luminance over the footprint of this texel of a small target, in a grid of taps over the whole picture.
void main() {
    vec2 cell = abs(vec2(dFdx(texCoord.x), dFdy(texCoord.y)));
    vec2 origin = texCoord - 0.5 * cell;
    float sum = 0.0;
    for (int y = 0; y < TAPS; y++) {
        for (int x = 0; x < TAPS; x++) {
            vec2 uv = origin + (vec2(x, y) + 0.5) / float(TAPS) * cell;
            float luma = dot(getColor(m_Texture, uv).rgb, vec3(0.2126, 0.7152, 0.0722));
            sum += clamp(log2(max(luma, 1e-6)), LOG_MIN, LOG_MAX);
        }
    }
    gl_FragColor = vec4(sum / float(TAPS * TAPS), 0.0, 0.0, 1.0);
}
