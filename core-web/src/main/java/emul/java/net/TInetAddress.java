package emul.java.net;

/** A host as it was named: a browser resolves nothing (see {@link TSocket}). */
public class TInetAddress {

    private final String host;

    TInetAddress(String host) {
        this.host = host;
    }

    public String getHostAddress() {
        return host;
    }
}
