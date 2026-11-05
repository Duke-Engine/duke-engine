#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
uniform vec2 m_Texel;
varying vec2 texCoord;

#ifdef KARIS
// The scene is read unfiltered, so the 2x2 block a bilinear tap would give is summed by hand. The tap sits on a corner
// between four texels because the target is half the picture.
vec3 block(vec2 uv) {
    vec2 h = 0.5 * m_Texel;
    return 0.25 * (getColor(m_Texture, uv + vec2(-h.x, -h.y)).rgb + getColor(m_Texture, uv + vec2(h.x, -h.y)).rgb
            + getColor(m_Texture, uv + vec2(-h.x, h.y)).rgb + getColor(m_Texture, uv + vec2(h.x, h.y)).rgb);
}

// A lone hot pixel would flicker through every level; weighed by 1 / (1 + luma) it counts for no more than its neighbours.
float karis(vec3 c) {
    return 1.0 / (1.0 + dot(c, vec3(0.2126, 0.7152, 0.0722)));
}
#else
vec3 block(vec2 uv) {
    return texture2D(m_Texture, uv).rgb;
}
#endif

// Jimenez, Next Generation Post Processing in Call of Duty: Advanced Warfare (2014): thirteen taps in five overlapping
// blocks, 1 in all, so that moving a bright thing a pixel does not make it shimmer.
void main() {
    vec2 t = m_Texel;
    vec3 a = block(texCoord + vec2(-2.0, 2.0) * t);
    vec3 b = block(texCoord + vec2(0.0, 2.0) * t);
    vec3 c = block(texCoord + vec2(2.0, 2.0) * t);
    vec3 d = block(texCoord + vec2(-2.0, 0.0) * t);
    vec3 e = block(texCoord);
    vec3 f = block(texCoord + vec2(2.0, 0.0) * t);
    vec3 g = block(texCoord + vec2(-2.0, -2.0) * t);
    vec3 h = block(texCoord + vec2(0.0, -2.0) * t);
    vec3 i = block(texCoord + vec2(2.0, -2.0) * t);
    vec3 j = block(texCoord + vec2(-1.0, 1.0) * t);
    vec3 k = block(texCoord + vec2(1.0, 1.0) * t);
    vec3 l = block(texCoord + vec2(-1.0, -1.0) * t);
    vec3 m = block(texCoord + vec2(1.0, -1.0) * t);
    #ifdef KARIS
        vec3 g0 = (a + b + d + e) * 0.25;
        vec3 g1 = (b + c + e + f) * 0.25;
        vec3 g2 = (d + e + g + h) * 0.25;
        vec3 g3 = (e + f + h + i) * 0.25;
        vec3 g4 = (j + k + l + m) * 0.25;
        float w0 = 0.125 * karis(g0);
        float w1 = 0.125 * karis(g1);
        float w2 = 0.125 * karis(g2);
        float w3 = 0.125 * karis(g3);
        float w4 = 0.5 * karis(g4);
        gl_FragColor = vec4((g0 * w0 + g1 * w1 + g2 * w2 + g3 * w3 + g4 * w4) / (w0 + w1 + w2 + w3 + w4), 1.0);
    #else
        gl_FragColor = vec4(e * 0.125 + (a + c + g + i) * 0.03125 + (b + d + f + h) * 0.0625 + (j + k + l + m) * 0.125, 1.0);
    #endif
}
