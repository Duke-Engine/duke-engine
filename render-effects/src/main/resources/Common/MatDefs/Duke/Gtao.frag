#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_Depth;
uniform sampler2D m_Surface;
uniform mat4 m_ProjectionInverse;
uniform float m_Radius;
uniform float m_Focal;
uniform int m_Frame;

varying vec2 texCoord;

// Ground truth ambient occlusion, Jimenez et al. 2016: for a few slices of the view through the pixel, the highest horizon
// each side finds, and the cosine-weighted share of the hemisphere in between, worked out in closed form.
const int SLICES = 2;
const int STEPS = 5;
const float PI = 3.14159265;
const float HALF_PI = 1.57079633;
// A radius wider than this many pixels is not walked to its end: far samples are cache misses for hardly any darkness.
const float MOST_PIXELS = 192.0;
// A radius of less than this many pixels sees nothing a pixel does not; the occlusion fades out up to it.
const float FADE_FROM = 1.5;
const float FADE_TO = 4.0;
// What stands in the outer share of the radius that this is counts less and less, to nothing at the radius.
const float FALLOFF = 0.6;

vec3 viewAt(vec2 uv, float depth) {
    vec4 at = m_ProjectionInverse * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return at.xyz / at.w;
}

void main() {
    float depth = texture2D(m_Depth, texCoord).r;
    vec3 normal = texture2D(m_Surface, texCoord).xyz;
    if (depth >= 1.0 || dot(normal, normal) < 0.5) {
        gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);
        return;
    }
    normal = normalize(normal);
    vec3 at = viewAt(texCoord, depth);
    vec3 toEye = normalize(-at);
    vec2 size = vec2(textureSize(m_Depth, 0));
    vec2 centre = texCoord * size;

    float radiusPixels = m_Radius * m_Focal / -at.z;
    float pixels = min(radiusPixels, MOST_PIXELS);
    float falloffMul = -1.0 / (m_Radius * FALLOFF);
    float falloffAdd = 1.0 / FALLOFF;

    // The nine pixels of a 3x3 patch hold nine of the half turn's eighteen directions (a slice more of each, half a turn
    // on) and nine offsets along the steps: the patch averaged in the next pass has all of them.
    ivec2 cell = ivec2(centre) % 3;
    int index = (cell.x + 3 * cell.y + m_Frame) % 9;
    float stepJitter = (float((index * 7) % 9) + 0.5) / 9.0;

    float visibility = 0.0;
    for (int slice = 0; slice < SLICES; slice++) {
        float phi = PI * (float((index * 4) % 9 + 9 * slice) + 0.5) / 18.0;
        vec2 direction = vec2(cos(phi), sin(phi));
        vec3 plane = vec3(direction, 0.0);
        vec3 across = plane - dot(plane, toEye) * toEye;
        vec3 axis = normalize(cross(plane, toEye));
        vec3 inPlane = normal - axis * dot(normal, axis);
        float planeLength = length(inPlane);
        float cosNormal = clamp(dot(inPlane, toEye) / max(planeLength, 1e-5), -1.0, 1.0);
        float n = (dot(across, inPlane) < 0.0 ? -1.0 : 1.0) * acos(cosNormal);

        // Each side starts as low as the tangent plane and rises as the samples find something.
        float lowSide = cos(n + HALF_PI);
        float lowOther = cos(n - HALF_PI);
        float horizonSide = lowSide;
        float horizonOther = lowOther;
        for (int step = 0; step < STEPS; step++) {
            float s = (float(step) + stepJitter) / float(STEPS);
            vec2 offset = direction * (s * s * pixels + 1.0);
            for (int side = 0; side < 2; side++) {
                vec2 pixel = floor(centre + (side == 0 ? offset : -offset)) + 0.5;
                if (any(lessThan(pixel, vec2(0.0))) || any(greaterThan(pixel, size))) {
                    continue;
                }
                vec2 uv = pixel / size;
                float d = texture2D(m_Depth, uv).r;
                if (d >= 1.0) {
                    continue;
                }
                // A pixel's centre is off the slice by up to half a pixel: what is measured is its place in the slice, so
                // that a flat floor finds its own tangent and nothing more.
                vec3 delta = viewAt(uv, d) - at;
                delta -= axis * dot(delta, axis);
                float dist = max(length(delta), 1e-5);
                float weight = clamp(dist * falloffMul + falloffAdd, 0.0, 1.0);
                if (side == 0) {
                    horizonSide = max(horizonSide, mix(lowSide, dot(delta, toEye) / dist, weight));
                } else {
                    horizonOther = max(horizonOther, mix(lowOther, dot(delta, toEye) / dist, weight));
                }
            }
        }
        float h0 = n + clamp(-acos(horizonOther) - n, -HALF_PI, HALF_PI);
        float h1 = n + clamp(acos(horizonSide) - n, -HALF_PI, HALF_PI);
        float sinN = sin(n);
        float arc0 = (cosNormal + 2.0 * h0 * sinN - cos(2.0 * h0 - n)) * 0.25;
        float arc1 = (cosNormal + 2.0 * h1 * sinN - cos(2.0 * h1 - n)) * 0.25;
        visibility += planeLength * (arc0 + arc1);
    }
    visibility = clamp(visibility / float(SLICES), 0.0, 1.0);
    visibility = mix(1.0, visibility, smoothstep(FADE_FROM, FADE_TO, radiusPixels));
    gl_FragColor = vec4(visibility, -at.z, 0.0, 1.0);
}
