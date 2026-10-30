package uz.dukeengine.client3d.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipFile;

/**
 * A game's data that is not in its APK: one zip, downloaded on the first start into the app's own files and unpacked
 * as {@code content/}, which {@link #mount} then puts on the class path, so the game and the engine read it as the
 * resources they read on the desktop. The download resumes where it stopped ({@code content.zip.part} stays across
 * restarts), is checked by size and SHA-256, unpacks beside the old folder and takes its place in one rename, and
 * {@code content/.version} says which pack it is.
 *
 * <p>Plain Java, no Android: {@link ContentActivity} is the screen around it.
 */
public final class ContentPack {

    /** Where a step of the work stands: {@code done} of {@code total} bytes. */
    public interface Progress {
        void at(String step, long done, long total);
    }

    private final File base;
    private final String url;
    private final String version;
    private final long size;
    private final String sha256;

    /**
     * @param base the app's files ({@code getExternalFilesDir(null)}): {@code content/} is made in it
     * @param version what {@code content/.version} must say, else the pack is fetched again
     * @param size the zip's length in bytes
     * @param sha256 the zip's SHA-256, lower-case hex
     */
    public ContentPack(File base, String url, String version, long size, String sha256) {
        this.base = base;
        this.url = url;
        this.version = version;
        this.size = size;
        this.sha256 = sha256;
    }

    public String url() {
        return url;
    }

    /** Whether this pack is unpacked already. */
    public boolean ready() {
        return version.equals(versionIn(new File(base, "content")));
    }

    /** Downloads what is missing, checks it, unpacks it and writes its version; on failure keeps what downloaded. */
    public void fetch(Progress progress) throws IOException {
        var part = new File(base, "content.zip.part");
        if (part.length() > size) {
            Files.delete(part.toPath());
        }
        if (part.length() < size) {
            download(part, progress);
        }
        progress.at("check", 0, size);
        if (!sha256.equalsIgnoreCase(sha256(part))) {
            Files.delete(part.toPath()); // shortcut: a bad pack is fetched whole again; keep chunk sums if that costs
            throw new IOException("the downloaded content is damaged (SHA-256 differs); try again");
        }
        unpack(part, progress);
        Files.delete(part.toPath());
    }

    private void download(File part, Progress progress) throws IOException {
        long have = part.length();
        long need = size - have + size; // the rest of the zip, and the folder it unpacks to: at least as big
        if (base.getUsableSpace() < need) {
            throw new IOException("not enough free space: " + mb(need) + " MB needed, " + mb(base.getUsableSpace())
                    + " MB free");
        }
        var http = (HttpURLConnection) URI.create(url).toURL().openConnection();
        http.setConnectTimeout(15_000);
        http.setReadTimeout(30_000);
        if (have > 0) {
            http.setRequestProperty("Range", "bytes=" + have + "-");
        }
        try {
            int code = http.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("the server answered HTTP " + code + " for " + url);
            }
            boolean resumed = code == HttpURLConnection.HTTP_PARTIAL;
            if (!resumed) {
                have = 0; // a server that ignores Range sends it all again
            }
            try (InputStream in = http.getInputStream(); OutputStream out = new FileOutputStream(part, resumed)) {
                var buffer = new byte[1 << 16];
                for (int n; (n = in.read(buffer)) > 0; ) {
                    out.write(buffer, 0, n);
                    have += n;
                    progress.at("download", have, size);
                }
            }
        } finally {
            http.disconnect();
        }
        if (part.length() != size) {
            throw new IOException("the download stopped at " + mb(part.length()) + " of " + mb(size) + " MB; try again");
        }
    }

    private void unpack(File zipFile, Progress progress) throws IOException {
        var folder = new File(base, "content");
        var fresh = new File(base, "content.new");
        deleteTree(fresh);
        try (var zip = new ZipFile(zipFile)) {
            long total = zip.stream().mapToLong(entry -> Math.max(0, entry.getSize())).sum();
            if (base.getUsableSpace() < total) {
                throw new IOException("not enough free space to unpack: " + mb(total) + " MB needed");
            }
            var root = fresh.getCanonicalPath() + File.separator;
            long done = 0;
            var buffer = new byte[1 << 16];
            for (var entries = zip.entries(); entries.hasMoreElements(); ) {
                var entry = entries.nextElement();
                var to = new File(fresh, entry.getName());
                if (!to.getCanonicalPath().startsWith(root)) { // a name with ../ in a downloaded zip
                    throw new IOException("the content names a file outside its folder: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(to.toPath());
                    continue;
                }
                Files.createDirectories(to.getParentFile().toPath());
                try (var in = zip.getInputStream(entry); var out = new FileOutputStream(to)) {
                    for (int n; (n = in.read(buffer)) > 0; ) {
                        out.write(buffer, 0, n);
                    }
                }
                done += Math.max(0, entry.getSize());
                progress.at("unpack", done, total);
            }
        }
        Files.write(new File(fresh, ".version").toPath(), version.getBytes(StandardCharsets.UTF_8));
        deleteTree(folder);
        if (!fresh.renameTo(folder)) {
            throw new IOException("could not move " + fresh + " to " + folder);
        }
    }

    /**
     * Puts {@code base/content/}, where a pack is unpacked, on the class path of {@code loader} (the app's), so
     * {@code getResource} and jME's class-path locator find its files as they find the APK's. Nothing where there is
     * no unpacked pack. Once a process.
     */
    public static synchronized void mount(File base, ClassLoader loader) {
        var folder = new File(base, "content");
        if (mounted || versionIn(folder) == null) {
            return;
        }
        try {
            // shortcut: BaseDexClassLoader.addDexPath is hidden API (unsupported list, allowed on API 35 with a log
            // line), which takes a folder as a resource root; if Android blocks it, give the game and the engine one
            // settable resource loader instead.
            loader.getClass().getMethod("addDexPath", String.class).invoke(loader, folder.getPath());
            mounted = true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot put " + folder + " on the class path", e);
        }
    }

    private static boolean mounted;

    private static String versionIn(File folder) {
        try {
            return new String(Files.readAllBytes(new File(folder, ".version").toPath()), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            return null;
        }
    }

    private static String sha256(File file) throws IOException {
        try (var in = new FileInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[1 << 20];
            for (int n; (n = in.read(buffer)) > 0; ) {
                digest.update(buffer, 0, n);
            }
            var hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteTree(File file) throws IOException {
        var inside = file.listFiles();
        if (inside != null) {
            for (var one : inside) {
                deleteTree(one);
            }
        }
        Files.deleteIfExists(file.toPath());
    }

    static long mb(long bytes) {
        return bytes / 1_000_000;
    }
}
