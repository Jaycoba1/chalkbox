package chalkbox.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Submission extends Source {

    public Submission(String baseDirectory, List<String> classPath) {
        super("submission", baseDirectory, classPath);
    }

    public String getAiDeclaration() {
        return getAiDeclaration("ai/README.txt");
    }

    public String getAiDeclaration(String path) {
        try {
            return Files.readString(
                Path.of(getBasePath(), path),
                StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            return "";
        }
    }
}
