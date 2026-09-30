#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec4 m_Color;
uniform vec4 m_Ambient;
uniform vec4 m_Sun;
uniform vec3 m_SunDirection;
uniform sampler2D m_FogMap;
#ifdef FOG_WINDOW
uniform vec4 m_FogWindow;
#endif
#ifdef HAZE
uniform vec4 m_Haze;
#endif

varying vec2 fogCoord;
varying vec3 worldNormal;
varying vec3 worldPos;

// The moving lights: a flaming arrow crossing a room, a fireball on its way.
//
// Written out here for the same reason the sun is, and with more reason. This
// shader never asked the engine for a light, which was fine while the only lights
// were a sun and a flat ambient that never moved -- and quietly stopped being
// fine the moment something burning flew down a corridor: the creatures lit up,
// drawn as they are with the engine's own lighting, and the floor they flew over
// did not. A torch that lights everything except the ground is not a torch.
//
// Four of them, and the array is always full: an unused slot carries a black
// colour, so it costs the arithmetic and contributes nothing. That is cheaper
// than a branch and it is the same cost every frame, which is the property worth
// having.
#define POINT_LIGHTS 8
uniform vec4 m_PointLightColours[POINT_LIGHTS];
// xyz is where it is; w is 1/radius, so the attenuation needs no division.
uniform vec4 m_PointLightPositions[POINT_LIGHTS];

#ifdef HAS_COLORMAP
uniform sampler2D m_ColorMap;
varying vec2 texCoord;
#endif

#ifdef VERTEX_ALPHA
varying float vertexAlpha;
#endif

#ifdef VERTEX_LIGHT
varying vec3 vertexLight;
#endif

#ifdef HAS_SHADEMAP
uniform sampler2D m_ShadeMap;
uniform vec4 m_ShadePlace;
uniform float m_ShadeShown;
#endif
#ifdef HAS_SHADEMAP2
uniform sampler2D m_ShadeMap2;
uniform vec4 m_ShadePlace2;
#endif

void main() {
    vec4 albedo = m_Color;
    #ifdef HAS_COLORMAP
    albedo *= texture2D(m_ColorMap, texCoord);
    #endif
    #ifdef VERTEX_ALPHA
    albedo.a *= vertexAlpha;
    #endif

    // One sun and one flat ambient. The art is flat-shaded palette work, so
    // anything more would be spent on a look it was not drawn for.
    vec3 normal = normalize(worldNormal);
    #ifdef VERTEX_LIGHT
    // The ground's own lights, worked per corner and carried in: the reference's terrain lighting.
    vec3 lit = albedo.rgb * vertexLight;
    #else
    float lambert = max(dot(normal, -m_SunDirection), 0.0);
    vec3 lit = albedo.rgb * (m_Ambient.rgb + m_Sun.rgb * lambert);
    #endif

    // What is burning nearby. Squared falloff rather than inverse-square: a light
    // that never quite reaches zero has to be cut off somewhere, and a cut-off
    // shows as a ring on the floor. This one arrives at nothing on its own.
    for (int i = 0; i < POINT_LIGHTS; i++) {
        vec3 toLight = m_PointLightPositions[i].xyz - worldPos;
        float fall = max(0.0, 1.0 - length(toLight) * m_PointLightPositions[i].w);
        // Faces turned away from it stay dark, or a wall lights from inside.
        float facing = max(dot(normal, normalize(toLight + vec3(0.0, 0.001, 0.0))), 0.0);
        lit += albedo.rgb * m_PointLightColours[i].rgb * fall * fall * facing;
    }

    // The pictures laid over all the ground -- clouds sliding, a noise that stays -- multiplied in by world place.
    #ifdef HAS_SHADEMAP
    vec3 shade = texture2D(m_ShadeMap, worldPos.xz * m_ShadePlace.xy + m_ShadePlace.zw).rgb;
    #ifdef HAS_SHADEMAP2
    shade *= texture2D(m_ShadeMap2, worldPos.xz * m_ShadePlace2.xy + m_ShadePlace2.zw).rgb;
    #endif
    lit *= mix(vec3(1.0), shade, m_ShadeShown);
    #endif

    // The dark, taken at this fragment's own place on the map. Every part of a
    // wall gets its own value, so the light runs out along the wall rather than
    // the wall being switched off, and the top and the foot of it agree.
    vec4 dark = texture2D(m_FogMap, fogCoord);
    #ifdef FOG_WINDOW
    // A picture of the window round the camera repeats past it, another place's dark: past the window, never seen.
    if (worldPos.x < m_FogWindow.x || worldPos.z < m_FogWindow.y || worldPos.x > m_FogWindow.z
            || worldPos.z > m_FogWindow.w) {
        dark.a = 1.0;
    }
    #endif
    #ifdef HAZE
    // Far from where the camera looks the ground fades into nothing, the edge of what is built round it never seen.
    dark.a = max(dark.a, smoothstep(m_Haze.z, m_Haze.w, length(worldPos.xz - m_Haze.xy)));
    #endif
    gl_FragColor = vec4(mix(lit, dark.rgb, dark.a), albedo.a);
}
