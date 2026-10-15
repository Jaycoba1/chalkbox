package chalkbox.stages.markingaid.customspotbugs;

import chalkbox.api.common.Execution;
import chalkbox.api.common.ProcessExecution;
import chalkbox.config.Config;
import chalkbox.config.ConfigException;
import chalkbox.source.Submission;
import chalkbox.stages.*;
import com.google.common.flogger.FluentLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.github.gestalt.config.exceptions.GestaltException;
import org.github.gestalt.config.reflect.TypeCapture;

/**
 * Processor to execute the Checkstyle tool on the submission.
 */
@RegisterStage
public class CustomSpotBugs
        extends BaseStage
        implements SubmissionOnlyStage, StageProducer {

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private String jar;
    private List<String> dependencies;
    private List<String> pluginsToInclude;
    private String bugsToIncludeFile;

    public CustomSpotBugs() {
        super("Custom Spotbugs Code Analysis");
    }

    /**
     * Sets up the Checkstyle stage ready to process a submission.
     */
    public CustomSpotBugs(
            String jar,
            List<String> dependencies,
            String bugsToIncludeFile,
            List<String> pluginsToInclude
    ) {
        this();
        this.jar = jar;
        this.dependencies = dependencies;
        this.bugsToIncludeFile = bugsToIncludeFile;
        this.pluginsToInclude = pluginsToInclude;
    }

    public StageResult run(Submission submission) throws StageException {
        var result = new Result(getName()).setOutputFormat("md").setVisibility(Visibility.HIDDEN);

        try {
            var compilation = submission.compileSrc();
            if (!compilation.success()) {
                result.appendOutput(
                        "Unable to compile: " + compilation.output()
                );
                return StageResult.fromOverview(result);
            }
        } catch (IOException e) {
            result.appendOutput(
                    "Submission did not compile, no custom spotbugs feedback provided"
            );
            result.appendOutput(e.toString());
            return StageResult.fromOverview(result);
        }
        logger
                .atInfo()
                .log(
                        "Filepath for the custom spot bugs tool: %s",
                        jar
                );

        List<String> processArgs = new ArrayList<>();
        processArgs.add("java");
        processArgs.add("-jar");
        processArgs.add(jar);

        if (pluginsToInclude != null && !pluginsToInclude.isEmpty()) {
            processArgs.add("-pluginList");
            processArgs.add(
                    String.join(File.pathSeparator, pluginsToInclude)
            );
        }

        if (bugsToIncludeFile != null && bugsToIncludeFile != "") {
            processArgs.add("-include");
            processArgs.add(bugsToIncludeFile);
        }

        if (dependencies != null && !dependencies.isEmpty()) {
            processArgs.add("-auxclasspath");
            processArgs.add(
                    dependencies.stream()
                            .map(Paths::get)
                            .map(Path::toString)
                            .collect(Collectors.joining(File.pathSeparator))
            );
        }

        // project to analyse
        processArgs.add(submission.getSrcBuildPath().toString());

        logger
                .atInfo()
                .log("Running Custom spotbugs %s", String.join(" ", processArgs));

        // todo(mh): Change this to # * timeunit.Seconds or likewise
        ProcessExecution process = null;
        try {
            process =
                    Execution.runProcess(200000, processArgs.toArray(String[]::new));
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        var spotbugsError = process.getError();
        if (!spotbugsError.isEmpty()) {
            throw new StageException(spotbugsError);
        }

        var checkstyleOutput = process.getOutput();

        var formattedOutput = Arrays.stream(checkstyleOutput.split("\n"))
                .filter(n ->
                        !n.contains("Starting audit") && !n.contains("Audit done")
                )
                .filter(n -> !n.contains("skip this detector"))
                .map(n -> n.replace(submission.getBasePath(), ""))
                .map(n -> n.replaceAll("[ \t]+", " ").strip())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(n -> n.replaceFirst("^\\S+\\s+\\S+\\s+\\S+:\\s*", ""))
                .map(n -> n.replaceFirst("\\s+At\\s+.*$", ""))
                .collect(Collectors.joining("\n"));

        result.appendOutput(
                    formattedOutput
        );
        return StageResult.fromOverview(result);
    }


    private <T> T getConfigOrNull(Config config, String path, Class<T> klass) {
        try {
            return config.getConfig(path, klass);
        } catch (ConfigException e) {
            return null;
        }
    }

    private <T> T getConfigOrNull(Config config, String path, TypeCapture<T> klass) {
        try {
            return config.getConfig(path, klass);
        } catch (ConfigException e) {
            return null;
        }
    }

    @Override
    public Stage build(Config config) {

        try {
            bugsToIncludeFile =
                    config.getConfig("markingAidSpotBugs.include", String.class);
        } catch (ConfigException e) {
            bugsToIncludeFile = null;
        }

        return new CustomSpotBugs(
                config.getConfig("markingAidSpotBugs.jar", String.class),
                config.getConfig("submission.classPath",
                        new TypeCapture<List<String>>() {}
                ),
                getConfigOrNull(config, "markingAidSpotBugs.include", String.class),
                getConfigOrNull(config, "markingAidSpotBugs.plugins",
                        new TypeCapture<List<String>>() {}
                )
        );
    }
}
