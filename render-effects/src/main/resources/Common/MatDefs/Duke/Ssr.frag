#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
uniform sampler2D m_Depth;
uniform sampler2D m_Surface;
uniform sampler2D m_Reflectance;
uniform mat4 m_Projection;
uniform mat4 m_ProjectionInverse;
uniform mat4 m_ViewInverse;
uniform vec2 m_ProjectionZ;
uniform float m_Near;
uniform float m_Reach;
uniform int m_Frame;
#ifdef PROBE
    uniform samplerCube m_ProbeMap;
    uniform float m_ProbeMips;
#endif

varying vec2 texCoord;

const int STEPS = 48;
const int REFINE = 6;
// The least a step is, in pixels, however short the ray: a ray of a few pixels sees nothing a neighbour does not.
const float MIN_STRIDE = 2.0;
// A surface rougher than this has the screen's reflection faded out, to nothing at the second: the probe's blur is the better one.
const float ROUGH_FROM = 0.25;
const float ROUGH_TO = 0.55;
// A ray leaving toward the eye (view z up) finds only what is behind the camera: faded out between these.
const float EYE_FROM = 0.4;
const float EYE_TO = 0.85;
// How far from the picture's edge, as a share of it, the hit fades out; and the share of the reach where the ray's end does.
const float EDGE = 0.1;
const float END_FROM = 0.6;
// What a ray passes behind is a hit within a step's depth span, and this much of its depth over.
const float THICKNESS = 0.03;

// The depth buffer's value to the view space z (negative in front of the camera).
float viewZ(float depth) {
    return -m_ProjectionZ.y / (depth * 2.0 - 1.0 + m_ProjectionZ.x);
}

float sceneZ(ivec2 pixel) {
    float depth = texelFetch(m_Depth, pixel, 0).r;
    return depth >= 1.0 ? -1.0e9 : viewZ(depth);
}

// As PBR.glsllib's integrateBRDFApprox: the share of an environment a surface of this F0 sends back, split-sum.
vec3 specularBrdf(vec3 specular, float roughness, float NoV) {
    const vec4 c0 = vec4(-1.0, -0.0275, -0.572, 0.022);
    const vec4 c1 = vec4(1.0, 0.0425, 1.04, -0.04);
    vec4 r = roughness * c0 + c1;
    float a004 = min(r.x * r.x, exp2(-9.28 * NoV)) * r.x + r.y;
    vec2 AB = vec2(-1.04, 1.04) * a004 + r.zw;
    return specular * AB.x + AB.y;
}

// Marches the ray (McGuire and Mara 2014: in pixels, with 1/w interpolated, so a step is as long on the screen as the
// next) until it passes behind what the depth holds, then halves the last step. False where it leaves the picture or
// finds nothing.
bool trace(vec3 from, vec3 direction, float jitter, out vec2 hitPixel, out vec3 hitView) {
    vec2 size = vec2(textureSize(m_Depth, 0));
    float reach = m_Reach;
    if (from.z + direction.z * reach > -m_Near) {
        reach = (-m_Near - from.z) / direction.z;
    }
    vec3 to = from + direction * reach;
    vec4 h0 = m_Projection * vec4(from, 1.0);
    vec4 h1 = m_Projection * vec4(to, 1.0);
    float k0 = 1.0 / h0.w;
    float k1 = 1.0 / h1.w;
    vec3 q0 = from * k0;
    vec3 q1 = to * k1;
    vec2 s0 = (h0.xy * k0 * 0.5 + 0.5) * size;
    vec2 s1 = (h1.xy * k1 * 0.5 + 0.5) * size;
    vec2 d = s1 - s0;
    // The part of the segment that is on the picture.
    float last = 1.0;
    if (s1.x > size.x - 1.0) last = min(last, (size.x - 1.0 - s0.x) / d.x);
    else if (s1.x < 1.0) last = min(last, (1.0 - s0.x) / d.x);
    if (s1.y > size.y - 1.0) last = min(last, (size.y - 1.0 - s0.y) / d.y);
    else if (s1.y < 1.0) last = min(last, (1.0 - s0.y) / d.y);
    float pixels = max(abs(d.x), abs(d.y)) * last;
    if (pixels < MIN_STRIDE) {
        return false;
    }
    float dt = max(MIN_STRIDE, pixels / float(STEPS)) / pixels * last;
    float before = 0.0;
    float zBefore = from.z;
    for (int i = 1; i <= STEPS; i++) {
        float t = (float(i) + jitter) * dt;
        if (t > last) {
            return false;
        }
        float rayZ = (q0.z + (q1.z - q0.z) * t) / (k0 + (k1 - k0) * t);
        float seen = sceneZ(ivec2(s0 + d * t));
        float behind = seen - rayZ;
        float thick = abs(rayZ - zBefore) + THICKNESS * -rayZ;
        if (behind > 0.0 && behind < thick) {
            float near = before;
            float far = t;
            for (int j = 0; j < REFINE; j++) {
                float mid = 0.5 * (near + far);
                float midZ = (q0.z + (q1.z - q0.z) * mid) / (k0 + (k1 - k0) * mid);
                if (sceneZ(ivec2(s0 + d * mid)) - midZ > 0.0) {
                    far = mid;
                } else {
                    near = mid;
                }
            }
            hitPixel = s0 + d * far;
            hitView = (q0 + (q1 - q0) * far) / (k0 + (k1 - k0) * far);
            return true;
        }
        before = t;
        zBefore = rayZ;
    }
    return false;
}

vec3 viewAt(vec2 uv, float depth) {
    vec4 at = m_ProjectionInverse * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return at.xyz / at.w;
}

void main() {
    vec4 colour = getColor(m_Texture, texCoord);
    vec4 reflects = texture2D(m_Reflectance, texCoord);
    vec4 surface = texture2D(m_Surface, texCoord);
    float depth = texture2D(m_Depth, texCoord).r;
    float roughness = surface.a;
    float weight = 1.0 - smoothstep(ROUGH_FROM, ROUGH_TO, roughness);
    vec3 position = viewAt(texCoord, depth);
    vec3 normal = normalize(surface.xyz);
    vec3 toSurface = normalize(position);
    vec3 ray = reflect(toSurface, normal);
    weight *= 1.0 - smoothstep(EYE_FROM, EYE_TO, ray.z);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy + 5.588238 * float(m_Frame), vec2(0.06711056, 0.00583715))));

    vec2 hitPixel;
    vec3 hitView;
    if (reflects.a < 0.5 || depth >= 1.0 || weight <= 0.0 || !trace(position, ray, jitter, hitPixel, hitView)) {
        #ifdef ONLY_REFLECTION
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        #else
            gl_FragColor = colour;
        #endif
        return;
    }
    vec2 hitUv = hitPixel / vec2(textureSize(m_Depth, 0));
    vec2 inside = min(hitUv, 1.0 - hitUv);
    weight *= smoothstep(0.0, EDGE, min(inside.x, inside.y));
    weight *= 1.0 - smoothstep(END_FROM, 1.0, distance(position, hitView) / m_Reach);
    // A ray that meets a surface from behind has found a part of the scene it cannot see.
    weight *= 1.0 - smoothstep(0.0, 0.4, dot(texture2D(m_Surface, hitUv).xyz, ray));

    float aimed = max(dot(normal, -toSurface), 1e-3);
    vec3 spec = specularBrdf(reflects.rgb, roughness, aimed);
    vec3 probe = vec3(0.0);
    #ifdef PROBE
        // What PBRLighting has put in the picture from the probe: its lookup again, to take back as much as the screen gives.
        vec3 worldNormal = mat3(m_ViewInverse) * normal;
        vec3 worldRay = mat3(m_ViewInverse) * ray;
        float alpha = roughness * roughness;
        float smoothness = 1.0 - alpha;
        vec3 dominant = mix(worldNormal, worldRay, smoothness * (sqrt(smoothness) + alpha));
        probe = textureCubeLod(m_ProbeMap, dominant, sqrt(roughness) * (m_ProbeMips - 1.0)).rgb * spec;
    #endif
    #ifdef ONLY_REFLECTION
        gl_FragColor = vec4(vec3(pow(weight, 2.2)), 1.0);
    #else
        vec3 hit = getColor(m_Texture, hitUv).rgb;
        gl_FragColor = vec4(colour.rgb - min(probe * weight, colour.rgb) + hit * spec * weight, colour.a);
    #endif
}
