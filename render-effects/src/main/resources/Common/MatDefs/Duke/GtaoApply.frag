#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
uniform sampler2D m_Occlusion;

varying vec2 texCoord;

// The integral is a lower bound of what a crease keeps from the sky; a power over it shows it as the eye expects.
const float STRENGTH = 1.6;
// How far apart in depth, as a share of it, two pixels can be and still count as one surface.
const float SAME_SURFACE = 0.03;

// Jimenez 2016: light that bounces between the walls of a crease comes back, more of it the lighter they are.
vec3 multiBounce(float visibility, vec3 albedo) {
    vec3 a = 2.0404 * albedo - 0.3324;
    vec3 b = -4.7951 * albedo + 0.6417;
    vec3 c = 2.7552 * albedo + 0.6903;
    return max(vec3(visibility), ((visibility * a + b) * visibility + c) * visibility);
}

void main() {
    vec4 colour = getColor(m_Texture, texCoord);
    vec2 centre = texture2D(m_Occlusion, texCoord).rg;
    if (centre.g <= 0.0) {
        #ifdef ONLY_OCCLUSION
            gl_FragColor = vec4(1.0);
        #else
            gl_FragColor = colour;
        #endif
        return;
    }
    // The first pass left nine different directions in every 3x3 patch: this takes them back together, over what is
    // the same surface and only that.
    vec2 texel = 1.0 / vec2(textureSize(m_Occlusion, 0));
    float sum = 0.0;
    float total = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 tap = texture2D(m_Occlusion, texCoord + vec2(x, y) * texel).rg;
            float weight = tap.g > 0.0 ? max(0.0, 1.0 - abs(tap.g - centre.g) / (SAME_SURFACE * centre.g)) : 0.0;
            sum += tap.r * weight;
            total += weight;
        }
    }
    float visibility = pow(sum / total, STRENGTH);
    #ifdef ONLY_OCCLUSION
        // The window turns linear to sRGB: undone, so a pixel of the shot reads as the visibility does.
        gl_FragColor = vec4(vec3(pow(visibility, 2.2)), 1.0);
    #else
        // shortcut: the lit colour squeezed into 0..1 stands for the surface's own, SceneBuffers holds no albedo; give
        // it a channel when something else needs the albedo too.
        vec3 albedo = colour.rgb / (colour.rgb + 1.0);
        gl_FragColor = vec4(colour.rgb * multiBounce(visibility, albedo), colour.a);
    #endif
}
