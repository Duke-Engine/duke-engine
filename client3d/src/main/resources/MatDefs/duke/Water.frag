#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec4 m_Color;
uniform sampler2D m_FogMap;

varying vec2 fogCoord;
varying vec2 place;
varying vec2 along;
varying float shore;

#ifdef HAS_COLORMAP
uniform sampler2D m_ColorMap;
uniform vec4 m_ColorPlace;
#endif
#ifdef HAS_OVERLAYMAP
uniform sampler2D m_OverlayMap;
uniform vec4 m_OverlayPlace;
#endif
#ifdef HAS_EDGEMAP
uniform sampler2D m_EdgeMap;
#endif

void main() {
    vec4 water = m_Color;
    #ifdef ALONG
    vec2 at = along;
    #else
    vec2 at = place;
    #endif
    #ifdef HAS_COLORMAP
    #ifdef ALONG
    water *= texture2D(m_ColorMap, vec2(at.x, at.y + m_ColorPlace.w));
    #else
    water *= texture2D(m_ColorMap, at * m_ColorPlace.xy + m_ColorPlace.zw);
    #endif
    #endif
    #ifdef HAS_OVERLAYMAP
    vec4 over = texture2D(m_OverlayMap, place * m_OverlayPlace.xy + m_OverlayPlace.zw);
    water.rgb = mix(water.rgb, over.rgb * m_Color.rgb, over.a);
    #endif
    #ifdef HAS_EDGEMAP
    water.a *= texture2D(m_EdgeMap, vec2(along.x, 0.5)).a;
    #endif
    water.a *= shore;

    vec4 dark = texture2D(m_FogMap, fogCoord);
    gl_FragColor = vec4(mix(water.rgb, dark.rgb, dark.a), water.a);
}
