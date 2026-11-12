package emul.java.net;

import java.io.IOException;

/** A browser listens on no port: see {@link TSocket}. */
public class TServerSocket implements java.io.Closeable {

    public TServerSocket(int port) throws IOException {
        throw TSocket.none();
    }

    public TSocket accept() throws IOException {
        throw TSocket.none();
    }

    public int getLocalPort() {
        return -1;
    }

    public void setSoTimeout(int millis) {
    }

    @Override
    public void close() {
    }
}
