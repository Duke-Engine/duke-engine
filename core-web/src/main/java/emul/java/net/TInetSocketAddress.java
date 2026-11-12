package emul.java.net;

/** Only what a TCP game names an address with; a browser never connects to one (see {@link TSocket}). */
public class TInetSocketAddress extends TSocketAddress {

    private final TInetAddress address;
    private final int port;

    public TInetSocketAddress(String host, int port) {
        this(new TInetAddress(host), port);
    }

    public TInetSocketAddress(TInetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    public TInetAddress getAddress() {
        return address;
    }

    public String getHostString() {
        return address == null ? null : address.getHostAddress();
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return getHostString() + ":" + port;
    }
}
