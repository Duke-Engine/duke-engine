#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec4 m_OcclusionColour;

void main() {
    gl_FragColor = m_OcclusionColour;
}
