package emul.java.util;

import java.io.InputStream;
import java.util.Locale;

/** jME's AssetConfig text format is not read in the browser: loaders are registered in code there. */
public class TScanner {
    public TScanner(InputStream in, String charset) {
        throw new UnsupportedOperationException("Scanner is not available in the browser");
    }
    public TScanner useLocale(Locale l) { return this; }
    public boolean hasNext() { return false; }
    public String next() { return null; }
    public String nextLine() { return null; }
}
