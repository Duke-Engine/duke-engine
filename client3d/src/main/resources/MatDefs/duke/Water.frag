#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec4 m_Color;
uniform sampler2D m_FogMap;
#ifdef FOG_WINDOW
uniform vec4 m_FogWindow;
#endif

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
    #ifdef FOG_WINDOW
    // As the ground's: past the window round the camera, never seen.
    if (place.x < m_FogWindow.x || place.y < m_FogWindow.y || place.x > m_FogWindow.z || place.y > m_FogWindow.w) {
        dark.a = 1.0;
    }
    #endif
    gl_FragColor = vec4(mix(water.rgb, dark.rgb, dark.a), water.a);
}
