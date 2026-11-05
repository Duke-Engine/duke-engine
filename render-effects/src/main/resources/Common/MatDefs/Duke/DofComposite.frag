#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"
#import "Common/MatDefs/Duke/DofCoc.glsllib"

uniform COLORTEXTURE m_Texture;
uniform sampler2D m_Far;
uniform sampler2D m_Near;

varying vec2 texCoord;

// A pixel is its own sharp colour in focus, the far field where it is beyond the focus plane by more than a pixel or two
// (read by its own disc, so that what is in focus is never run over by what lies behind it), and the near field over both
// as much as the near field covers it. The half size fields are read by four bilinear taps half a texel off, a 3x3 tent that
// evens out the gather's sparse taps.
void main() {
    vec4 sharp = getColor(m_Texture, texCoord);
    float coc = confusion(texture2D(m_Depth, texCoord).r);
    vec2 offset = 0.5 / vec2(textureSize(m_Far, 0));
    vec3 far = vec3(0.0);
    vec3 near = vec3(0.0);
    float cover = 0.0;
    for (int i = 0; i < 4; i++) {
        vec2 at = texCoord + offset * vec2(i < 2 ? -1.0 : 1.0, (i & 1) == 0 ? -1.0 : 1.0);
        far += texture2D(m_Far, at).rgb * 0.25;
        vec4 n = texture2D(m_Near, at);
        near += n.rgb * n.a;
        cover += n.a * 0.25;
    }
    near = cover > 0.0 ? near / (cover * 4.0) : near;
    vec3 colour = mix(sharp.rgb, far, smoothstep(0.5, 3.0, coc));
    colour = mix(colour, near, cover);
    gl_FragColor = vec4(colour, sharp.a);
}
