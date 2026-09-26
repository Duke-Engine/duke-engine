#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_Picture;

void main() {
    gl_FragColor = texture2D(m_Picture, gl_PointCoord);
}
