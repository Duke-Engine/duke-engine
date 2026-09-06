package uz.duke.studio.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import uz.duke.studio.model.StudioProject;

/** Saves and loads Studio projects as pretty-printed JSON ({@code .duke} files). */
public final class ProjectIO {

    public static final String EXTENSION = "duke";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProjectIO() {
    }

    public static void save(StudioProject project, Path file) throws IOException {
        Files.writeString(file, GSON.toJson(project));
    }

    public static StudioProject load(Path file) throws IOException {
        var project = GSON.fromJson(Files.readString(file), StudioProject.class);
        if (project == null) {
            throw new IOException("empty or invalid project file: " + file);
        }
        project.ensureIntegrity();
        return project;
    }

    /** Serialize a project to JSON — used by undo/redo and duplication. */
    public static String toJson(StudioProject project) {
        return GSON.toJson(project);
    }

    /** Restore a project from {@link #toJson}. */
    public static StudioProject fromJson(String json) {
        var project = GSON.fromJson(json, StudioProject.class);
        project.ensureIntegrity();
        return project;
    }
}
