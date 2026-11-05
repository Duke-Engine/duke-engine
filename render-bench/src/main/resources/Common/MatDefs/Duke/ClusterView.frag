#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/LightClusters.glsllib"

uniform sampler2D m_Texture;
uniform sampler2D m_DepthTexture;
uniform sampler2D m_Grid;
uniform vec4 m_Scale;
uniform vec2 m_NearFar;
varying vec2 texCoord;

void main() {
    vec3 picture = texture2D(m_Texture, texCoord).rgb;
    float luma = dot(picture, vec3(0.2126, 0.7152, 0.0722));
    vec3 colour = vec3(luma / (1.0 + luma));
    float z = texture2D(m_DepthTexture, texCoord).r;
    if (z < 1.0) {
        float depth = m_NearFar.x * m_NearFar.y / (m_NearFar.y - z * (m_NearFar.y - m_NearFar.x));
        ivec3 cell = ivec3(ivec2(gl_FragCoord.xy * m_Scale.xy), int(log(depth) * m_Scale.z + m_Scale.w));
        cell = clamp(cell, ivec3(0), ivec3(CLUSTERS_X - 1, CLUSTERS_Y - 1, CLUSTERS_Z - 1));
        float count = texelFetch(m_Grid, clusterTexel(cell.x + CLUSTERS_X * (cell.y + CLUSTERS_Y * cell.z)), 0).g;
        // None dark, then blue at 1, green at 8, yellow at 16, red at 32 and over.
        float t = clamp(log2(count + 1.0) / log2(33.0), 0.0, 1.0);
        vec3 heat = count < 0.5 ? vec3(0.0) : clamp(vec3(2.0 * t - 0.6, 2.0 - abs(4.0 * t - 2.4), 1.6 - 2.5 * t), 0.0, 1.0);
        colour = mix(colour, heat, 0.7);
    }
    vec2 edge = fract(gl_FragCoord.xy * m_Scale.xy);
    if (min(edge.x, edge.y) < 0.012) {
        colour *= 0.4;
    }
    // The window turns linear to sRGB: undone, so the shot shows the colours as written.
    gl_FragColor = vec4(pow(colour, vec3(2.2)), 1.0);
}
