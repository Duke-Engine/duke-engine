package com.jme3.system.web;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLocator;
import com.jme3.asset.AssetManager;
import com.jme3.util.res.ResourceLoader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * Assets and resources read from the page's server, each when it is asked for: the asking (green) thread waits for
 * the fetch. The page may set {@code globalThis.dukeFile(path)} to a promise of an ArrayBuffer, null when missing;
 * without it a path is fetched under {@code assets/}.
 */
public final class WebFiles implements AssetLocator, ResourceLoader {
    @JSFunctor
    interface Got extends JSObject {
        void got(ArrayBuffer bytes);
    }

    @JSBody(params = {"path", "got"}, script = """
            var f = globalThis.dukeFile || function(p) {
              return fetch('assets/' + p).then(function(r) { return r.ok ? r.arrayBuffer() : null; }); };
            f(path).then(function(b) { got(b); }, function() { got(null); });
            """)
    private static native void fetch(String path, Got got);

    @Async
    private static native byte[] file(String path);

    private static void file(String path, AsyncCallback<byte[]> callback) {
        fetch(path, b -> callback.complete(b == null ? null : new Int8Array(b).copyToJavaArray()));
    }

    /** The file's bytes, or null if the server has none. */
    public static byte[] read(String path) {
        return file(path.startsWith("/") ? path.substring(1) : path);
    }

    @Override
    public void setRootPath(String rootPath) {
    }

    @Override
    public AssetInfo locate(AssetManager manager, AssetKey key) {
        byte[] bytes = read(key.getName());
        if (bytes == null) {
            return null;
        }
        return new AssetInfo(manager, key) {
            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    @Override
    public URL getResource(String path, Class<?> parent) {
        return null;
    }

    @Override
    public InputStream getResourceAsStream(String path, Class<?> parent) {
        // As Class.getResourceAsStream: a name without a leading slash is in the class's package.
        if (parent != null && !path.startsWith("/")) {
            String name = parent.getName();
            path = name.substring(0, name.lastIndexOf('.') + 1).replace('.', '/') + path;
        }
        byte[] bytes = read(path);
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }

    @Override
    public Enumeration<URL> getResources(String path) {
        return Collections.emptyEnumeration();
    }
}
