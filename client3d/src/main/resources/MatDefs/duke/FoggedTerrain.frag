#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec4 m_Color;
uniform vec4 m_Ambient;
uniform vec4 m_Sun;
uniform vec3 m_SunDirection;
uniform sampler2D m_FogMap;

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

void main() {
    vec4 albedo = m_Color;
    #ifdef HAS_COLORMAP
    albedo *= texture2D(m_ColorMap, texCoord);
    #endif

    // One sun and one flat ambient. The art is flat-shaded palette work, so
    // anything more would be spent on a look it was not drawn for.
    vec3 normal = normalize(worldNormal);
    float lambert = max(dot(normal, -m_SunDirection), 0.0);
    vec3 lit = albedo.rgb * (m_Ambient.rgb + m_Sun.rgb * lambert);

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

    // The dark, taken at this fragment's own place on the map. Every part of a
    // wall gets its own value, so the light runs out along the wall rather than
    // the wall being switched off, and the top and the foot of it agree.
    vec4 dark = texture2D(m_FogMap, fogCoord);
    gl_FragColor = vec4(mix(lit, dark.rgb, dark.a), albedo.a);
}
