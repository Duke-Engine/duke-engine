package uz.dukeengine.client3d.android;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ContentPackTest {

    private ServerSocket server;
    private byte[] zip;
    private final List<String> ranges = new ArrayList<>();

    @Before
    public void serve() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry("data/game.duke"));
            out.write("Game {}".getBytes(StandardCharsets.UTF_8));
            out.putNextEntry(new ZipEntry("models/big.bin"));
            var noise = new byte[100_000]; // that does not deflate, so the zip is longer than the part kept
            new java.util.Random(1).nextBytes(noise);
            out.write(noise);
        }
        zip = bytes.toByteArray();
        // A server of one request at a time that answers Range: what the test asks of it, no more. (android.jar, which the
        // unit tests compile against, has no com.sun.net.httpserver.)
        server = new ServerSocket(0);
        var serving = new Thread(() -> {
            while (!server.isClosed()) {
                try (var socket = server.accept()) {
                    var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                    String range = null;
                    for (String line; (line = in.readLine()) != null && !line.isEmpty(); ) {
                        if (line.toLowerCase(java.util.Locale.ROOT).startsWith("range:")) {
                            range = line.substring(6).strip();
                        }
                    }
                    ranges.add(String.valueOf(range));
                    int from = range == null ? 0 : Integer.parseInt(range.replaceAll("\\D*(\\d+)-.*", "$1"));
                    var out = socket.getOutputStream();
                    out.write(((range == null ? "HTTP/1.1 200 OK" : "HTTP/1.1 206 Partial Content")
                            + "\r\nContent-Length: " + (zip.length - from) + "\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.ISO_8859_1));
                    out.write(zip, from, zip.length - from);
                } catch (IOException closed) {
                    // the test is over
                }
            }
        });
        serving.setDaemon(true);
        serving.start();
    }

    @After
    public void stop() throws IOException {
        server.close();
    }

    private ContentPack pack(File base, String sha) {
        return new ContentPack(base, "http://127.0.0.1:" + server.getLocalPort() + "/pack.zip", "v1",
                zip.length, sha);
    }

    @Test
    public void resumesAPartDownloadAndUnpacksIt() throws Exception {
        var base = Files.createTempDirectory("pack").toFile();
        Files.write(new File(base, "content.zip.part").toPath(), java.util.Arrays.copyOf(zip, 1000));
        var pack = pack(base, sha(zip));
        assertFalse(pack.ready());

        pack.fetch((step, done, total) -> {});

        assertEquals(List.of("bytes=1000-"), ranges);
        assertTrue(pack.ready());
        assertArrayEquals("Game {}".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(new File(base, "content/data/game.duke").toPath()));
        assertEquals(100_000, new File(base, "content/models/big.bin").length());
        assertFalse(new File(base, "content.zip.part").exists());
    }

    @Test
    public void throwsAwayADamagedDownload() throws Exception {
        var base = Files.createTempDirectory("pack").toFile();
        try {
            pack(base, "00").fetch((step, done, total) -> {});
            fail("a pack whose sum differs was unpacked");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("SHA-256"));
        }
        assertFalse(new File(base, "content").exists());
        assertFalse(new File(base, "content.zip.part").exists());
    }

    private static String sha(byte[] bytes) throws Exception {
        var hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
