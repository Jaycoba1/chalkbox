package chalkbox.stages.pracdemos;

import chalkbox.api.common.java.JUnitIndividualResult;
import chalkbox.api.common.java.JUnitRunner;
import chalkbox.config.Config;
import chalkbox.config.ConfigException;
import chalkbox.source.Solution;
import chalkbox.source.Submission;
import chalkbox.stages.*;
import chalkbox.stages.conformance.SourceLoader;
import chalkbox.stages.functionality.ClassResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * The practical demonstration stage differs from the Functionality stage
 * in that test classes are read from a tasks file included in the submission
 * as each student will have a different subset of test classes.
 */
// TODO: This should really share as much of Functionality as possible
@RegisterStage
public class PracDemo
    extends BaseStage
    implements SubmissionAndSolutionStage, StageProducer {

    public static final String name = "PracDemo";

    private double maxScore;
    private boolean showPassing;
    private boolean allVisible;

    public PracDemo() {
        super("PracDemo");
    }

    public PracDemo(double maxScore, boolean showPassing, boolean allVisible) {
        this();
        this.maxScore = maxScore;
        this.showPassing = showPassing;
        this.allVisible = allVisible;
    }

    @Override
    public Stage build(Config config) throws ConfigException {
        return new PracDemo(
            config.getConfig("pracdemo.weighting", 100.0, Double.class),
            config.getConfig("pracdemo.showPassing", true, Boolean.class),
            config.getConfig("pracdemo.allVisible", false, Boolean.class)
        );
    }

    /**
     * Run the tests on a submission.
     * <p>
     * If there were issues compiling the sample solution or the tests, or
     * the submission did not compile successfully, no action is taken.
     * <p>
     * Uses a JUnit listener to observe the passed/failed tests for each test
     * class. One Gradescope test is created for each JUnit test method, with
     * a mark of zero if the test failed, or a mark of
     * <code>stageWeighting / numTests</code> if the test passed, where
     * <code>stageWeighting</code> is the number of marks allocated to this
     * stage, and <code>numTests</code> is the total number of JUnit test
     * methods in all test classes.
     */
    @Override
    public StageResult run(Submission submission, Solution solution)
        throws StageException {
        // Compile the solution, tests and the submission
        try {
            var compilation = solution.compileSrc();
            if (!compilation.success()) {
                throw new StageException(
                    "Unable to compile solution: " + compilation.output()
                );
            }
            compilation = solution.compileTest();
            if (!compilation.success()) {
                throw new StageException(
                    "Unable to compile tests: " + compilation.output()
                );
            }
            compilation = submission.compileSrc();
            if (!compilation.success()) {
                throw new StageException(
                    "Unable to compile submission: " + compilation.output()
                );
            }
        } catch (IOException e) {
            throw new StageException(e);
        }

        List<String> tests = null;
        try {
            tests = solution.getTestClasses();
        } catch (IOException e) {
            throw new StageException(e.toString());
        }

        // Run tests against the solution
        var classPath =
            solution.getClassPath() +
            File.pathSeparator +
            solution.getSrcBuildPath() +
            File.pathSeparator +
            solution.getTestBuildPath();
        var baselineResults = this.runTests(tests, classPath);

        // Path contains dependencies and the compile submission
        classPath =
        solution.getClassPath() +
        File.pathSeparator +
        submission.getSrcBuildPath() +
        File.pathSeparator +
        solution.getTestBuildPath();
        var submissionResults = this.runTests(tests, classPath);

        var totalNumTests = 0;
        var innerResults = new ArrayList<Result>();
        var classResults = new ArrayList<ClassResult>();

        List<String> testNames;
        try {
            Path taskFile = Path.of(submission.getBasePath(), "tasks");
            testNames = Files.readAllLines(taskFile);
        } catch (IOException e) {
            throw new StageException("Unable to find tasks file");
        }
        for (String className : testNames) {
            className = "demos." + className + "Test";

            int classPassing = 0;

            // Use test summaries to collect information even if test fails to compile
            var classTests = baselineResults.get(className).size();
            var classWeighting = baselineResults
                .get(className)
                .getFirst()
                .classWeight();

            for (JUnitIndividualResult unit : submissionResults.get(
                className
            )) {
                var isPassing = unit.passes() == 1;
                var visibility = allVisible
                    ? Visibility.VISIBLE
                    : unit.visibility();
                var unitResult = new Result("Functionality: " + unit.name())
                    .setVisibility(visibility)
                    .setStatus(isPassing ? Status.PASSED : Status.FAILED);

                if (!isPassing || showPassing) {
                    unitResult.appendOutput(
                        isPassing
                            ? "✅ Test scenario passes\n"
                            : "❌ Test scenario fails\n"
                    );

                    // Get Test class JavaDoc
                    var testDescription = getTestJavaDoc(
                        solution.getTestBuildPath(),
                        className,
                        unit.name()
                    );
                    if (!testDescription.isEmpty()) {
                        unitResult.appendOutput("### Scenario\n");
                        unitResult.appendOutput(testDescription);
                    }

                    if (!isPassing) {
                        unitResult.appendOutput("### Details\n");
                        unitResult.appendOutput(unit.output());
                    }
                }

                var testMultiplier = (Integer) unit.weight();
                // e.g. a test worth 5 "units" will increase the total number of tests by 5
                totalNumTests += testMultiplier;
                innerResults.add(unitResult);
                classPassing += unit.passes() == 1 ? 1 : 0;
            }
            classResults.add(
                new ClassResult(
                    className,
                    classTests,
                    classPassing,
                    classWeighting,
                    submissionResults.get(className).size()
                )
            );
        }

        if (totalNumTests == 0) {
            // todo(mh): Do something better here
            return null;
        }

        double total = 0;
        double possible = 0;
        var table = new StringBuilder(
            "| TestClass | Weighting | Passing Tests | Total |"
        );
        table.append(
            "\n| ----------- | ----------- | ----------- | ----------- |\n"
        );
        for (var classResult : classResults) {
            if (classResult.count() <= 0) {
                continue;
            }
            double score =
                (classResult.passing() / (float) classResult.count()) *
                classResult.weight();
            table
                .append("| ")
                .append(classResult.name())
                .append(" | ")
                .append(classResult.weight())
                .append(" | ")
                .append(classResult.passing())
                .append("/")
                .append(classResult.count())
                .append(" | ")
                .append(String.format("%.3f", score))
                .append("|\n");
            total += score;
            possible += classResult.weight();
        }
        double scaled = Math.ceil((total / possible) * maxScore);

        //var equation = "\n$$\n\\dfrac{" + String.format("%.3f", total) + "}{" + possible + "} \\times " + maxScore + " = " + scaled + "\n$$";
        var equation = "\n$$sum = " + total + "$$";
        var overview = new Result(name);
        overview
            .setMaxScore(maxScore)
            .setScore(total)
            .appendOutput(table + equation)
            .setOutputFormat("md")
            .setVisibility(Visibility.AFTER_PUBLISHED);

        return new StageResult(overview, innerResults);
    }

    private Map<String, List<JUnitIndividualResult>> runTests(
        List<String> tests,
        String classPath
    ) {
        var collection = new HashMap<String, List<JUnitIndividualResult>>();
        for (String className : tests) {
            // Ignore any that dont end in TEST
            if (!className.endsWith("Test")) {
                continue;
            }

            var results = JUnitRunner.runTests(className, classPath);
            if (results.isEmpty()) {
                continue;
            }
            results.sort(Comparator.comparing(JUnitIndividualResult::name));
            collection.put(className, results);
        }
        return collection;
    }

    private String getTestJavaDoc(
        String folder,
        String className,
        String methodName
    ) {
        try {
            var testDescription = new StringBuilder();
            var javaDoc = new SourceLoader(folder).getTestJavadoc(className);
            for (var method : javaDoc.getMethods()) {
                if (method.getName().equals(methodName.split("\\.")[1])) {
                    testDescription.append(method.getComment()).append("\n");
                }
            }
            return testDescription.toString();
        } catch (IOException ignored) {
            // Do Nothing
        }
        return "";
    }
}
