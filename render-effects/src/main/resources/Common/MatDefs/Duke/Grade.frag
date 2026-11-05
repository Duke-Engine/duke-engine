#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
#ifdef LUT
uniform sampler3D m_Lut;
uniform float m_LutSize;
#endif
#ifdef VIGNETTE
uniform float m_Vignette;
#endif

varying vec2 texCoord;

// The sRGB curve, which the grade's cube is read in: the picture here is linear light, and the screen encodes it by the same curve on the way out.
vec3 encode(vec3 c) {
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

vec3 decode(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

void main() {
    vec4 colour = getColor(m_Texture, texCoord);
    vec3 rgb = colour.rgb;
#ifdef VIGNETTE
    // Distance from the middle, 1 at the corners whatever the shape of the picture (the quad's coordinate runs w:h over the pixels).
    float aspect = abs(dFdy(texCoord.y)) / abs(dFdx(texCoord.x));
    float r = length((texCoord - 0.5) * vec2(aspect, 1.0)) / length(vec2(aspect, 1.0) * 0.5);
    float edge = smoothstep(0.2, 1.0, r);
    rgb *= 1.0 - m_Vignette * edge * edge;
#endif
#ifdef LUT
    // The middle of a step, not its edge, is where the cube holds its colour.
    vec3 seen = clamp(encode(clamp(rgb, 0.0, 1.0)), 0.0, 1.0);
    rgb = decode(texture3D(m_Lut, seen * ((m_LutSize - 1.0) / m_LutSize) + 0.5 / m_LutSize).rgb);
#endif
    gl_FragColor = vec4(rgb, colour.a);
}
