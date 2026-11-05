#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
uniform float m_Strength;

varying vec2 texCoord;

// The picture is linear light; the eye's steps are nearer its square root, which is where the sharpening is done.
vec3 seen(vec2 at) {
    return sqrt(max(getColor(m_Texture, at).rgb, 0.0));
}

// After AMD's contrast adaptive sharpening: the five taps of a cross, the push weaker the nearer a channel is to black
// or white, so that nothing overshoots, and an edge that already has bite is left alone.
void main() {
    // The step to the next texel, from how fast the quad's coordinate runs over the pixels.
    vec2 texel = vec2(abs(dFdx(texCoord.x)), abs(dFdy(texCoord.y)));
    vec4 colour = getColor(m_Texture, texCoord);
    vec3 e = sqrt(max(colour.rgb, 0.0));
    vec3 b = seen(texCoord + vec2(0.0, -texel.y));
    vec3 d = seen(texCoord + vec2(-texel.x, 0.0));
    vec3 f = seen(texCoord + vec2(texel.x, 0.0));
    vec3 h = seen(texCoord + vec2(0.0, texel.y));
    vec3 low = min(min(min(d, e), min(f, b)), h);
    vec3 high = max(max(max(d, e), max(f, b)), h);
    vec3 room = sqrt(clamp(min(low, 1.0 - high) / max(high, 1e-4), 0.0, 1.0));
    vec3 weight = -room / mix(8.0, 5.0, m_Strength);
    vec3 sharpened = clamp(((b + d + f + h) * weight + e) / (1.0 + 4.0 * weight), 0.0, 1.0);
    gl_FragColor = vec4(sharpened * sharpened, colour.a);
}
