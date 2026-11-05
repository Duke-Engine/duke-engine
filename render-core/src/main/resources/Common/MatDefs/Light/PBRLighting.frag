#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/PBR.glsllib"
#import "Common/ShaderLib/Parallax.glsllib"
#import "Common/ShaderLib/Lighting.glsllib"
#ifdef CLUSTERED
  #import "Common/ShaderLib/LightClusters.glsllib"
#endif

// The normal map's tangent frame is also the clear coat's own normal map's.
#if defined(NORMALMAP) || defined(PARALLAXMAP) || defined(CLEARCOAT_NORMALMAP)
  #define TANGENT_FRAME
#endif

varying vec2 texCoord;
#ifdef SEPARATE_TEXCOORD
  varying vec2 texCoord2;
#endif

varying vec4 Color;

uniform vec4 g_LightData[NB_LIGHTS];
uniform vec3 g_CameraPosition;
uniform vec4 g_AmbientLightColor;
#ifdef AMBIENT
  uniform vec4 m_Ambient;
#endif
#ifdef SCENE_AMBIENT
  uniform vec4 m_SceneAmbient;
#endif

uniform float m_Roughness;
uniform float m_Metallic;

varying vec3 wPosition;    


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

#ifdef BASECOLORMAP
  uniform sampler2D m_BaseColorMap;
#endif

#ifdef USE_PACKED_MR
  uniform sampler2D m_MetallicRoughnessMap;
#else
    #ifdef METALLICMAP
      uniform sampler2D m_MetallicMap;
    #endif
    #ifdef ROUGHNESSMAP
      uniform sampler2D m_RoughnessMap;
    #endif
#endif

#ifdef EMISSIVE
  uniform vec4 m_Emissive;
#endif
#ifdef EMISSIVEMAP
  uniform sampler2D m_EmissiveMap;
#endif
#if defined(EMISSIVE) || defined(EMISSIVEMAP)
    uniform float m_EmissivePower;
    uniform float m_EmissiveIntensity;
#endif 

#ifdef SPECGLOSSPIPELINE

  uniform vec4 m_Specular;
  uniform float m_Glossiness;
  #ifdef USE_PACKED_SG
    uniform sampler2D m_SpecularGlossinessMap;
  #else
    uniform sampler2D m_SpecularMap;
    uniform sampler2D m_GlossinessMap;
  #endif
#endif

#ifdef PARALLAXMAP
  uniform sampler2D m_ParallaxMap;  
#endif
#if (defined(PARALLAXMAP) || (defined(NORMALMAP_PARALLAX) && defined(NORMALMAP)))
    uniform float m_ParallaxHeight;
#endif

#ifdef LIGHTMAP
  uniform sampler2D m_LightMap;
#endif

#ifdef AO_STRENGTH
  uniform float m_AoStrength;
#endif
  
#ifdef TANGENT_FRAME
  uniform sampler2D m_NormalMap;   
  varying vec4 wTangent;
#endif
#ifdef CLEARCOAT
  uniform float m_ClearCoat;
  uniform float m_ClearCoatRoughness;
  #ifdef CLEARCOAT_NORMALMAP
    uniform sampler2D m_ClearCoatNormalMap;
  #endif
#endif
#ifdef SHEEN
  uniform vec4 m_SheenColor;
  uniform float m_SheenRoughness;
#endif
#ifdef SUBSURFACE
  uniform vec4 m_Subsurface;
  uniform float m_SubsurfaceTransmission;
#endif
#ifdef NORMALSCALE
  uniform float m_NormalScale;
#endif
varying vec3 wNormal;

// Specular-AA
#ifdef SPECULAR_AA_SCREEN_SPACE_VARIANCE
  uniform float m_SpecularAASigma;
#endif
#ifdef SPECULAR_AA_THRESHOLD
  uniform float m_SpecularAAKappa;
#endif

#ifdef DISCARD_ALPHA
  uniform float m_AlphaDiscardThreshold;
#endif

void main(){
    vec2 newTexCoord;
    vec3 viewDir = normalize(g_CameraPosition - wPosition);

    vec3 norm = normalize(wNormal);
    #ifdef TANGENT_FRAME
        vec3 tan = normalize(wTangent.xyz);
        mat3 tbnMat = mat3(tan, wTangent.w * cross( (norm), (tan)), norm);
    #endif

    #if (defined(PARALLAXMAP) || (defined(NORMALMAP_PARALLAX) && defined(NORMALMAP)))
       vec3 vViewDir =  viewDir * tbnMat;  
       #ifdef STEEP_PARALLAX
           #ifdef NORMALMAP_PARALLAX
               //parallax map is stored in the alpha channel of the normal map         
               newTexCoord = steepParallaxOffset(m_NormalMap, vViewDir, texCoord, m_ParallaxHeight);
           #else
               //parallax map is a texture
               newTexCoord = steepParallaxOffset(m_ParallaxMap, vViewDir, texCoord, m_ParallaxHeight);         
           #endif
       #else
           #ifdef NORMALMAP_PARALLAX
               //parallax map is stored in the alpha channel of the normal map         
               newTexCoord = classicParallaxOffset(m_NormalMap, vViewDir, texCoord, m_ParallaxHeight);
           #else
               //parallax map is a texture
               newTexCoord = classicParallaxOffset(m_ParallaxMap, vViewDir, texCoord, m_ParallaxHeight);
           #endif
       #endif
    #else
       newTexCoord = texCoord;    
    #endif
    
    #ifdef BASECOLORMAP
        vec4 albedo = texture2D(m_BaseColorMap, newTexCoord) * Color;
    #else
        vec4 albedo = Color;
    #endif

    //ao in r channel, roughness in green channel, metallic in blue channel!
    vec3 aoRoughnessMetallicValue = vec3(1.0, 1.0, 0.0);
    #ifdef USE_PACKED_MR
        aoRoughnessMetallicValue = texture2D(m_MetallicRoughnessMap, newTexCoord).rgb;
        float Roughness = aoRoughnessMetallicValue.g * max(m_Roughness, 1e-4);
        float Metallic = aoRoughnessMetallicValue.b * max(m_Metallic, 0.0);
    #else
        #ifdef ROUGHNESSMAP
            float Roughness = texture2D(m_RoughnessMap, newTexCoord).r * max(m_Roughness, 1e-4);
        #else
            float Roughness =  max(m_Roughness, 1e-4);
        #endif
        #ifdef METALLICMAP
            float Metallic = texture2D(m_MetallicMap, newTexCoord).r * max(m_Metallic, 0.0);
        #else
            float Metallic =  max(m_Metallic, 0.0);
        #endif
    #endif
 
    float alpha = albedo.a;

    #ifdef DISCARD_ALPHA
        if(alpha < m_AlphaDiscardThreshold){
            discard;
        }
    #endif
 
    // ***********************
    // Read from textures
    // ***********************
    #if defined(NORMALMAP)
      vec4 normalHeight = texture2D(m_NormalMap, newTexCoord);
      // Note we invert directx style normal maps to opengl style

      #ifdef NORMALSCALE
        vec3 normal = normalize((normalHeight.xyz * vec3(2.0, NORMAL_TYPE * 2.0, 2.0) - vec3(1.0, NORMAL_TYPE * 1.0, 1.0)) * vec3(m_NormalScale, m_NormalScale, 1.0));
      #else
        vec3 normal = normalize((normalHeight.xyz * vec3(2.0, NORMAL_TYPE * 2.0, 2.0) - vec3(1.0, NORMAL_TYPE * 1.0, 1.0)));
      #endif
      normal = normalize(tbnMat * normal);
      //normal = normalize(normal * inverse(tbnMat));
    #else
      vec3 normal = norm;
    #endif

    #ifdef SPECGLOSSPIPELINE

        #ifdef USE_PACKED_SG
            vec4 specularColor = texture2D(m_SpecularGlossinessMap, newTexCoord);
            float glossiness = specularColor.a * m_Glossiness;
            specularColor *= m_Specular;
        #else
            #ifdef SPECULARMAP
                vec4 specularColor = texture2D(m_SpecularMap, newTexCoord);
            #else
                vec4 specularColor = vec4(1.0);
            #endif
            #ifdef GLOSSINESSMAP
                float glossiness = texture2D(m_GlossinessMap, newTexCoord).r * m_Glossiness;
            #else
                float glossiness = m_Glossiness;
            #endif
            specularColor *= m_Specular;
        #endif
        vec4 diffuseColor = albedo;// * (1.0 - max(max(specularColor.r, specularColor.g), specularColor.b));
        Roughness = 1.0 - glossiness;
        vec3 fZero = specularColor.xyz;
    #else
        float specular = 0.5;
        float nonMetalSpec = 0.08 * specular;
        vec4 specularColor = (nonMetalSpec - nonMetalSpec * Metallic) + albedo * Metallic;
        vec4 diffuseColor = albedo - albedo * Metallic;
        // The light a surface sends back head on: about 4% of it, the metal's own colour on a metal.
        vec3 fZero = specularColor.rgb;
    #endif

    gl_FragColor.rgb = vec3(0.0);
    vec3 ao = vec3(1.0);

    #ifdef LIGHTMAP
       vec3 lightMapColor;
       #ifdef SEPARATE_TEXCOORD
          lightMapColor = texture2D(m_LightMap, texCoord2).rgb;
       #else
          lightMapColor = texture2D(m_LightMap, texCoord).rgb;
       #endif
       #ifdef AO_MAP
         lightMapColor.gb = lightMapColor.rr;
         ao = lightMapColor;
       #else
         gl_FragColor.rgb += diffuseColor.rgb * lightMapColor;
       #endif
       specularColor.rgb *= lightMapColor;
    #endif

    #if defined(AO_PACKED_IN_MR_MAP) && defined(USE_PACKED_MR)
       ao = aoRoughnessMetallicValue.rrr;
    #endif

    #ifdef AO_STRENGTH
       ao = 1.0 + m_AoStrength * (ao - 1.0);
       // sanity check
       ao = clamp(ao, 0.0, 1.0);
    #endif

    #ifdef SPECULAR_AA
        float sigma = 1.0;
        float kappa = 0.18;
        #ifdef SPECULAR_AA_SCREEN_SPACE_VARIANCE
            sigma = m_SpecularAASigma;
        #endif
        #ifdef SPECULAR_AA_THRESHOLD
            kappa = m_SpecularAAKappa;
        #endif
    #endif
    float ndotv = max( dot( normal, viewDir ),0.0);

    // The layers over the base, outermost last: what each lets through of what is under it, and what it adds.
    #if defined(CLEARCOAT) || defined(SHEEN)
        float indirectKeep = 1.0;
        vec3 coatNormal = norm;
        vec4 coatTerm = vec4(0.0);
        vec3 sheenTint = vec3(0.0);
    #endif
    #ifdef SHEEN
        // Below 0.2 the fit of PBR_SheenAlbedo is out of range, and a cloth that sharp is a satin, not a sheen.
        float sheenRoughness = clamp(m_SheenRoughness, 0.2, 1.0);
        float sheenAlbedo = PBR_SheenAlbedo(sheenRoughness, ndotv);
        // The base gives up what the cloth takes, so a white cloth under a white sky stays under 1.
        float sheenKeep = 1.0 - max(m_SheenColor.r, max(m_SheenColor.g, m_SheenColor.b)) * sheenAlbedo;
        sheenTint = m_SheenColor.rgb * sheenAlbedo;
        indirectKeep *= sheenKeep;
    #endif
    #ifdef CLEARCOAT
        #ifdef CLEARCOAT_NORMALMAP
            coatNormal = normalize(tbnMat * (texture2D(m_ClearCoatNormalMap, newTexCoord).xyz * vec3(2.0, NORMAL_TYPE * 2.0, 2.0) - vec3(1.0, NORMAL_TYPE * 1.0, 1.0)));
        #endif
        float coat = clamp(m_ClearCoat, 0.0, 1.0);
        // Under 0.045 the lobe is narrower than a pixel and its peak overflows half floats.
        float coatRoughness = max(m_ClearCoatRoughness, 0.045);
        float ndotvCoat = max(dot(coatNormal, viewDir), 0.0);
        coatTerm = vec4(coat, coatRoughness, ndotvCoat, 0.0);
        float coatKeep = 1.0 - coat * integrateBRDFApprox(vec3(COAT_F0), coatRoughness, ndotvCoat).x;
        indirectKeep *= coatKeep;
        sheenTint *= coatKeep;
    #endif
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
    for( int i = 0;i < NB_LIGHTS; i+=3){
        vec4 lightColor = g_LightData[i];
        vec4 lightData1 = g_LightData[i+1];
        vec4 lightData2 = g_LightData[i+2];
    #endif
        vec4 lightDir;
        vec3 lightVec;            
        lightComputeDir(wPosition, lightColor.w, lightData1, lightDir, lightVec);

        float fallOff = 1.0;
        #if __VERSION__ >= 110
            // allow use of control flow
        if(lightColor.w > 1.0){
        #endif
            fallOff =  computeSpotFalloff(lightData2, lightVec);
        #if __VERSION__ >= 110
        }
        #endif
        //point light attenuation
        fallOff *= lightDir.w;

        lightDir.xyz = normalize(lightDir.xyz);            
        vec3 directDiffuse;
        vec3 directSpecular;

        #ifdef SPECULAR_AA
            float hdotv = PBR_ComputeDirectLightWithSpecularAA(
                                normal, lightDir.xyz, viewDir,
                                lightColor.rgb, fZero, Roughness, sigma, kappa, ndotv,
                                directDiffuse,  directSpecular);
        #else
            float hdotv = PBR_ComputeDirectLight(
                                normal, lightDir.xyz, viewDir,
                                lightColor.rgb, fZero, Roughness, ndotv,
                                directDiffuse,  directSpecular);
        #endif

        #ifdef SUBSURFACE
            directDiffuse = PBR_SubsurfaceDiffuse(dot(normal, lightDir.xyz), lightColor.rgb, m_Subsurface.rgb);
        #endif

        vec3 directLighting = diffuseColor.rgb *directDiffuse + directSpecular;
        #ifdef SUBSURFACE
            directLighting += diffuseColor.rgb * PBR_SubsurfaceTransmission(normal, lightDir.xyz, viewDir, lightColor.rgb, m_Subsurface.rgb, m_SubsurfaceTransmission);
        #endif
        #ifdef SHEEN
            directLighting = directLighting * sheenKeep + PBR_Sheen(normal, lightDir.xyz, viewDir, lightColor.rgb, m_SheenColor.rgb, sheenRoughness, ndotv);
        #endif
        #ifdef CLEARCOAT
            vec3 coatDiffuse;
            vec3 coatSpecular;
            #ifdef SPECULAR_AA
                PBR_ComputeDirectLightWithSpecularAA(coatNormal, lightDir.xyz, viewDir, lightColor.rgb, vec3(COAT_F0), coatRoughness, sigma, kappa, ndotvCoat, coatDiffuse, coatSpecular);
            #else
                PBR_ComputeDirectLight(coatNormal, lightDir.xyz, viewDir, lightColor.rgb, vec3(COAT_F0), coatRoughness, ndotvCoat, coatDiffuse, coatSpecular);
            #endif
            // What the coat reflects does not reach the base.
            directLighting = directLighting * (1.0 - coat * F_Shlick(hdotv, vec3(COAT_F0)).x) + coatSpecular * coat;
        #endif
        
        gl_FragColor.rgb += directLighting * fallOff;
    }

    #if NB_PROBES >= 1
        vec3 color1 = vec3(0.0);
        vec3 color2 = vec3(0.0);
        vec3 color3 = vec3(0.0);
        float weight1 = 1.0;
        float weight2 = 0.0;
        float weight3 = 0.0;

        float ndf = renderProbe(viewDir, wPosition, normal, norm, Roughness, diffuseColor, specularColor, ndotv, ao, g_LightProbeData, g_ShCoeffs, g_PrefEnvMap, color1);
        #if NB_PROBES >= 2
            float ndf2 = renderProbe(viewDir, wPosition, normal, norm, Roughness, diffuseColor, specularColor, ndotv, ao, g_LightProbeData2, g_ShCoeffs2, g_PrefEnvMap2, color2);
        #endif
        #if NB_PROBES == 3
            float ndf3 = renderProbe(viewDir, wPosition, normal, norm, Roughness, diffuseColor, specularColor, ndotv, ao, g_LightProbeData3, g_ShCoeffs3, g_PrefEnvMap3, color3);
        #endif

        #if NB_PROBES >= 2
            float invNdf =  max(1.0 - ndf,0.0);
            float invNdf2 =  max(1.0 - ndf2,0.0);
            float sumNdf = ndf + ndf2;
            float sumInvNdf = invNdf + invNdf2;
            #if NB_PROBES == 3
                float invNdf3 = max(1.0 - ndf3,0.0);
                sumNdf += ndf3;
                sumInvNdf += invNdf3;
                weight3 =  ((1.0 - (ndf3 / sumNdf)) / (NB_PROBES - 1)) *  (invNdf3 / sumInvNdf);
            #endif

            weight1 = ((1.0 - (ndf / sumNdf)) / (NB_PROBES - 1)) *  (invNdf / sumInvNdf);
            weight2 = ((1.0 - (ndf2 / sumNdf)) / (NB_PROBES - 1)) *  (invNdf2 / sumInvNdf);

            float weightSum = weight1 + weight2 + weight3;

            weight1 /= weightSum;
            weight2 /= weightSum;
            weight3 /= weightSum;
        #endif

        #if defined(CLEARCOAT) || defined(SHEEN)
            color1 = color1 * indirectKeep + renderProbeLayers(viewDir, wPosition, normal, coatNormal, coatTerm, sheenTint, ao, g_LightProbeData, g_ShCoeffs, g_PrefEnvMap);
            #if NB_PROBES >= 2
                color2 = color2 * indirectKeep + renderProbeLayers(viewDir, wPosition, normal, coatNormal, coatTerm, sheenTint, ao, g_LightProbeData2, g_ShCoeffs2, g_PrefEnvMap2);
            #endif
            #if NB_PROBES == 3
                color3 = color3 * indirectKeep + renderProbeLayers(viewDir, wPosition, normal, coatNormal, coatTerm, sheenTint, ao, g_LightProbeData3, g_ShCoeffs3, g_PrefEnvMap3);
            #endif
        #endif

        #ifdef USE_AMBIENT_LIGHT
            color1.rgb *= g_AmbientLightColor.rgb;
            color2.rgb *= g_AmbientLightColor.rgb;
            color3.rgb *= g_AmbientLightColor.rgb;
        #endif
        gl_FragColor.rgb += color1 * clamp(weight1,0.0,1.0) + color2 * clamp(weight2,0.0,1.0) + color3 * clamp(weight3,0.0,1.0);

    #endif

    #if defined(EMISSIVE) || defined (EMISSIVEMAP)
        #ifdef EMISSIVEMAP
            vec4 emissive = texture2D(m_EmissiveMap, newTexCoord);
            #ifdef EMISSIVE
                emissive *= m_Emissive;
            #endif
        #else
            vec4 emissive = m_Emissive;
        #endif
        gl_FragColor += emissive * pow(emissive.a, m_EmissivePower) * m_EmissiveIntensity;
    #endif
    // Later passes of a thing lit in several have a black ambient: a flash and a tint are added once.
    #ifdef SCENE_AMBIENT
        gl_FragColor.rgb += albedo.rgb * max(g_AmbientLightColor.rgb - m_SceneAmbient.rgb, vec3(0.0));
    #endif
    #ifdef AMBIENT
        if (g_AmbientLightColor.rgb != vec3(0.0)) {
            gl_FragColor.rgb += albedo.rgb * m_Ambient.rgb;
        }
    #endif
    gl_FragColor.a = alpha;
   
}
