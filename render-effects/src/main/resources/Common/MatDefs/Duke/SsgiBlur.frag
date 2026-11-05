#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_In;
uniform sampler2D m_Surface;

varying vec2 texCoord;

// How far apart in depth, as a share of it, two pixels can be and be one surface.
const float SAME_SURFACE = 0.05;

void main() {
    ivec2 cell = ivec2(gl_FragCoord.xy);
    vec4 centre = texelFetch(m_In, cell, 0);
    if (centre.a <= 0.0) {
        gl_FragColor = vec4(0.0);
        return;
    }
    ivec2 limit = textureSize(m_In, 0) - 1;
    vec3 normal = texelFetch(m_Surface, cell * 2, 0).xyz;
    vec3 sum = vec3(0.0);
    float total = 0.0;
    // 5x5, weighed by nearness, by depth and by the normal: light does not run over an edge.
    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            ivec2 at = clamp(cell + ivec2(x, y), ivec2(0), limit);
            vec4 tap = texelFetch(m_In, at, 0);
            if (tap.a <= 0.0) {
                continue;
            }
            float weight = exp(-0.22 * float(x * x + y * y))
                    * max(0.0, 1.0 - abs(tap.a - centre.a) / (SAME_SURFACE * centre.a))
                    * pow(max(dot(texelFetch(m_Surface, at * 2, 0).xyz, normal), 0.0), 8.0);
            sum += tap.rgb * weight;
            total += weight;
        }
    }
    gl_FragColor = vec4(total > 1e-4 ? sum / total : centre.rgb, centre.a);
}
