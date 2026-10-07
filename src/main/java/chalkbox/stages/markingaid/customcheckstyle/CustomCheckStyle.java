package chalkbox.stages.markingaid.customcheckstyle;

import chalkbox.api.common.Execution;
import chalkbox.api.common.ProcessExecution;
import chalkbox.config.Config;
import chalkbox.source.Submission;
import chalkbox.stages.*;
import com.google.common.flogger.FluentLogger;
import java.io.IOException;
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
public class CustomCheckStyle
        extends BaseStage
        implements SubmissionOnlyStage, StageProducer {

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private String jar;
    private String config;
    private List<String> excludedFiles;

    public CustomCheckStyle() {
        super("Check Style Code Analysis");
    }

    /**
     * Sets up the Checkstyle stage ready to process a submission.
     */
    public CustomCheckStyle(
            String jar,
            String config,
            List<String> excludedFiles
    ) {
        this();
        this.jar = jar;
        this.config = config;
        this.excludedFiles = excludedFiles;
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
                    "Submission did not compile, no custom checkstyle feedback provided"
            );
            result.appendOutput(e.toString());
            return StageResult.fromOverview(result);
        }
        logger
                .atInfo()
                .log(
                        "Filepath for the custom checkstyle tool: %s with config %s",
                        jar,
                        config
                );

        var path = Paths.get(config);
        var configPath = path.toAbsolutePath().getParent().toString();

        List<String> processArgs = new ArrayList<>();
        processArgs.add("java");
        processArgs.add(
                String.format("-Dcheckstyle.config.path=%s", configPath)
        );
        processArgs.add("-jar");
        processArgs.add(jar);
        processArgs.add("-c");
        processArgs.add(config);
        processArgs.addAll(
                generateExcludedArgs(excludedFiles, submission.getSrcFolder())
        );
        processArgs.add(submission.getSrcFolder());
        logger
                .atInfo()
                .log("Running Custom CheckStyle %s", String.join(" ", processArgs));

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
        if (!checkstyleOutput.contains("Audit done.")) {
            result.appendOutput(
                    """
                    ❌ Custom checkstyle did not exit successfully. \
                    This can indicate a syntax error or missing files.\
    
                    ### Details
    
                    """
            );
            result.appendOutput(checkstyleOutput);
            return StageResult.fromOverview(result);
        }

        var formattedOutput = Arrays
                .stream(checkstyleOutput.split("\n"))
                .filter(n ->
                        !n.contains("Starting audit") && !n.contains("Audit done")
                )
                .map(n -> n.replace("[WARN] ", "❌ "))
                .map(n -> n.replace(submission.getBasePath(), ""))
                .collect(Collectors.joining("\n"));

        result.appendOutput(formattedOutput);
        return StageResult.fromOverview(result);
    }

    /**
     * Transforms the given list of excluded directories to a list of command
     * line arguments for the Checkstyle tool.
     *
     * @param excluded list of excluded paths
     * @return list of command line arguments specifying excluded paths
     */
    private List<String> generateExcludedArgs(
            List<String> excluded,
            String srcFolder
    ) {
        List<String> args = new ArrayList<>();
        for (String s : excluded) {
            args.add("-e");
            args.add(Path.of(srcFolder, s).toString());
        }
        return args;
    }

    @Override
    public Stage build(Config config) {
        return new CustomCheckStyle(
                config.getConfig("markingAidCheckstyle.jar", String.class),
                config.getConfig("markingAidCheckstyle.config", String.class),
                config.getConfig(
                        "codestyle.excluded",
                        new TypeCapture<List<String>>() {}
                )
        );
    }
}
