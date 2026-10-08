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

#ifdef BLENDED
uniform sampler2D m_Surface0;
uniform sampler2D m_Surface1;
uniform sampler2D m_Surface2;
uniform sampler2D m_Surface3;
uniform sampler2D m_Surface4;
uniform sampler2D m_Surface5;
uniform sampler2D m_Surface6;
uniform sampler2D m_Surface7;
uniform vec4 m_SurfaceScale0;
uniform vec4 m_SurfaceScale1;
uniform vec4 m_SurfaceTints[8];
uniform float m_BlendSharp;
varying vec4 blend0;
varying vec4 blend1;
#ifdef BLEND_NOISE
uniform sampler2D m_BlendNoise;
uniform float m_NoiseScale;
uniform float m_BlendRagged;
#endif

#ifdef BLEND_VARIATION
uniform sampler2D m_Variation;
uniform float m_VariationScale;
uniform vec4 m_SurfaceVariation0;
uniform vec4 m_SurfaceVariation1;

// How much a surface is varied here: the variation picture, each surface reading it a little way off the others so they
// do not darken and lighten together, about one on average and nothing at all for a surface of no variation.
float varied(float strength, float slot) {
    float v = texture2D(m_Variation, worldPos.xz * m_VariationScale + vec2(slot * 0.173, slot * 0.311)).r;
    return mix(1.0, 0.5 + v, strength);
}
#define VARY(strength, slot) varied(strength, slot)
#else
#define VARY(strength, slot) 1.0
#endif

// The ground blended from its paint, as Warcraft III blends its tiles: each surface's weight comes in at the cell's
// corners and is blended across it; the noise moves a border only where the surface already shows -- a surface never
// appears where no cell round it is painted with it -- and the weights are sharpened and made to add up to one again.
vec3 blendedGround() {
    vec4 w0 = blend0;
    vec4 w1 = blend1;
    vec4 shown0 = step(vec4(0.0001), w0);
    vec4 shown1 = step(vec4(0.0001), w1);
    #ifdef BLEND_NOISE
    vec2 at = worldPos.xz * m_NoiseScale;
    vec4 n0 = texture2D(m_BlendNoise, at);
    vec4 n1 = texture2D(m_BlendNoise, at * 1.37 + vec2(0.31, 0.71));
    w0 = max(w0 + (n0 - 0.5) * m_BlendRagged, vec4(0.0)) * shown0;
    w1 = max(w1 + (n1 - 0.5) * m_BlendRagged, vec4(0.0)) * shown1;
    #endif
    w0 = pow(w0, vec4(m_BlendSharp));
    w1 = pow(w1, vec4(m_BlendSharp));
    float total = dot(w0, vec4(1.0)) + dot(w1, vec4(1.0));
    if (total < 0.0001) {
        // Every weight moved away at once: the paint's own weights, unbroken.
        w0 = blend0;
        w1 = blend1;
        total = max(dot(w0, vec4(1.0)) + dot(w1, vec4(1.0)), 0.0001);
    }
    w0 /= total;
    w1 /= total;
    vec2 p = worldPos.xz;
    vec3 colour = w0.x * texture2D(m_Surface0, p * m_SurfaceScale0.x).rgb * m_SurfaceTints[0].rgb
            * VARY(m_SurfaceVariation0.x, 0.0);
    colour += w0.y * texture2D(m_Surface1, p * m_SurfaceScale0.y).rgb * m_SurfaceTints[1].rgb
            * VARY(m_SurfaceVariation0.y, 1.0);
    colour += w0.z * texture2D(m_Surface2, p * m_SurfaceScale0.z).rgb * m_SurfaceTints[2].rgb
            * VARY(m_SurfaceVariation0.z, 2.0);
    colour += w0.w * texture2D(m_Surface3, p * m_SurfaceScale0.w).rgb * m_SurfaceTints[3].rgb
            * VARY(m_SurfaceVariation0.w, 3.0);
    colour += w1.x * texture2D(m_Surface4, p * m_SurfaceScale1.x).rgb * m_SurfaceTints[4].rgb
            * VARY(m_SurfaceVariation1.x, 4.0);
    colour += w1.y * texture2D(m_Surface5, p * m_SurfaceScale1.y).rgb * m_SurfaceTints[5].rgb
            * VARY(m_SurfaceVariation1.y, 5.0);
    colour += w1.z * texture2D(m_Surface6, p * m_SurfaceScale1.z).rgb * m_SurfaceTints[6].rgb
            * VARY(m_SurfaceVariation1.z, 6.0);
    colour += w1.w * texture2D(m_Surface7, p * m_SurfaceScale1.w).rgb * m_SurfaceTints[7].rgb
            * VARY(m_SurfaceVariation1.w, 7.0);
    return colour;
}
#endif

void main() {
    vec4 albedo = m_Color;
    #ifdef BLENDED
    albedo.rgb *= blendedGround();
    #endif
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
