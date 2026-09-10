#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec4 m_Color;
uniform vec4 m_Ambient;
uniform vec4 m_Sun;
uniform vec3 m_SunDirection;
uniform sampler2D m_FogMap;

varying vec2 fogCoord;
varying vec3 worldNormal;

#ifdef HAS_COLORMAP
uniform sampler2D m_ColorMap;
varying vec2 texCoord;
#endif

void main() {
    vec4 albedo = m_Color;
    #ifdef HAS_COLORMAP
    albedo *= texture2D(m_ColorMap, texCoord);
    #endif

    // One sun and one flat ambient. The art is flat-shaded palette work, so
    // anything more would be spent on a look it was not drawn for.
    float lambert = max(dot(normalize(worldNormal), -m_SunDirection), 0.0);
    vec3 lit = albedo.rgb * (m_Ambient.rgb + m_Sun.rgb * lambert);

    // The dark, taken at this fragment's own place on the map. Every part of a
    // wall gets its own value, so the light runs out along the wall rather than
    // the wall being switched off, and the top and the foot of it agree.
    vec4 dark = texture2D(m_FogMap, fogCoord);
    gl_FragColor = vec4(mix(lit, dark.rgb, dark.a), albedo.a);
}
