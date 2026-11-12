package emul.java.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A browser opens no sockets: every TCP game (hosting, joining) fails here, by name. */
public class TSocket implements java.io.Closeable {

    public TSocket() {
    }

    public TSocket(String host, int port) throws IOException {
        throw none();
    }

    static IOException none() {
        return new IOException("a browser opens no TCP sockets");
    }

    public void connect(TSocketAddress address, int millis) throws IOException {
        throw none();
    }

    public InputStream getInputStream() throws IOException {
        throw none();
    }

    public OutputStream getOutputStream() throws IOException {
        throw none();
    }

    public TInetAddress getInetAddress() {
        return null;
    }

    public void setSoTimeout(int millis) {
    }

    public void setTcpNoDelay(boolean on) {
    }

    @Override
    public void close() {
    }
}
