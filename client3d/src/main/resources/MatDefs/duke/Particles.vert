#import "Common/ShaderLib/GLSLCompat.glsllib"

// A particle's whole life, worked out here from four numbers written once.
//
// Nothing about a particle is updated after it is born: where it started, how
// fast it left, when, and for how long are written into its four corners on the
// frame it is lit, and every frame after that this shader asks "how old is it
// now" and draws it where that puts it. So a burst of sixty sparks costs the
// processor one upload when it goes off and nothing at all while it burns --
// which is the difference between effects that are free while they play and
// effects that make a laptop hot.

uniform mat4 g_WorldMatrix;
uniform mat4 g_ViewMatrix;
uniform mat4 g_ViewProjectionMatrix;

uniform float m_Time;
uniform vec4 m_StartColour;
uniform vec4 m_EndColour;
uniform float m_ColourEase;
uniform float m_SizeStart;
uniform float m_SizeEnd;
uniform float m_SizeEase;
uniform float m_FadeIn;
uniform float m_FadeOut;
uniform vec3 m_Gravity;
uniform float m_Drag;
uniform float m_Stretch;
uniform float m_Spin;
uniform float m_PulseRate;
uniform float m_PulseDepth;
uniform float m_Opacity;
uniform float m_Rise;
uniform float m_RiseEase;

// Where it was born, in the geometry's own space.
attribute vec3 inPosition;
// Which corner of its quad this vertex is: 0 or 1, twice.
attribute vec2 inTexCoord;
// How fast it left, and which way.
attribute vec3 inNormal;
// When it was born, how long it lives, a number of its own, and how big it is
// against the layer.
attribute vec4 inTexCoord2;
// An AXIS particle's direction and, in w, its length; a PILLAR's way in y, up or
// down, and its height in w; anything else's angle at birth, in w.
attribute vec4 inTexCoord3;

varying vec2 texCoord;
varying vec4 colour;

// The curve every change in here is taken along.
//
// Straight lines are what machines do. A power above 1 is fast and then settling
// -- a thing that was struck, a flame catching -- and below -1 is slow and then
// sudden -- a thing about to arrive. Between the two it is the line, kept for
// the rare change that really is steady.
float ease(float t, float power) {
    if (power > 1.0001) {
        return 1.0 - pow(1.0 - t, power);
    }
    if (power < -1.0001) {
        return pow(t, -power);
    }
    return t;
}

vec2 turn(vec2 corner, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(corner.x * c - corner.y * s, corner.x * s + corner.y * c);
}

void main() {
    float birth = inTexCoord2.x;
    float life = max(inTexCoord2.y, 0.0001);
    float seed = inTexCoord2.z;
    float scale = inTexCoord2.w;
    float age = m_Time - birth;
    float t = age / life;
    texCoord = inTexCoord;

    if (age < 0.0 || t > 1.0) {
        // Not born yet, or already gone: put every corner in the same place
        // outside the view, where the triangle has no area and is thrown away
        // before a single pixel of it is considered.
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        colour = vec4(0.0);
        return;
    }

    vec3 born = (g_WorldMatrix * vec4(inPosition, 1.0)).xyz;
    vec3 leaving = (g_WorldMatrix * vec4(inNormal, 0.0)).xyz;
    // Air slows a thing exponentially, and how far it has gone is the integral
    // of that -- which has a closed form, so it is still one line.
    float slowed = exp(-m_Drag * age);
    float carried = m_Drag > 0.0001 ? (1.0 - slowed) / m_Drag : age;
    vec3 here = born + leaving * carried + 0.5 * m_Gravity * age * age;
    vec3 moving = leaving * slowed + m_Gravity * age;

    float size = mix(m_SizeStart, m_SizeEnd, ease(t, m_SizeEase)) * scale;
    vec2 corner = inTexCoord * 2.0 - 1.0;
    // Turned by the angle it was born at and by however far it has spun since.
    // The birth angle is the processor's to choose: random for a puff of smoke,
    // fixed for a slash that has to face the thing it cut.
    float spin = inTexCoord3.w + m_Spin * age;

    vec3 right = vec3(g_ViewMatrix[0][0], g_ViewMatrix[1][0], g_ViewMatrix[2][0]);
    vec3 up = vec3(g_ViewMatrix[0][1], g_ViewMatrix[1][1], g_ViewMatrix[2][1]);
    vec3 facing = vec3(g_ViewMatrix[0][2], g_ViewMatrix[1][2], g_ViewMatrix[2][2]);
    vec3 world;

#if defined(GROUND)
    // Lying on the floor: a ring, a scorch, a warning.
    vec2 laid = turn(corner, spin) * size * 0.5;
    world = here + vec3(laid.x, 0.0, laid.y);
#elif defined(PILLAR)
    // Standing on the floor and turned round its own upright towards the camera:
    // light coming down onto something, or rising out of the ground under it. The
    // end that moves crosses the whole height over the first Rise of its life, and
    // the shape is drawn over however much of the column there is so far, so its
    // soft ends are always the column's ends. Laid over the whole height and
    // uncovered instead, the moving end cut through the bright middle of it along a
    // hard straight line.
    float tall = max(inTexCoord3.w, 0.0);
    float crossed = m_Rise > 0.0001 ? ease(clamp(t / m_Rise, 0.0, 1.0), m_RiseEase) : 1.0;
    float bottom = inTexCoord3.y < 0.0 ? tall * (1.0 - crossed) : 0.0;
    float top = inTexCoord3.y < 0.0 ? tall : tall * crossed;
    float upright = mix(bottom, top, inTexCoord.y);
    vec3 across = cross(vec3(0.0, 1.0, 0.0), facing);
    float wide = length(across);
    across = wide > 0.0001 ? across / wide : right;
    // Brought towards the eye by half its width, as a card is: whoever it stands
    // on is inside the light, rather than cutting it in two.
    world = here + across * corner.x * size * 0.5 + vec3(0.0, upright, 0.0)
            + facing * size * 0.5;
#elif defined(AXIS)
    // Along a line it was given and towards the camera across it: a beam, or a
    // streak that is not moving but still has a direction.
    vec3 along = normalize((g_WorldMatrix * vec4(inTexCoord3.xyz, 0.0)).xyz);
    vec3 side = normalize(cross(along, facing));
    world = here + side * corner.x * size * 0.5 + along * corner.y * inTexCoord3.w * 0.5;
#elif defined(STREAK)
    // Drawn out along the way it is going, as the eye smears anything fast.
    vec3 across = moving - dot(moving, facing) * facing;
    float speed = length(across);
    vec3 along = speed > 0.0001 ? across / speed : up;
    vec3 side = normalize(cross(along, facing));
    world = here + side * corner.x * size * 0.5
            + along * corner.y * size * 0.5 * (1.0 + m_Stretch * speed);
#else
    vec2 turned = turn(corner, spin) * size * 0.5;
    // Pulled towards the eye by half its size. A flat card standing in the world
    // passes through the floor wherever it is bigger than its height above it,
    // and the floor cuts it off along a hard straight line -- the one thing a
    // puff of smoke must never have. Brought forward, the card clears the ground
    // it is drawn over and still sits where it was born on the screen.
    world = here + right * turned.x + up * turned.y + facing * size * 0.5;
#endif

    gl_Position = g_ViewProjectionMatrix * vec4(world, 1.0);

    float fadeIn = m_FadeIn > 0.0001 ? smoothstep(0.0, m_FadeIn, t) : 1.0;
    float fadeOut = m_FadeOut > 0.0001 ? 1.0 - smoothstep(1.0 - m_FadeOut, 1.0, t) : 1.0;
    float pulse = m_PulseRate > 0.0
            ? 1.0 - m_PulseDepth * (0.5 + 0.5 * sin(age * m_PulseRate * 6.2831853))
            : 1.0;
    colour = mix(m_StartColour, m_EndColour, ease(t, m_ColourEase));
    colour.a *= fadeIn * fadeOut * pulse * m_Opacity;
}
