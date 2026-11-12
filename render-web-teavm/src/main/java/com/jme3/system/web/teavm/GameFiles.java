package com.jme3.system.web.teavm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import org.teavm.extension.spi.resources.ResourcesPolicy;

/**
 * A duke game's text files, under {@code data/} and {@code maps/}, built into the script: a game reads them off the
 * class loader, which in a browser has only what the compiler put in. Models and pictures stay files the page fetches.
 * The compiler names only classes, so the files are found by walking those folders on the compile class path.
 */
public class GameFiles implements ResourcesPolicy {

    private static final List<String> FOLDERS = List.of("data/", "maps/");

    @Override
    public String[] supplyResources(Collection<? extends String> classes) {
        var names = new TreeSet<String>();
        try {
            for (var folder : FOLDERS) {
                var found = GameFiles.class.getClassLoader().getResources(folder);
                while (found.hasMoreElements()) {
                    list(found.nextElement(), folder, names);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names.toArray(String[]::new);
    }

    private static void list(URL url, String folder, Set<String> names) throws IOException {
        if (url.getProtocol().equals("jar")) {
            var connection = (JarURLConnection) url.openConnection();
            connection.setUseCaches(false); // a cached jar is shared, and closing it would close it for the compiler
            try (var jar = connection.getJarFile()) {
                jar.stream().map(JarEntry::getName).filter(n -> n.startsWith(folder) && !n.endsWith("/"))
                        .forEach(names::add);
            }
            return;
        }
        Path root;
        try {
            root = Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .forEach(f -> names.add(folder + root.relativize(f).toString().replace('\\', '/')));
        }
    }
}
