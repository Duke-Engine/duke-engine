#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"
#import "Common/MatDefs/Duke/DofCoc.glsllib"

uniform COLORTEXTURE m_Texture;

// Half the picture: the colour of the 2x2 pixels each of these stands for, and the disc they spread to, signed, in pixels of the full picture.
void main() {
    ivec2 full = textureSize(m_Depth, 0);
    ivec2 base = ivec2(gl_FragCoord.xy) * 2;
    vec3 sum = vec3(0.0);
    float weights = 0.0;
    float coc = 0.0;
    for (int i = 0; i < 4; i++) {
        ivec2 at = min(base + ivec2(i & 1, i >> 1), full - 1);
        vec3 colour = getColor(m_Texture, (vec2(at) + 0.5) / vec2(full)).rgb;
        // Karis: one very bright pixel does not light the whole of its disc.
        float weight = 1.0 / (1.0 + dot(colour, vec3(0.2126, 0.7152, 0.0722)));
        sum += colour * weight;
        weights += weight;
        coc += confusion(texelFetch(m_Depth, at, 0).r) * 0.25;
    }
    gl_FragColor = vec4(sum / weights, coc);
}
