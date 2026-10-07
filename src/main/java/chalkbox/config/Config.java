package chalkbox.config;

import chalkbox.source.Solution;
import chalkbox.source.Submission;
import chalkbox.stages.bugfixes.BugFixes;
import java.nio.file.Path;
import java.util.List;
import org.github.gestalt.config.Gestalt;
import org.github.gestalt.config.builder.GestaltBuilder;
import org.github.gestalt.config.exceptions.GestaltException;
import org.github.gestalt.config.reflect.TypeCapture;
import org.github.gestalt.config.source.FileConfigSourceBuilder;

public class Config {

    private final Gestalt gestalt;

    public Config(Path path) throws ConfigException {
        GestaltBuilder builder = new GestaltBuilder();
        try {
            this.gestalt =
            builder
                .addSource(
                    FileConfigSourceBuilder.builder().setPath(path).build()
                )
                .build();
        } catch (GestaltException e) {
            throw new ConfigException(
                "unable to setup discovery for configuration: " + e
            );
        }

        try {
            gestalt.loadConfigs();
        } catch (GestaltException e) {
            throw new ConfigException("unable to load config: " + e, e);
        }
    }

    /**
     * Get a config for a path and a given class.
     * If the config is missing or invalid it will return the default value.
     *
     * @param path       path to get the config for. The path is not case sensitive.
     * @param defaultVal the default value to return if the config is invalid.
     * @param klass      class to get the class for.
     * @param <T>        type of class to get.
     * @return the configuration, or the default if the configuration is not found.
     */
    public <T> T getConfig(String path, T defaultVal, Class<T> klass) {
        return gestalt.getConfig(path, defaultVal, klass);
    }

    /**
     * Get a config for a path and a given class.
     * If the config is missing or invalid it will return null.
     *
     * @param path       path to get the config for. The path is not case sensitive.
     * @param klass      class to get the class for.
     * @param <T>        type of class to get.
     * @return the configuration, or the default if the configuration is not found.
     */
    public <T> T getConfig(String path, Class<T> klass) throws ConfigException {
        try {
            return gestalt.getConfig(path, klass);
        } catch (GestaltException e) {
            throw new ConfigException(e.toString());
        }
    }

    /**
     * Get a config for a path and a given TypeCapture.
     *
     * @param path  path to get the config for. The path is not case sensitive.
     * @param klass TypeCapture to get the class for.
     * @param <T>   type of class to get.
     * @return the configuration.
     * @throws GestaltException any errors such as if there are no configs.
     */
    public <T> T getConfig(String path, TypeCapture<T> klass)
        throws ConfigException {
        try {
            return gestalt.getConfig(path, klass);
        } catch (GestaltException e) {
            throw new ConfigException(e.toString());
        }
    }

    public BugFixes toBugFixes() throws ConfigException {
        try {
            return new BugFixes(
                gestalt.getConfig("bugfixes.weighting", Double.class),
                gestalt.getConfig("bugfixes.providedPassing", Double.class),
                gestalt.getConfig("bugfixes.providedFailing", Double.class)
            );
        } catch (GestaltException e) {
            throw new ConfigException(e.toString());
        }
    }

    public Submission toSubmission() throws ConfigException {
        try {
            return new Submission(
                gestalt.getConfig("submission.path", String.class),
                gestalt.getConfig(
                    "submission.classPath",
                    new TypeCapture<List<String>>() {}
                )
            );
        } catch (GestaltException e) {
            throw new RuntimeException(e);
        }
    }

    public Solution toSolution() throws ConfigException {
        try {
            return new Solution(
                gestalt.getConfig("solution.path", String.class),
                gestalt.getConfig(
                    "solution.classPath",
                    new TypeCapture<List<String>>() {}
                )
            );
        } catch (GestaltException e) {
            throw new RuntimeException(e);
        }
    }
}
