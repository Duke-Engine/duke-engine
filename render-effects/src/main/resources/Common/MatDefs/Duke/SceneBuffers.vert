#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Instancing.glsllib"
#import "Common/ShaderLib/Skinning.glsllib"
#import "Common/ShaderLib/MorphAnim.glsllib"

attribute vec3 inPosition;
attribute vec3 inNormal;
attribute vec2 inTexCoord;

uniform mat4 m_PreviousViewProjection;
#ifndef INSTANCING
  uniform mat4 m_PreviousWorldMatrix;
#endif
#ifdef PREVIOUS_BONES
  uniform mat4 m_PreviousBoneMatrices[NUM_BONES];
#endif
#ifdef NORMALMAP
  attribute vec4 inTangent;
  varying vec4 vTangent;
#endif
#ifdef DISCARD_ALPHA
  uniform vec4 m_BaseColor;
  varying float vAlpha;
  #ifdef VERTEX_COLOR
    attribute vec4 inColor;
  #endif
#endif

varying vec2 texCoord;
varying vec3 vNormal;
varying vec4 vClip;
varying vec4 vPreviousClip;

void main() {
    vec4 position = vec4(inPosition, 1.0);
    vec3 normal = inNormal;
    #ifdef NORMALMAP
        vec3 tangent = inTangent.xyz;
    #endif

    // shortcut: last frame's shape takes this frame's morph weights, keep the weights too when a game morphs fast.
    #ifdef NUM_MORPH_TARGETS
        #ifdef NORMALMAP
            Morph_Compute(position, normal, tangent);
        #else
            Morph_Compute(position, normal);
        #endif
    #endif

    vec4 previous = position;
    #ifdef NUM_BONES
        #ifdef PREVIOUS_BONES
            if (inHWBoneWeight.x != 0.0) {
                previous = (m_PreviousBoneMatrices[int(inHWBoneIndex.x)] * inHWBoneWeight.x
                        + m_PreviousBoneMatrices[int(inHWBoneIndex.y)] * inHWBoneWeight.y
                        + m_PreviousBoneMatrices[int(inHWBoneIndex.z)] * inHWBoneWeight.z
                        + m_PreviousBoneMatrices[int(inHWBoneIndex.w)] * inHWBoneWeight.w) * previous;
            }
        #endif
        #ifdef NORMALMAP
            Skinning_Compute(position, normal, tangent);
        #else
            Skinning_Compute(position, normal);
        #endif
        #ifndef PREVIOUS_BONES
            previous = position;
        #endif
    #endif

    gl_Position = TransformWorldViewProjection(position);
    vClip = gl_Position;
    #ifdef INSTANCING
        // shortcut: an instance tells only the camera's motion, keep the last instance buffer when instanced things move.
        vPreviousClip = m_PreviousViewProjection * TransformWorld(previous);
    #else
        vPreviousClip = m_PreviousViewProjection * m_PreviousWorldMatrix * previous;
    #endif

    texCoord = inTexCoord;
    vNormal = TransformNormal(normal);
    #ifdef NORMALMAP
        vTangent = vec4(TransformNormal(tangent), inTangent.w);
    #endif
    #ifdef DISCARD_ALPHA
        vAlpha = m_BaseColor.a;
        #ifdef VERTEX_COLOR
            vAlpha *= inColor.a;
        #endif
    #endif
}
