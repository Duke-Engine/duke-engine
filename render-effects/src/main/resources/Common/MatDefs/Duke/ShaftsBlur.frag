#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_Shafts;

varying vec2 texCoord;

// How far apart in distance, as a share of it, two pixels can be and still count as one surface. A floor seen at a grazing
// angle changes by a tenth between neighbours; an edge by far more.
const float SAME_SURFACE = 0.2;

// The first pass jittered its steps by a 4x4 pattern: these 16 pixels hold all of it, taken back together over one surface.
void main() {
    ivec2 size = textureSize(m_Shafts, 0);
    ivec2 centre = ivec2(gl_FragCoord.xy);
    vec2 here = texelFetch(m_Shafts, centre, 0).rg;
    float sum = 0.0;
    float total = 0.0;
    for (int y = -2; y <= 1; y++) {
        for (int x = -2; x <= 1; x++) {
            vec2 tap = texelFetch(m_Shafts, clamp(centre + ivec2(x, y), ivec2(0), size - 1), 0).rg;
            float weight = max(0.0, 1.0 - abs(tap.g - here.g) / (SAME_SURFACE * here.g));
            sum += tap.r * weight;
            total += weight;
        }
    }
    gl_FragColor = vec4(sum / total, here.g, 0.0, 1.0);
}
