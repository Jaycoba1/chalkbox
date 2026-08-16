package chalkbox.stages.tlc;

import chalkbox.api.common.Execution;
import chalkbox.api.common.ProcessExecution;
import chalkbox.config.Config;
import chalkbox.config.ConfigException;
import chalkbox.source.Submission;
import chalkbox.stages.*;
import com.google.common.flogger.FluentLogger;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@RegisterStage
public class TLC
    extends BaseStage
    implements SubmissionOnlyStage, StageProducer {

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private String jar;
    private double weighting;
    private String configPath;
    private String tlaPath;

    private OutputStream outputOutputStream;

    public TLC() {
        super("TLC");
    }

    public TLC(
        String jar,
        double weighting,
        String configPath,
        String tlaPath
    ) {
        this();
        this.jar = jar;
        this.weighting = weighting;
        this.configPath = configPath;
        this.tlaPath = tlaPath;
    }

    @Override
    public StageResult run(Submission submission) throws StageException {
        var result = new Result(getName()).setOutputFormat("md");
        result.setMaxScore(weighting);

        var codePath = Path.of(submission.getBasePath(), tlaPath);
        if (Files.notExists(codePath)) {
            return StageResult.fromOverview(
                result
                    .setScore(0)
                    .setStatus(Status.FAILED)
                    .appendOutput(
                        "File `" + tlaPath + "` not found in submission"
                    )
            );
        }

        var tmpConfigPath = Path.of(
            submission.getBasePath(), configPath
        );

        List<String> processArgs = new ArrayList<>();
        processArgs.add("java");
        processArgs.add("-XX:+UseParallelGC");
        processArgs.add("-jar");
        processArgs.add(jar);
        processArgs.add("-config");
        processArgs.add(tmpConfigPath.toString());
        processArgs.add(codePath.toString());
        logger.atInfo().log("Running TLC %s", String.join(" ", processArgs));

        ProcessExecution process = null;
        try {
            process =
            Execution.runProcess(480000, processArgs.toArray(String[]::new));
        } catch (IOException | TimeoutException e) {
            throw new StageException(e);
        }

        var processError = process.getError();
        if (!processError.isEmpty()) {
            throw new StageException(processError);
        }

        var processOutput = process.getOutput();
        var formattedOutput = Arrays
            .stream(processOutput.split("\n"))
            //.filter(line -> !line.contains("Parsing file"))
            .map(n -> n.replace(submission.getBasePath(), ""))
            .collect(Collectors.joining("\n"));

        if (
            !processOutput.contains(
                "Model checking completed. No error has been found."
            )
        ) {
            result.appendOutput(
                """
                ❌ Model checking did not complete without error. Please refer to the output logs below. \

                =============
                """
            );
            result.appendOutput(formattedOutput);
            result.setScore(0);
            return StageResult.fromOverview(result);
        }

        result
            .appendOutput(
                """
                ✅ Model checking succeeded. Ensure that the properties you've specified are accurate and what you intended.

                =============
                """
            )
            .appendOutput(formattedOutput);
        result.setScore(weighting);
        return StageResult.fromOverview(result);
    }

    @Override
    public Stage build(Config config) throws ConfigException {
        return new TLC(
            config.getConfig("tlc.jar", String.class),
            config.getConfig("tlc.weighting", Double.class),
            config.getConfig("tlc.config", String.class),
            config.getConfig("tlc.source", String.class)
        );
    }
}
