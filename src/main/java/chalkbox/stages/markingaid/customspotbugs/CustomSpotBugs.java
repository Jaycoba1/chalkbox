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
    private List<String> plugins;
    private SpotBugsExclusions spotBugsExclusions;

    public CustomSpotBugs() {
        super("Custom Spotbugs Code Analysis");
    }

    /**
     * Sets up the Checkstyle stage ready to process a submission.
     */
    public CustomSpotBugs(
            String jar,
            List<String> plugins,
            SpotBugsExclusions spotBugsExclusions
    ) {
        this();
        this.jar = jar;
        this.plugins = plugins;
        this.spotBugsExclusions = spotBugsExclusions;
    }

    public StageResult run(Submission submission) throws StageException {
        var result = new Result(getName()).setOutputFormat("md");

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

        // If you want to add a new detector follow spotbugs instructors and create plugin and add to list
        if (!plugins.isEmpty()) {
            processArgs.add("-pluginList");
            processArgs.add(
                    String.join(File.pathSeparator, plugins)
            );
        }

        // add exclusion XML file
        if (!spotBugsExclusions.noExclusions()) {
            processArgs.add("-exclude");

            Path excludeFile;

            // todo: Ensure temporarily created xml file is destroyed
            try {
                excludeFile = Files.createTempFile(
                        "spotbugs-exclude-",
                        ".xml"
                );

                Files.writeString(
                        excludeFile,
                        generateExcludeXml(spotBugsExclusions)
                );
            } catch (IOException e) {
                throw new StageException(
                        "Failed to create SpotBugs exclusion filter: " + e.getMessage()
                );
            }
            processArgs.add(excludeFile.toString());
        }

        processArgs.add("-auxclasspath");
        processArgs.add(submission);
        // todo: Check if processArgs.add(-project) tag is needed
        processArgs.add(submission.getSrcBuildPath().toString());

        logger
                .atInfo()
                .log("Running Custom spotbugs %s", String.join(" ", processArgs));

        // todo(mh): Change this to # * timeunit.Seconds or likewise
        ProcessExecution process = null;
        try {
            process =
                    Execution.runProcess(20000, processArgs.toArray(String[]::new));
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        var checkstyleError = process.getError();
        if (!checkstyleError.isEmpty()) {
            throw new StageException(checkstyleError);
        }

        var checkstyleOutput = process.getOutput();

        var formattedOutput = Arrays
                .stream(checkstyleOutput.split("\n"))
                .filter(n ->
                        !n.contains("Starting audit") && !n.contains("Audit done")
                )
                .map(n -> n.replace("[WARN] ", "❌ "))
                .map(n -> n.replace(submission.getBasePath(), ""))
                .collect(Collectors.joining("\n"));

        result.appendOutput(
                String.format(
                        """
                        Following violation were found:
                        =============
                        %s
                        """,
                        formattedOutput
                )
        );
        return StageResult.fromOverview(result);
    }


    private String generateExcludeXml(SpotBugsExclusions exclusions) {
        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<FindBugsFilter>\n");

        generateClassExclusions(exclusions.classes(), xml);
        generatePatternExclusions(exclusions.patterns(), xml);
        generateCodeExclusions(exclusions.codes(), xml);
        generateCategoryExclusions(exclusions.categories(), xml);

        xml.append("</FindBugsFilter>\n");

        return xml.toString();
    }

    private void generateClassExclusions(
            List<String> classes,
            StringBuilder xml
    ) {
        for (String className : classes) {
            xml.append("""
                <Match>
                    <Class name="%s"/>
                </Match>
                """.formatted(className));
        }
    }

    private void generatePatternExclusions(
            List<String> patterns,
            StringBuilder xml
    ) {
        for (String pattern : patterns) {
            xml.append("""
                <Match>
                    <Bug pattern="%s"/>
                </Match>
                """.formatted(pattern));
        }
    }

    private void generateCodeExclusions(
            List<String> codes,
            StringBuilder xml
    ) {
        for (String code : codes) {
            xml.append("""
                <Match>
                    <Bug code="%s"/>
                </Match>
                """.formatted(code));
        }
    }

    private void generateCategoryExclusions(
            List<String> categories,
            StringBuilder xml
    ) {
        for (String category : categories) {
            xml.append("""
                <Match>
                    <Bug category="%s"/>
                </Match>
                """.formatted(category));
        }
    }

    /**
     * Helper method that is mainly used to extract configuration list else returns empty list mainly for spotbugs
     * filter file
     */
    private List<String> getConfigOrEmpty(
            Config config,
            String path
    ) {
        try {
            return config.getConfig(
                    path,
                    new TypeCapture<List<String>>() {}
            );
        } catch (ConfigException e) {
            return List.of();
        }
    }

    // todo: Figure out how to list excluded classes. Odds are this will be in out/and same structure as .java files
    @Override
    public Stage build(Config config) {
        return new CustomSpotBugs(
                config.getConfig("customSpotbugs.jar", String.class),
                config.getConfig("customSpotbugs.plugins",
                        new TypeCapture<List<String>>() {}
                        ),
                new SpotBugsExclusions(
                        getConfigOrEmpty(config, "codestyle.excluded"),
                        getConfigOrEmpty(config, "customSpotbugs.patterns"),
                        getConfigOrEmpty(config, "customSpotbugs.codes"),
                        getConfigOrEmpty(config, "customSpotbugs.categories")
                )
        );
    }
}
