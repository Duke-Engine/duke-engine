#import "Common/ShaderLib/GLSLCompat.glsllib"

// The shape is the texture's and the colour is the layer's.
//
// Every texture this reads is a white shape on transparency, so what comes out
// is the colour the vertex shader worked out for this moment of the particle's
// life, cut to the shape.
//
// Written premultiplied, under a blend that adds the colour and then takes away
// Cover's share of what was behind: so one blend is light (Cover 0: fire and
// magic add), stuff (Cover 1: smoke and dust cover) and anything between. The
// between is what fire needs. Pure light added to a pale floor is a pale smudge;
// a flame that also hides a little of the floor keeps its orange on any ground.

uniform sampler2D m_Texture;
uniform float m_Cover;

varying vec2 texCoord;
varying vec4 colour;

void main() {
    vec4 shape = texture2D(m_Texture, texCoord);
    // The faintest few hundredths of the texture's alpha are dropped. Some of the
    // pack's flares and flames carry a haze across the whole card at two or three
    // percent: nothing when drawn small, and a hard-edged square once a flash is a
    // hundred units wide over a floor its own light has just brightened.
    float amount = max(shape.a - 0.04, 0.0) / 0.96 * colour.a;
    gl_FragColor = vec4(shape.rgb * colour.rgb * amount, amount * m_Cover);
}
