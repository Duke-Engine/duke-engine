#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_Texture;
varying vec2 texCoord;

// The mean of each square block of the measured texels.
void main() {
    int block = int(float(textureSize(m_Texture, 0).x) * abs(dFdx(texCoord.x)) + 0.5);
    ivec2 base = ivec2(gl_FragCoord.xy) * block;
    float sum = 0.0;
    for (int y = 0; y < block; y++) {
        for (int x = 0; x < block; x++) {
            sum += texelFetch(m_Texture, base + ivec2(x, y), 0).r;
        }
    }
    gl_FragColor = vec4(sum / float(block * block), 0.0, 0.0, 1.0);
}
