package com.jme3.renderer.web;

import com.jme3.renderer.RendererException;
import com.jme3.renderer.opengl.GL;
import com.jme3.renderer.opengl.GL2;
import com.jme3.renderer.opengl.GLES_30;
import com.jme3.renderer.opengl.GLExt;
import com.jme3.renderer.opengl.GLFbo;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBuffer;
import org.teavm.jso.JSBufferType;
import org.teavm.jso.JSObject;

/**
 * jME's GL on a WebGL2 context, after AndroidGL. WebGL names its objects by JS handles where GL uses ints, so every
 * buffer, texture, shader, program, framebuffer, renderbuffer, vertex array, query and uniform location is kept in one
 * table and GL is handed its index (0 stays "none"). A call WebGL2 does not have throws.
 */
public class WebGL implements GL, GL2, GLES_30, GLExt, GLFbo {
    /** {gl, o: handles, v(type, Int8Array): the typed view a pixel type wants}. */
    private final JSObject w;

    public WebGL(JSObject context) {
        w = init(context);
    }

    @JSBody(params = "gl", script = ""
            + "gl.getSupportedExtensions().forEach(function(e) { gl.getExtension(e); });"
            + "return {gl: gl, o: [null], v: function(t, d) {"
            + "  if (d == null) return null;"
            + "  switch (t) {"
            + "    case 0x1406: return new Float32Array(d.buffer, d.byteOffset, d.byteLength >> 2);" // FLOAT
            + "    case 0x1405: case 0x84FA: case 0x8C3B: case 0x8C3E: case 0x8368:" // UINT, 24_8, 10F_11F_11F, 5_9_9_9, 2_10_10_10_REV
            + "      return new Uint32Array(d.buffer, d.byteOffset, d.byteLength >> 2);"
            + "    case 0x1403: case 0x140B: case 0x8363: case 0x8033: case 0x8034:" // USHORT, HALF, 5_6_5, 4_4_4_4, 5_5_5_1
            + "      return new Uint16Array(d.buffer, d.byteOffset, d.byteLength >> 1);"
            + "    case 0x1402: return new Int16Array(d.buffer, d.byteOffset, d.byteLength >> 1);"
            + "    case 0x1404: return new Int32Array(d.buffer, d.byteOffset, d.byteLength >> 2);"
            + "    case 0x1400: return d;"
            + "    default: return new Uint8Array(d.buffer, d.byteOffset, d.byteLength);"
            + "  }"
            + "}};")
    private static native JSObject init(JSObject gl);

    // The views of a buffer's remaining elements: TeaVM's view of a buffer spans all of it.

    @JSBody(params = {"b", "p", "l"}, script = "return b.subarray(p, l);")
    private static native JSObject i8(@JSBuffer(JSBufferType.INT8) Buffer b, int p, int l);

    @JSBody(params = {"b", "p", "l"}, script = "return b.subarray(p, l);")
    private static native JSObject i16(@JSBuffer(JSBufferType.INT16) Buffer b, int p, int l);

    @JSBody(params = {"b", "p", "l"}, script = "return b.subarray(p, l);")
    private static native JSObject i32(@JSBuffer(JSBufferType.INT32) Buffer b, int p, int l);

    @JSBody(params = {"b", "p", "l"}, script = "return b.subarray(p, l);")
    private static native JSObject f32(@JSBuffer(JSBufferType.FLOAT32) Buffer b, int p, int l);

    private static JSObject view(ByteBuffer b) {
        return b == null ? null : i8(b, b.position(), b.limit());
    }

    private static JSObject view(ShortBuffer b) {
        return i16(b, b.position(), b.limit());
    }

    private static JSObject view(IntBuffer b) {
        return i32(b, b.position(), b.limit());
    }

    private static JSObject view(FloatBuffer b) {
        return f32(b, b.position(), b.limit());
    }

    private static JSObject upload(ByteBuffer b) {
        checkLimit(b);
        return view(b);
    }

    private static void checkLimit(Buffer buffer) {
        if (buffer != null && buffer.remaining() == 0) {
            throw new RendererException("Attempting to upload empty buffer (remaining = 0), that's an error");
        }
    }

    private static UnsupportedOperationException missing(String call) {
        return new UnsupportedOperationException("WebGL2 has no " + call);
    }

    // Plain calls, on the context or with handles looked up.

    @JSBody(params = {"w", "s"}, script = "return w.gl[s]();")
    private static native int call0(JSObject w, String s);

    @JSBody(params = {"w", "s", "a"}, script = "w.gl[s](a);")
    private static native void call(JSObject w, String s, int a);

    @JSBody(params = {"w", "s", "a", "b"}, script = "w.gl[s](a, b);")
    private static native void call(JSObject w, String s, int a, int b);

    @JSBody(params = {"w", "s", "a", "b", "c"}, script = "w.gl[s](a, b, c);")
    private static native void call(JSObject w, String s, int a, int b, int c);

    @JSBody(params = {"w", "s", "a", "b", "c", "d"}, script = "w.gl[s](a, b, c, d);")
    private static native void call(JSObject w, String s, int a, int b, int c, int d);

    @JSBody(params = {"w", "s", "a", "b", "c", "d", "e"}, script = "w.gl[s](a, b, c, d, e);")
    private static native void call(JSObject w, String s, int a, int b, int c, int d, int e);

    @JSBody(params = {"w", "s", "a", "b", "c", "d"}, script = "w.gl[s](a, b, c, d);")
    private static native void callf(JSObject w, String s, float a, float b, float c, float d);

    @JSBody(params = {"w", "s", "a", "b"}, script = "w.gl[s](a, w.o[b]);")
    private static native void callH1(JSObject w, String s, int a, int b);

    @JSBody(params = {"w", "s", "a"}, script = "w.gl[s](w.o[a]);")
    private static native void callH(JSObject w, String s, int a);

    @JSBody(params = {"w", "s", "a", "b"}, script = "w.gl[s](w.o[a], w.o[b]);")
    private static native void callHH(JSObject w, String s, int a, int b);

    /** Makes an object by {@code create<s>()} and answers its index. */
    @JSBody(params = {"w", "s", "a"}, script = "var x = a < 0 ? w.gl['create' + s]() : w.gl['create' + s](a);"
            + " if (x == null) return 0; w.o.push(x); return w.o.length - 1;")
    private static native int create(JSObject w, String s, int a);

    @JSBody(params = {"w", "s", "i"}, script = "w.gl['delete' + s](w.o[i]); w.o[i] = null;")
    private static native void delete(JSObject w, String s, int i);

    private void create(String what, IntBuffer ids) {
        for (int i = ids.position(); i < ids.limit(); i++) {
            ids.put(i, create(w, what, -1));
        }
    }

    private void delete(String what, IntBuffer ids) {
        for (int i = ids.position(); i < ids.limit(); i++) {
            delete(w, what, ids.get(i));
        }
    }

    @Override
    public void resetStats() {
    }

    @Override
    public void glActiveTexture(int texture) {
        call(w, "activeTexture", texture);
    }

    @Override
    public void glAttachShader(int program, int shader) {
        callHH(w, "attachShader", program, shader);
    }

    @Override
    public void glBeginQuery(int target, int query) {
        callH1(w, "beginQuery", target, query);
    }

    @Override
    public void glBindBuffer(int target, int buffer) {
        callH1(w, "bindBuffer", target, buffer);
    }

    @Override
    public void glBindTexture(int target, int texture) {
        callH1(w, "bindTexture", target, texture);
    }

    @Override
    public void glBlendFunc(int sfactor, int dfactor) {
        call(w, "blendFunc", sfactor, dfactor);
    }

    @Override
    public void glBlendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha) {
        call(w, "blendFuncSeparate", sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha);
    }

    @Override
    public void glBlendEquationSeparate(int colorMode, int alphaMode) {
        call(w, "blendEquationSeparate", colorMode, alphaMode);
    }

    @JSBody(params = {"w", "t", "d", "u"}, script = "w.gl.bufferData(t, d, u);")
    private static native void bufferData(JSObject w, int target, JSObject data, int usage);

    @JSBody(params = {"w", "t", "s", "u"}, script = "w.gl.bufferData(t, s, u);")
    private static native void bufferSize(JSObject w, int target, double size, int usage);

    @JSBody(params = {"w", "t", "o", "d"}, script = "w.gl.bufferSubData(t, o, d);")
    private static native void bufferSubData(JSObject w, int target, double offset, JSObject data);

    @Override
    public void glBufferData(int target, FloatBuffer data, int usage) {
        checkLimit(data);
        bufferData(w, target, view(data), usage);
    }

    @Override
    public void glBufferData(int target, ShortBuffer data, int usage) {
        checkLimit(data);
        bufferData(w, target, view(data), usage);
    }

    @Override
    public void glBufferData(int target, ByteBuffer data, int usage) {
        bufferData(w, target, upload(data), usage);
    }

    @Override
    public void glBufferData(int target, IntBuffer data, int usage) {
        checkLimit(data);
        bufferData(w, target, view(data), usage);
    }

    @Override
    public void glBufferData(int target, long dataSize, int usage) {
        bufferSize(w, target, dataSize, usage);
    }

    @Override
    public void glBufferSubData(int target, long offset, FloatBuffer data) {
        checkLimit(data);
        bufferSubData(w, target, offset, view(data));
    }

    @Override
    public void glBufferSubData(int target, long offset, ShortBuffer data) {
        checkLimit(data);
        bufferSubData(w, target, offset, view(data));
    }

    @Override
    public void glBufferSubData(int target, long offset, ByteBuffer data) {
        bufferSubData(w, target, offset, upload(data));
    }

    @Override
    public void glBufferSubData(int target, long offset, IntBuffer data) {
        checkLimit(data);
        bufferSubData(w, target, offset, view(data));
    }

    @JSBody(params = {"w", "t", "o", "d"}, script = "w.gl.getBufferSubData(t, o, d);")
    private static native void getBufferSubData(JSObject w, int target, double offset, JSObject data);

    @Override
    public void glGetBufferSubData(int target, long offset, ByteBuffer data) {
        getBufferSubData(w, target, offset, view(data));
    }

    @Override
    public void glClear(int mask) {
        call(w, "clear", mask);
    }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        callf(w, "clearColor", red, green, blue, alpha);
    }

    @JSBody(params = {"w", "r", "g", "b", "a"}, script = "w.gl.colorMask(r, g, b, a);")
    private static native void colorMask(JSObject w, boolean r, boolean g, boolean b, boolean a);

    @Override
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        colorMask(w, red, green, blue, alpha);
    }

    @Override
    public void glCompileShader(int shader) {
        callH(w, "compileShader", shader);
    }

    @JSBody(params = {"w", "t", "l", "f", "x", "y", "b", "d"},
            script = "w.gl.compressedTexImage2D(t, l, f, x, y, b, d);")
    private static native void compressedTexImage2D(JSObject w, int t, int l, int f, int x, int y, int b, JSObject d);

    @Override
    public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border,
            ByteBuffer data) {
        compressedTexImage2D(w, target, level, internalformat, width, height, 0, upload(data));
    }

    @JSBody(params = {"w", "t", "l", "x", "y", "wd", "h", "f", "d"},
            script = "w.gl.compressedTexSubImage2D(t, l, x, y, wd, h, f, d);")
    private static native void compressedTexSubImage2D(JSObject w, int t, int l, int x, int y, int wd, int h, int f,
            JSObject d);

    @Override
    public void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height,
            int format, ByteBuffer data) {
        compressedTexSubImage2D(w, target, level, xoffset, yoffset, width, height, format, upload(data));
    }

    @Override
    public int glCreateProgram() {
        return create(w, "Program", -1);
    }

    @Override
    public int glCreateShader(int shaderType) {
        return create(w, "Shader", shaderType);
    }

    @Override
    public void glCullFace(int mode) {
        call(w, "cullFace", mode);
    }

    @Override
    public void glDeleteBuffers(IntBuffer buffers) {
        delete("Buffer", buffers);
    }

    @Override
    public void glDeleteProgram(int program) {
        delete(w, "Program", program);
    }

    @Override
    public void glDeleteShader(int shader) {
        delete(w, "Shader", shader);
    }

    @Override
    public void glDeleteTextures(IntBuffer textures) {
        delete("Texture", textures);
    }

    @Override
    public void glDepthFunc(int func) {
        call(w, "depthFunc", func);
    }

    @JSBody(params = {"w", "f"}, script = "w.gl.depthMask(f);")
    private static native void depthMask(JSObject w, boolean f);

    @Override
    public void glDepthMask(boolean flag) {
        depthMask(w, flag);
    }

    @Override
    public void glDepthRange(double nearVal, double farVal) {
        callf(w, "depthRange", (float) nearVal, (float) farVal, 0, 0);
    }

    @Override
    public void glDetachShader(int program, int shader) {
        callHH(w, "detachShader", program, shader);
    }

    @Override
    public void glDisable(int cap) {
        call(w, "disable", cap);
    }

    @Override
    public void glDisableVertexAttribArray(int index) {
        call(w, "disableVertexAttribArray", index);
    }

    @Override
    public void glDrawArrays(int mode, int first, int count) {
        call(w, "drawArrays", mode, first, count);
    }

    @Override
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, long indices) {
        call(w, "drawElements", mode, count, type, (int) indices);
    }

    @Override
    public void glEnable(int cap) {
        call(w, "enable", cap);
    }

    @Override
    public void glEnableVertexAttribArray(int index) {
        call(w, "enableVertexAttribArray", index);
    }

    @Override
    public void glEndQuery(int target) {
        call(w, "endQuery", target);
    }

    @Override
    public void glGenBuffers(IntBuffer buffers) {
        create("Buffer", buffers);
    }

    @Override
    public void glGenTextures(IntBuffer textures) {
        create("Texture", textures);
    }

    @Override
    public void glGenQueries(int num, IntBuffer buff) {
        for (int i = 0; i < num; i++) {
            buff.put(buff.position() + i, create(w, "Query", -1));
        }
    }

    @JSBody(params = {"w", "p", "n"}, script = "return w.gl.getAttribLocation(w.o[p], n);")
    private static native int getAttribLocation(JSObject w, int program, String name);

    @Override
    public int glGetAttribLocation(int program, String name) {
        return getAttribLocation(w, program, name);
    }

    /** getParameter as numbers: a boolean is 0 or 1, an array is spread, null is one 0. */
    @JSBody(params = {"w", "p"}, script = "var v = w.gl.getParameter(p);"
            + " if (v == null) return [0]; if (typeof v !== 'object') return [+v]; return Array.from(v, Number);")
    private static native double[] parameter(JSObject w, int pname);

    @Override
    public void glGetBoolean(int pname, ByteBuffer params) {
        double[] v = parameter(w, pname);
        for (int i = 0; i < v.length && params.position() + i < params.limit(); i++) {
            params.put(params.position() + i, (byte) (v[i] != 0 ? 1 : 0));
        }
    }

    @Override
    public int glGetError() {
        return call0(w, "getError");
    }

    @Override
    public void glGetFloat(int parameterId, FloatBuffer storeValues) {
        double[] v = parameter(w, parameterId);
        for (int i = 0; i < v.length && storeValues.position() + i < storeValues.limit(); i++) {
            storeValues.put(storeValues.position() + i, (float) v[i]);
        }
    }

    @Override
    public void glGetInteger(int pname, IntBuffer params) {
        double[] v = parameter(w, pname);
        for (int i = 0; i < v.length && params.position() + i < params.limit(); i++) {
            params.put(params.position() + i, (int) v[i]);
        }
    }

    // The info log's length is asked as a parameter in GL; WebGL answers the log itself.
    @JSBody(params = {"w", "p", "n"}, script = "var x = w.o[p];"
            + " if (n === 0x8B84) { var l = w.gl.getProgramInfoLog(x); return l ? l.length + 1 : 0; }"
            + " return +w.gl.getProgramParameter(x, n);")
    private static native int programParameter(JSObject w, int program, int pname);

    @JSBody(params = {"w", "s", "n"}, script = "var x = w.o[s];"
            + " if (n === 0x8B84) { var l = w.gl.getShaderInfoLog(x); return l ? l.length + 1 : 0; }"
            + " if (n === 0x8B88) { var c = w.gl.getShaderSource(x); return c ? c.length + 1 : 0; }"
            + " return +w.gl.getShaderParameter(x, n);")
    private static native int shaderParameter(JSObject w, int shader, int pname);

    @Override
    public void glGetProgram(int program, int pname, IntBuffer params) {
        params.put(params.position(), programParameter(w, program, pname));
    }

    @Override
    public void glGetShader(int shader, int pname, IntBuffer params) {
        params.put(params.position(), shaderParameter(w, shader, pname));
    }

    @JSBody(params = {"w", "p"}, script = "return w.gl.getProgramInfoLog(w.o[p]) || '';")
    private static native String programInfoLog(JSObject w, int program);

    @JSBody(params = {"w", "s"}, script = "return w.gl.getShaderInfoLog(w.o[s]) || '';")
    private static native String shaderInfoLog(JSObject w, int shader);

    @Override
    public String glGetProgramInfoLog(int program, int maxLength) {
        return programInfoLog(w, program);
    }

    @Override
    public String glGetShaderInfoLog(int shader, int maxLength) {
        return shaderInfoLog(w, shader);
    }

    @JSBody(params = {"w", "q", "n"}, script = "return +w.gl.getQueryParameter(w.o[q], n);")
    private static native double queryParameter(JSObject w, int query, int pname);

    @Override
    public long glGetQueryObjectui64(int query, int pname) {
        return (long) queryParameter(w, query, pname);
    }

    @Override
    public int glGetQueryObjectiv(int query, int pname) {
        return (int) queryParameter(w, query, pname);
    }

    /**
     * WebGL's own version string ("WebGL 2.0 (OpenGL ES 3.0 ...)") would be read as 2.0, so GL_VERSION leads with the
     * ES version GLRenderer parses and keeps "WebGL", which it looks for. Extensions get GL's "GL_" prefix.
     */
    @JSBody(params = {"w", "n"}, script = "var gl = w.gl;"
            + " if (n === 0x1F02) return 'OpenGL ES 3.0 ' + gl.getParameter(n);"
            + " if (n === 0x8B8C) return 'OpenGL ES GLSL ES 3.00';"
            + " if (n === 0x1F03) return gl.getSupportedExtensions().map(function(e) { return 'GL_' + e; }).join(' ');"
            + " var v = gl.getParameter(n); return v == null ? null : String(v);")
    private static native String string(JSObject w, int name);

    @Override
    public String glGetString(int name) {
        return string(w, name);
    }

    @JSBody(params = {"w", "p", "n"}, script = "var l = w.gl.getUniformLocation(w.o[p], n);"
            + " if (l == null) return -1; w.o.push(l); return w.o.length - 1;")
    private static native int uniformLocation(JSObject w, int program, String name);

    @Override
    public int glGetUniformLocation(int program, String name) {
        return uniformLocation(w, program, name);
    }

    @JSBody(params = {"w", "c"}, script = "return w.gl.isEnabled(c);")
    private static native boolean isEnabled(JSObject w, int cap);

    @Override
    public boolean glIsEnabled(int cap) {
        return isEnabled(w, cap);
    }

    @Override
    public void glLineWidth(float width) {
        callf(w, "lineWidth", width, 0, 0, 0);
    }

    @Override
    public void glLinkProgram(int program) {
        callH(w, "linkProgram", program);
    }

    @Override
    public void glPixelStorei(int pname, int param) {
        call(w, "pixelStorei", pname, param);
    }

    @Override
    public void glPolygonOffset(float factor, float units) {
        callf(w, "polygonOffset", factor, units, 0, 0);
    }

    @JSBody(params = {"w", "x", "y", "wd", "h", "f", "t", "d"},
            script = "w.gl.readPixels(x, y, wd, h, f, t, w.v(t, d));")
    private static native void readPixels(JSObject w, int x, int y, int wd, int h, int f, int t, JSObject d);

    @Override
    public void glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer data) {
        readPixels(w, x, y, width, height, format, type, view(data));
    }

    @JSBody(params = {"w", "x", "y", "wd", "h", "f", "t", "o"}, script = "w.gl.readPixels(x, y, wd, h, f, t, o);")
    private static native void readPixelsTo(JSObject w, int x, int y, int wd, int h, int f, int t, double o);

    @Override
    public void glReadPixels(int x, int y, int width, int height, int format, int type, long offset) {
        readPixelsTo(w, x, y, width, height, format, type, offset);
    }

    @Override
    public void glScissor(int x, int y, int width, int height) {
        call(w, "scissor", x, y, width, height);
    }

    @JSBody(params = {"w", "s", "c"}, script = "w.gl.shaderSource(w.o[s], c);")
    private static native void shaderSource(JSObject w, int shader, String code);

    @Override
    public void glShaderSource(int shader, String[] string, IntBuffer length) {
        shaderSource(w, shader, String.join("", string));
    }

    @Override
    public void glStencilFuncSeparate(int face, int func, int ref, int mask) {
        call(w, "stencilFuncSeparate", face, func, ref, mask);
    }

    @Override
    public void glStencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        call(w, "stencilOpSeparate", face, sfail, dpfail, dppass);
    }

    @JSBody(params = {"w", "t", "l", "i", "x", "y", "b", "f", "ty", "d"},
            script = "w.gl.texImage2D(t, l, i, x, y, b, f, ty, w.v(ty, d));")
    private static native void texImage2D(JSObject w, int t, int l, int i, int x, int y, int b, int f, int ty,
            JSObject d);

    @Override
    public void glTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int format,
            int type, ByteBuffer data) {
        if (data == null && renderable(internalFormat) != internalFormat) { // a target to draw to, so it has no data
            internalFormat = renderable(internalFormat);
            format = GL_RGBA;
        }
        texImage2D(w, target, level, internalFormat, width, height, border, format, type, view(data));
    }

    private static final int GL_RGBA = 0x1908;

    /**
     * WebGL2 draws to no three-channel float texture (RGB16F, RGB32F), which a desktop does: the four-channel one
     * stands in, its alpha unused.
     */
    private static int renderable(int internalFormat) {
        return switch (internalFormat) {
            case 0x881B -> 0x881A; // RGB16F -> RGBA16F
            case 0x8815 -> 0x8814; // RGB32F -> RGBA32F
            default -> internalFormat;
        };
    }

    @JSBody(params = {"w", "t", "p", "v"}, script = "w.gl.texParameterf(t, p, v);")
    private static native void texParameterf(JSObject w, int target, int pname, float param);

    @Override
    public void glTexParameterf(int target, int pname, float param) {
        texParameterf(w, target, pname, param);
    }

    @Override
    public void glTexParameteri(int target, int pname, int param) {
        call(w, "texParameteri", target, pname, param);
    }

    @JSBody(params = {"w", "t", "l", "x", "y", "wd", "h", "f", "ty", "d"},
            script = "w.gl.texSubImage2D(t, l, x, y, wd, h, f, ty, w.v(ty, d));")
    private static native void texSubImage2D(JSObject w, int t, int l, int x, int y, int wd, int h, int f, int ty,
            JSObject d);

    @Override
    public void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format,
            int type, ByteBuffer data) {
        texSubImage2D(w, target, level, xoffset, yoffset, width, height, format, type, view(data));
    }

    // Uniforms: the location is a handle.

    @JSBody(params = {"w", "s", "l", "d"}, script = "w.gl[s](w.o[l], d);")
    private static native void uniformv(JSObject w, String s, int location, JSObject data);

    @JSBody(params = {"w", "s", "l", "t", "d"}, script = "w.gl[s](w.o[l], t, d);")
    private static native void uniformMatrix(JSObject w, String s, int location, boolean transpose, JSObject data);

    @JSBody(params = {"w", "s", "l", "a", "b", "c", "d"}, script = "w.gl[s](w.o[l], a, b, c, d);")
    private static native void uniformf(JSObject w, String s, int location, float a, float b, float c, float d);

    @JSBody(params = {"w", "l", "a"}, script = "w.gl.uniform1i(w.o[l], a);")
    private static native void uniform1i(JSObject w, int location, int a);

    @Override
    public void glUniform1(int location, FloatBuffer value) {
        uniformv(w, "uniform1fv", location, view(value));
    }

    @Override
    public void glUniform1(int location, IntBuffer value) {
        uniformv(w, "uniform1iv", location, view(value));
    }

    @Override
    public void glUniform1f(int location, float v0) {
        uniformf(w, "uniform1f", location, v0, 0, 0, 0);
    }

    @Override
    public void glUniform1i(int location, int v0) {
        uniform1i(w, location, v0);
    }

    @Override
    public void glUniform2(int location, IntBuffer value) {
        uniformv(w, "uniform2iv", location, view(value));
    }

    @Override
    public void glUniform2(int location, FloatBuffer value) {
        uniformv(w, "uniform2fv", location, view(value));
    }

    @Override
    public void glUniform2f(int location, float v0, float v1) {
        uniformf(w, "uniform2f", location, v0, v1, 0, 0);
    }

    @Override
    public void glUniform3(int location, IntBuffer value) {
        uniformv(w, "uniform3iv", location, view(value));
    }

    @Override
    public void glUniform3(int location, FloatBuffer value) {
        uniformv(w, "uniform3fv", location, view(value));
    }

    @Override
    public void glUniform3f(int location, float v0, float v1, float v2) {
        uniformf(w, "uniform3f", location, v0, v1, v2, 0);
    }

    @Override
    public void glUniform4(int location, FloatBuffer value) {
        uniformv(w, "uniform4fv", location, view(value));
    }

    @Override
    public void glUniform4(int location, IntBuffer value) {
        uniformv(w, "uniform4iv", location, view(value));
    }

    @Override
    public void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        uniformf(w, "uniform4f", location, v0, v1, v2, v3);
    }

    @Override
    public void glUniformMatrix3(int location, boolean transpose, FloatBuffer value) {
        uniformMatrix(w, "uniformMatrix3fv", location, transpose, view(value));
    }

    @Override
    public void glUniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        uniformMatrix(w, "uniformMatrix4fv", location, transpose, view(value));
    }

    @Override
    public void glUseProgram(int program) {
        callH(w, "useProgram", program);
    }

    @JSBody(params = {"w", "i", "s", "t", "n", "st", "p"}, script = "w.gl.vertexAttribPointer(i, s, t, n, st, p);")
    private static native void vertexAttribPointer(JSObject w, int index, int size, int type, boolean normalized,
            int stride, double pointer);

    @Override
    public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        vertexAttribPointer(w, index, size, type, normalized, stride, pointer);
    }

    @Override
    public void glViewport(int x, int y, int width, int height) {
        call(w, "viewport", x, y, width, height);
    }

    // GL2

    @Override
    public void glAlphaFunc(int func, float ref) {
    }

    @Override
    public void glPointSize(float size) {
    }

    @Override
    public void glPolygonMode(int face, int mode) {
    }

    @Override
    public void glDrawBuffer(int mode) {
        IntBuffer one = IntBuffer.allocate(16);
        int n = mode - GLFbo.GL_COLOR_ATTACHMENT0_EXT + 1;
        if (n <= 0 || n > 16) { // GL_NONE or GL_BACK
            one.put(mode);
        } else {
            for (int i = 0; i < n - 1; i++) {
                one.put(GL.GL_NONE);
            }
            one.put(mode);
        }
        one.flip();
        drawBuffers(w, toArray(one));
    }

    @Override
    public void glReadBuffer(int mode) {
        call(w, "readBuffer", mode);
    }

    @JSBody(params = {"w", "t", "l", "f", "x", "y", "z", "b", "d"},
            script = "w.gl.compressedTexImage3D(t, l, f, x, y, z, b, d);")
    private static native void compressedTexImage3D(JSObject w, int t, int l, int f, int x, int y, int z, int b,
            JSObject d);

    @Override
    public void glCompressedTexImage3D(int target, int level, int internalFormat, int width, int height, int depth,
            int border, ByteBuffer data) {
        compressedTexImage3D(w, target, level, internalFormat, width, height, depth, border, upload(data));
    }

    @JSBody(params = {"w", "t", "l", "x", "y", "z", "wd", "h", "dp", "f", "d"},
            script = "w.gl.compressedTexSubImage3D(t, l, x, y, z, wd, h, dp, f, d);")
    private static native void compressedTexSubImage3D(JSObject w, int t, int l, int x, int y, int z, int wd, int h,
            int dp, int f, JSObject d);

    @Override
    public void glCompressedTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width,
            int height, int depth, int format, ByteBuffer data) {
        compressedTexSubImage3D(w, target, level, xoffset, yoffset, zoffset, width, height, depth, format,
                upload(data));
    }

    @JSBody(params = {"w", "t", "l", "i", "x", "y", "z", "b", "f", "ty", "d"},
            script = "w.gl.texImage3D(t, l, i, x, y, z, b, f, ty, w.v(ty, d));")
    private static native void texImage3D(JSObject w, int t, int l, int i, int x, int y, int z, int b, int f, int ty,
            JSObject d);

    @Override
    public void glTexImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border,
            int format, int type, ByteBuffer data) {
        texImage3D(w, target, level, internalFormat, width, height, depth, border, format, type, view(data));
    }

    @JSBody(params = {"w", "t", "l", "x", "y", "z", "wd", "h", "dp", "f", "ty", "d"},
            script = "w.gl.texSubImage3D(t, l, x, y, z, wd, h, dp, f, ty, w.v(ty, d));")
    private static native void texSubImage3D(JSObject w, int t, int l, int x, int y, int z, int wd, int h, int dp,
            int f, int ty, JSObject d);

    @Override
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height,
            int depth, int format, int type, ByteBuffer data) {
        texSubImage3D(w, target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, view(data));
    }

    // GLES_30

    @Override
    public void glBindVertexArray(int array) {
        callH(w, "bindVertexArray", array);
    }

    @Override
    public void glDeleteVertexArrays(IntBuffer arrays) {
        delete("VertexArray", arrays);
    }

    @Override
    public void glGenVertexArrays(IntBuffer arrays) {
        create("VertexArray", arrays);
    }

    // GLExt

    @Override
    public void glDrawArraysInstancedARB(int mode, int first, int count, int primcount) {
        call(w, "drawArraysInstanced", mode, first, count, primcount);
    }

    @JSBody(params = {"w", "b"}, script = "w.gl.drawBuffers(b);")
    private static native void drawBuffers(JSObject w, int[] buffers);

    private static int[] toArray(IntBuffer b) {
        int[] a = new int[b.remaining()];
        for (int i = 0; i < a.length; i++) {
            a[i] = b.get(b.position() + i);
        }
        return a;
    }

    @Override
    public void glDrawBuffers(IntBuffer bufs) {
        drawBuffers(w, toArray(bufs));
    }

    @Override
    public void glDrawElementsInstancedARB(int mode, int indicesCount, int type, long indicesBufferOffset,
            int primcount) {
        call(w, "drawElementsInstanced", mode, indicesCount, type, (int) indicesBufferOffset, primcount);
    }

    @Override
    public void glGetMultisample(int pname, int index, FloatBuffer val) {
        throw missing("glGetMultisample");
    }

    @Override
    public void glRenderbufferStorageMultisampleEXT(int target, int samples, int internalformat, int width,
            int height) {
        call(w, "renderbufferStorageMultisample", target, samples, renderable(internalformat), width, height);
    }

    @Override
    public void glTexImage2DMultisample(int target, int samples, int internalformat, int width, int height,
            boolean fixedSampleLocations) {
        throw missing("glTexImage2DMultisample");
    }

    @Override
    public void glVertexAttribDivisorARB(int index, int divisor) {
        call(w, "vertexAttribDivisor", index, divisor);
    }

    @JSBody(params = {"w", "c", "f"}, script = "return w.gl.fenceSync(c, f);")
    private static native JSObject fenceSync(JSObject w, int condition, int flags);

    @JSBody(params = {"w", "s", "f", "t"}, script = "return w.gl.clientWaitSync(s, f, t);")
    private static native int clientWaitSync(JSObject w, JSObject sync, int flags, double timeout);

    @JSBody(params = {"w", "s"}, script = "w.gl.deleteSync(s);")
    private static native void deleteSync(JSObject w, JSObject sync);

    @Override
    public Object glFenceSync(int condition, int flags) {
        return fenceSync(w, condition, flags);
    }

    @Override
    public int glClientWaitSync(Object sync, int flags, long timeout) {
        // WebGL caps the timeout at MAX_CLIENT_WAIT_TIMEOUT_WEBGL, which is 0 in browsers: it only polls.
        return clientWaitSync(w, (JSObject) sync, flags, 0);
    }

    @Override
    public void glDeleteSync(Object sync) {
        deleteSync(w, (JSObject) sync);
    }

    // GLFbo

    @Override
    public void glBlitFramebufferEXT(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1,
            int dstY1, int mask, int filter) {
        blit(w, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @JSBody(params = {"w", "a", "b", "c", "d", "e", "f", "g", "h", "m", "fl"},
            script = "w.gl.blitFramebuffer(a, b, c, d, e, f, g, h, m, fl);")
    private static native void blit(JSObject w, int a, int b, int c, int d, int e, int f, int g, int h, int m, int fl);

    @Override
    public void glBindFramebufferEXT(int target, int framebuffer) {
        callH1(w, "bindFramebuffer", target, framebuffer);
    }

    @Override
    public void glBindRenderbufferEXT(int target, int renderbuffer) {
        callH1(w, "bindRenderbuffer", target, renderbuffer);
    }

    @JSBody(params = {"w", "t"}, script = "return w.gl.checkFramebufferStatus(t);")
    private static native int checkFramebufferStatus(JSObject w, int target);

    @Override
    public int glCheckFramebufferStatusEXT(int target) {
        return checkFramebufferStatus(w, target);
    }

    @Override
    public void glDeleteFramebuffersEXT(IntBuffer framebuffers) {
        delete("Framebuffer", framebuffers);
    }

    @Override
    public void glDeleteRenderbuffersEXT(IntBuffer renderbuffers) {
        delete("Renderbuffer", renderbuffers);
    }

    @JSBody(params = {"w", "t", "a", "rt", "r"}, script = "w.gl.framebufferRenderbuffer(t, a, rt, w.o[r]);")
    private static native void framebufferRenderbuffer(JSObject w, int t, int a, int rt, int r);

    @Override
    public void glFramebufferRenderbufferEXT(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        framebufferRenderbuffer(w, target, attachment, renderbuffertarget, renderbuffer);
    }

    @JSBody(params = {"w", "t", "a", "tt", "x", "l"}, script = "w.gl.framebufferTexture2D(t, a, tt, w.o[x], l);")
    private static native void framebufferTexture2D(JSObject w, int t, int a, int tt, int x, int l);

    @Override
    public void glFramebufferTexture2DEXT(int target, int attachment, int textarget, int texture, int level) {
        framebufferTexture2D(w, target, attachment, textarget, texture, level);
    }

    @JSBody(params = {"w", "t", "a", "x", "l", "y"}, script = "w.gl.framebufferTextureLayer(t, a, w.o[x], l, y);")
    private static native void framebufferTextureLayer(JSObject w, int t, int a, int x, int l, int y);

    @Override
    public void glFramebufferTextureLayerEXT(int target, int attachment, int texture, int level, int layer) {
        framebufferTextureLayer(w, target, attachment, texture, level, layer);
    }

    @Override
    public void glGenFramebuffersEXT(IntBuffer framebuffers) {
        create("Framebuffer", framebuffers);
    }

    @Override
    public void glGenRenderbuffersEXT(IntBuffer renderbuffers) {
        create("Renderbuffer", renderbuffers);
    }

    @Override
    public void glGenerateMipmapEXT(int target) {
        call(w, "generateMipmap", target);
    }

    @Override
    public void glRenderbufferStorageEXT(int target, int internalformat, int width, int height) {
        call(w, "renderbufferStorage", target, renderable(internalformat), width, height);
    }
}
