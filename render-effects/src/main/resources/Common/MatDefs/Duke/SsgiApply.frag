#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"
#import "Common/ShaderLib/Ssgi.glsllib"

uniform COLORTEXTURE m_Texture;
uniform sampler2D m_Gi;
uniform sampler2D m_Depth;
uniform sampler2D m_Surface;
uniform sampler2D m_Albedo;
uniform mat4 m_ProjectionInverse;

varying vec2 texCoord;

// How far apart in depth, as a share of it, two pixels can be and be one surface.
const float SAME_SURFACE = 0.05;

void main() {
    vec4 colour = getColor(m_Texture, texCoord);
    vec3 albedo = texture2D(m_Albedo, texCoord).rgb;
    float depth = texture2D(m_Depth, texCoord).r;
    vec3 gi = vec3(0.0);
    if (depth < 1.0 && dot(albedo, albedo) > 0.0) {
        float z = ssgiDepth(m_ProjectionInverse, depth);
        vec3 normal = texture2D(m_Surface, texCoord).xyz;
        ivec2 size = textureSize(m_Gi, 0);
        vec2 at = texCoord * vec2(size) - 0.5;
        ivec2 base = ivec2(floor(at));
        vec2 f = at - vec2(base);
        // The four half-resolution pixels round this one, by nearness and by being the same surface, the normal too where it can be had.
        vec3 strict = vec3(0.0);
        float strictTotal = 0.0;
        vec3 loose = vec3(0.0);
        float looseTotal = 0.0;
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 2; i++) {
                ivec2 tap = clamp(base + ivec2(i, j), ivec2(0), size - 1);
                vec4 g = texelFetch(m_Gi, tap, 0);
                if (g.a <= 0.0) {
                    continue;
                }
                float weight = (i == 0 ? 1.0 - f.x : f.x) * (j == 0 ? 1.0 - f.y : f.y)
                        * max(0.0, 1.0 - abs(g.a - z) / (SAME_SURFACE * z));
                float facing = pow(max(dot(texelFetch(m_Surface, tap * 2, 0).xyz, normal), 0.0), 8.0);
                strict += g.rgb * weight * facing;
                strictTotal += weight * facing;
                loose += g.rgb * weight;
                looseTotal += weight;
            }
        }
        if (strictTotal > 0.05) {
            gi = strict / strictTotal;
        } else if (looseTotal > 1e-4) {
            gi = loose / looseTotal;
        }
    }
    #ifdef ONLY_GI
        gl_FragColor = vec4(albedo * gi, 1.0);
    #else
        gl_FragColor = vec4(colour.rgb + albedo * gi, colour.a);
    #endif
}
