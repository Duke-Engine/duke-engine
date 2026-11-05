#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/PBR.glsllib"
#import "Common/ShaderLib/Lighting.glsllib"
#import "Common/ShaderLib/Water.glsllib"
#ifdef CLUSTERED
  #import "Common/ShaderLib/LightClusters.glsllib"
#else
  uniform mat4 g_ViewMatrix;
#endif

// Water, lit as PBRLighting lights a dielectric of F0 0.02 (the lights, the probes, the clusters), over the picture behind it: seen
// through the waves' refraction, dimmed by what the water between takes of each colour, plus the light the water scatters back.
// See uz.dukeengine.render.Water.

uniform vec4 g_LightData[NB_LIGHTS];
uniform vec3 g_CameraPosition;
uniform vec4 g_AmbientLightColor;

uniform float m_Time;
uniform float m_Roughness;
uniform vec3 m_Extinction;
uniform vec4 m_Scatter;
uniform float m_Foam;
uniform sampler2D m_Scene;
uniform sampler2D m_Behind;
// The camera's projection: m00, m11, m22, m23.
uniform vec4 m_Projection;

#if NB_PROBES >= 1
  uniform samplerCube g_PrefEnvMap;
  uniform vec3 g_ShCoeffs[9];
  uniform mat4 g_LightProbeData;
#endif
#if NB_PROBES >= 2
  uniform samplerCube g_PrefEnvMap2;
  uniform vec3 g_ShCoeffs2[9];
  uniform mat4 g_LightProbeData2;
#endif
#if NB_PROBES == 3
  uniform samplerCube g_PrefEnvMap3;
  uniform vec3 g_ShCoeffs3[9];
  uniform mat4 g_LightProbeData3;
#endif

varying vec3 wPosition;
varying vec3 wNormal;

// Water's index of refraction, squared.
const float INDEX2 = 1.7689;
// How far a bottom is seen shifted by a tilted surface, per unit of the water over it and of the tilt.
const float BEND = 0.25;
// The light that lit the bottom came through the water too, slantwise: this many times the depth.
const float SUN_PATH = 1.5;
// The sun's glint on water this smooth is far past what a half float holds: cut where the eye has long been white.
const float GLINT = 100.0;

// The view depth, positive in front of the camera, that a depth buffer value stands for.
float viewDepth(float depth) {
    return m_Projection.w / (depth * 2.0 - 1.0 + m_Projection.z);
}

// Foam's grain at p: slow blobs that drift.
float foamGrain(vec2 p, float time) {
    return 0.5 + 0.25 * sin(p.x * 5.1 + time * 0.9 + 2.0 * sin(p.y * 3.7)) + 0.25 * sin(p.y * 7.3 - time * 1.3 + 2.0 * sin(p.x * 4.1));
}

void main() {
    vec3 viewDir = normalize(g_CameraPosition - wPosition);
    vec3 up = normalize(wNormal);
    vec3 normal = waterNormal(up, wPosition, viewDir, m_Time);
    float ndotv = max(dot(normal, viewDir), 0.0);
    float roughness = m_Roughness;
    vec3 fZero = vec3(0.02);

    // The bottom is seen where the waves bend the ray: shifted along the tilt of the normal, more the deeper the water is.
    vec2 size = vec2(textureSize(m_Scene, 0));
    vec2 uv = gl_FragCoord.xy / size;
    float surface = viewDepth(gl_FragCoord.z);
    float behind = viewDepth(texture2D(m_Behind, uv).r);
    vec3 ray = vec3((uv * 2.0 - 1.0) / m_Projection.xy, 1.0);
    float cosView = max(dot(viewDir, up), 0.02);
    float down = min((behind - surface) * length(ray) * cosView, 4.0);
    vec2 tilt = (mat3(g_ViewMatrix) * (normal - up)).xy;
    vec2 seen = clamp(uv + 0.5 * tilt * down * BEND * m_Projection.xy / max(behind, 1e-3), 0.5 / size, 1.0 - 0.5 / size);
    float seenDepth = viewDepth(texture2D(m_Behind, seen).r);
    if (seenDepth < surface) {
        // Something stands in front of the water there: it is not on the bottom the ray reaches.
        seen = uv;
        seenDepth = behind;
    }
    vec3 bottom = texture2D(m_Scene, seen).rgb;

    // The path in the water: its depth under the surface, along the ray as it goes on bent toward the normal.
    float depth = (seenDepth - surface) * length(ray) * cosView;
    float cosWater = sqrt(1.0 - (1.0 - cosView * cosView) / INDEX2);
    vec3 through = exp(-m_Extinction * (depth / cosWater));
    // Foam where the water is shallow, broken by its grain.
    float foam = 0.0;
    if (m_Foam > 0.0) {
        float shore = 1.0 - smoothstep(0.0, m_Foam, depth);
        foam = 0.75 * shore * smoothstep(0.3, 0.7, foamGrain(wPosition.xz, m_Time) + 0.6 * shore);
    }
    vec3 lightLeft = exp(-m_Extinction * depth * SUN_PATH);
    through *= 1.0 - foam;
    // Light that gets in is not light that is reflected.
    vec3 gets = vec3(1.0) - F_Shlick(ndotv, fZero);
    vec3 body = mix(m_Scatter.rgb * (1.0 - through), vec3(0.85), foam) * gets;

    vec3 lit = vec3(0.0);
    vec3 glint = vec3(0.0);
    #ifdef CLUSTERED
    // The geometry's own lights, then its cluster's, with the same light for each.
    ivec2 clustered = clusterOf(wPosition);
    for (int i = 0; i < NB_LIGHTS + 3 * clustered.y; i += 3) {
        vec4 lightColor;
        vec4 lightData1;
        vec4 lightData2;
        if (i < NB_LIGHTS) {
            lightColor = g_LightData[i];
            lightData1 = g_LightData[i+1];
            lightData2 = g_LightData[i+2];
            // An empty slot: no light, and a direction of 0 / 0.
            if (lightColor.rgb == vec3(0.0)) {
                continue;
            }
        } else {
            int light = 3 * int(texelFetch(g_ClusterIndices, clusterTexel(clustered.x + (i - NB_LIGHTS) / 3), 0).r);
            lightColor = texelFetch(g_ClusterLights, clusterTexel(light), 0);
            lightData1 = texelFetch(g_ClusterLights, clusterTexel(light + 1), 0);
            lightData2 = texelFetch(g_ClusterLights, clusterTexel(light + 2), 0);
        }
    #else
    for (int i = 0; i < NB_LIGHTS; i += 3) {
        vec4 lightColor = g_LightData[i];
        vec4 lightData1 = g_LightData[i+1];
        vec4 lightData2 = g_LightData[i+2];
    #endif
        vec4 lightDir;
        vec3 lightVec;
        lightComputeDir(wPosition, lightColor.w, lightData1, lightDir, lightVec);
        float fallOff = 1.0;
        if (lightColor.w > 1.0) {
            fallOff = computeSpotFalloff(lightData2, lightVec);
        }
        fallOff *= lightDir.w;
        lightDir.xyz = normalize(lightDir.xyz);
        vec3 directDiffuse;
        vec3 directSpecular;
        PBR_ComputeDirectLight(normal, lightDir.xyz, viewDir, lightColor.rgb, fZero, roughness, ndotv, directDiffuse, directSpecular);
        lit += directDiffuse * fallOff;
        glint += directSpecular * fallOff;
    }

    gl_FragColor.rgb = bottom * through * lightLeft * gets + body * lit + min(glint, vec3(GLINT));

    #if NB_PROBES >= 1
        vec3 color1 = vec3(0.0);
        vec3 color2 = vec3(0.0);
        vec3 color3 = vec3(0.0);
        vec3 ao = vec3(1.0);
        vec4 bodyColour = vec4(body, 1.0);
        vec4 reflects = vec4(fZero, 1.0);
        float depth1 = renderProbe(viewDir, wPosition, normal, up, roughness, bodyColour, reflects, ndotv, ao, g_LightProbeData, g_ShCoeffs, g_PrefEnvMap, color1);
        float depth2 = 0.0;
        #if NB_PROBES >= 2
            depth2 = renderProbe(viewDir, wPosition, normal, up, roughness, bodyColour, reflects, ndotv, ao, g_LightProbeData2, g_ShCoeffs2, g_PrefEnvMap2, color2);
        #endif
        #if NB_PROBES == 3
            renderProbe(viewDir, wPosition, normal, up, roughness, bodyColour, reflects, ndotv, ao, g_LightProbeData3, g_ShCoeffs3, g_PrefEnvMap3, color3);
        #endif
        // Probes come narrowest first, as PBRLighting weighs them.
        vec3 weights = probeWeights(depth1, depth2, NB_PROBES);
        #ifdef USE_AMBIENT_LIGHT
            color1.rgb *= g_AmbientLightColor.rgb;
            color2.rgb *= g_AmbientLightColor.rgb;
            color3.rgb *= g_AmbientLightColor.rgb;
        #endif
        gl_FragColor.rgb += color1 * clamp(weights.x, 0.0, 1.0) + color2 * clamp(weights.y, 0.0, 1.0) + color3 * clamp(weights.z, 0.0, 1.0);
    #endif
    gl_FragColor.a = 1.0;
}
