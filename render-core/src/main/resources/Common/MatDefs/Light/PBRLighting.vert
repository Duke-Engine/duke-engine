#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Instancing.glsllib"
#import "Common/ShaderLib/Skinning.glsllib"
#import "Common/ShaderLib/MorphAnim.glsllib"

uniform vec4 m_BaseColor;
uniform vec4 g_AmbientLightColor;
varying vec2 texCoord;

#ifdef SEPARATE_TEXCOORD
  varying vec2 texCoord2;
  attribute vec2 inTexCoord2;
#endif

varying vec4 Color;

attribute vec3 inPosition;
attribute vec2 inTexCoord;
attribute vec3 inNormal;

#if defined(NORMALMAP) || defined(PARALLAXMAP) || defined(CLEARCOAT_NORMALMAP)
  #define TANGENT_FRAME
#endif

#ifdef VERTEX_COLOR
  attribute vec4 inColor;
#endif

varying vec3 wNormal;
varying vec3 wPosition;
#ifdef TANGENT_FRAME
    attribute vec4 inTangent;
    varying vec4 wTangent;
#endif

void main(){
    vec4 modelSpacePos = vec4(inPosition, 1.0);
    vec3 modelSpaceNorm = inNormal;

    #if defined(TANGENT_FRAME) && !defined(VERTEX_LIGHTING)
         vec3 modelSpaceTan  = inTangent.xyz;
    #endif

    #ifdef NUM_MORPH_TARGETS
         #if defined(TANGENT_FRAME) && !defined(VERTEX_LIGHTING)
            Morph_Compute(modelSpacePos, modelSpaceNorm, modelSpaceTan);
         #else
            Morph_Compute(modelSpacePos, modelSpaceNorm);
         #endif
    #endif

    #ifdef NUM_BONES
         #if defined(TANGENT_FRAME) && !defined(VERTEX_LIGHTING)
            Skinning_Compute(modelSpacePos, modelSpaceNorm, modelSpaceTan);
         #else
            Skinning_Compute(modelSpacePos, modelSpaceNorm);
         #endif
    #endif

    gl_Position = TransformWorldViewProjection(modelSpacePos);
    texCoord = inTexCoord;
    #ifdef SEPARATE_TEXCOORD
       texCoord2 = inTexCoord2;
    #endif

    wPosition = TransformWorld(modelSpacePos).xyz;
    wNormal  = TransformWorldNormal(modelSpaceNorm);

    #ifdef TANGENT_FRAME
      wTangent = vec4(TransformWorldNormal(modelSpaceTan),inTangent.w);
    #endif

    Color = m_BaseColor;
    
    #ifdef VERTEX_COLOR                    
        Color *= inColor;
    #endif
}
