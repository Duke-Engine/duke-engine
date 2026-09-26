// jMonkeyEngine's post shadow filter (Common/MatDefs/Shadow/PostShadowFilter15.frag, BSD-3-Clause, Copyright (c)
// 2009-2024 jMonkeyEngine), for a directional light alone, a shadow multiplying what it falls on by a colour.
#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"
#import "Common/ShaderLib/Shadows.glsllib"

uniform COLORTEXTURE m_Texture;
uniform DEPTHTEXTURE m_DepthTexture;
uniform mat4 m_ViewProjectionMatrixInverse;
uniform vec4 m_ViewProjectionMatrixRow2;
uniform vec4 m_ShadowColor;

varying vec2 texCoord;

const mat4 biasMat = mat4(0.5, 0.0, 0.0, 0.0,
                          0.0, 0.5, 0.0, 0.0,
                          0.0, 0.0, 0.5, 0.0,
                          0.5, 0.5, 0.5, 1.0);

uniform mat4 m_LightViewProjectionMatrix0;
uniform mat4 m_LightViewProjectionMatrix1;
uniform mat4 m_LightViewProjectionMatrix2;
uniform mat4 m_LightViewProjectionMatrix3;

uniform vec3 m_LightDir;

#ifdef FADE
uniform vec2 m_FadeInfo;
#endif

vec3 getPosition(in float depth, in vec2 uv){
    vec4 pos = vec4(uv, depth, 1.0) * 2.0 - 1.0;
    pos = m_ViewProjectionMatrixInverse * pos;
    return pos.xyz / pos.w;
}

vec4 main_multiSample(in int numSample){
    float depth = fetchTextureSample(m_DepthTexture, texCoord, numSample).r;
    vec4 color = fetchTextureSample(m_Texture, texCoord, numSample);

    // Nothing drawn there: no shadow to fall on it.
    if (depth == 1.0) {
        return color;
    }

    vec4 worldPos = vec4(getPosition(depth, texCoord), 1.0);

    vec4 projCoord0 = biasMat * m_LightViewProjectionMatrix0 * worldPos;
    vec4 projCoord1 = biasMat * m_LightViewProjectionMatrix1 * worldPos;
    vec4 projCoord2 = biasMat * m_LightViewProjectionMatrix2 * worldPos;
    vec4 projCoord3 = biasMat * m_LightViewProjectionMatrix3 * worldPos;

    float shadowPosition = m_ViewProjectionMatrixRow2.x * worldPos.x + m_ViewProjectionMatrixRow2.y * worldPos.y
            + m_ViewProjectionMatrixRow2.z * worldPos.z + m_ViewProjectionMatrixRow2.w;

    float shadow = getDirectionalLightShadows(m_Splits, shadowPosition,
            m_ShadowMap0, m_ShadowMap1, m_ShadowMap2, m_ShadowMap3,
            projCoord0, projCoord1, projCoord2, projCoord3);

    #ifdef FADE
        shadow = clamp(max(0.0, mix(shadow, 1.0, (shadowPosition - m_FadeInfo.x) * m_FadeInfo.y)), 0.0, 1.0);
    #endif

    shadow = shadow * m_ShadowIntensity + (1.0 - m_ShadowIntensity);
    // Lit, as drawn; in shadow, multiplied by the colour — the reference's DESTCOLOR x ZERO over its stencil.
    return vec4(color.rgb * mix(m_ShadowColor.rgb, vec3(1.0), shadow), color.a);
}

void main() {
    #ifdef RESOLVE_MS
        vec4 color = vec4(0.0);
        for (int i = 0; i < m_NumSamples; i++){
            color += main_multiSample(i);
        }
        gl_FragColor = color / m_NumSamples;
    #else
        gl_FragColor = main_multiSample(0);
    #endif
}
