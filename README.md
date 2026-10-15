# ChalkBox ![Gradle Build](https://github.com/UQTools/chalkbox/workflows/Gradle%20Build/badge.svg)

![ChalkBox Logo](https://github.com/UQTools/chalkbox/raw/master/docs/_static/images/logo/chalkbox.png)

## Overview
ChalkBox is a tool for processing and marking Java based programming assignment
submissions for GradeScope.

Common use cases include running automated tests and static analysis tools on
submitted code.

### Key Features

- **Integrates with Gradescope**

  ChalkBox has been redesigned to work with the
  [Gradescope](https://www.gradescope.com/) autograder platform.
  ChalkBox runs on a single submission, so students can receive instant feedback
  every time they submit.

## Documentation

Javadoc for the source code of ChalkBox and guides for working with and
extending ChalkBox can be found at
[chalkbox.readthedocs.io](https://chalkbox.readthedocs.io).

## Stages

### Overview

### Ai

### Codestyle

### Conformance

### Functionality

### Testing

### Mutation

### Spotbugs



ChalkBox supports static analysis using [SpotBugs](https://spotbugs.github.io/), including both its default detectors and custom detectors implemented through SpotBugs plugins.

#### Configuration

SpotBugs is run through chalk box stage called `customSpotBugs`, which performs additional processing and formatting of the SpotBugs results before they are reported.

To configure SpotBugs, use the following files:

- **`config.yml`** — Add `customSpotBugs` to the configured stages and list the SpotBugs plugins to load.
- **`spot_bugs_include.xml`** — Specify the bug codes to include in the final results. This file supports both custom bug codes and bug codes from the default SpotBugs detectors. Add or remove entries to customise which detectors' findings are reported. You can also specify class names for which classes/packages to analyse. See SpotBugs filter files for more details. Can also expand on this with an exclude filter file in the future
- **`detector_config.yml`** — Configure the behaviour of supported custom detectors.

#### Adding a Custom SpotBugs Plugin

Custom detectors can be implemented by following the [SpotBugs plugin development guide](https://spotbugs.readthedocs.io/).

Once a plugin has been created:

1. **Package the plugin:** Build the plugin JAR using Maven.
2. **Add the JAR:** Place the generated JAR in the `lib` folder.
3. **Register the plugin:** Add the jar file name to `config.yml` so that `customSpotBugs` includes it in the SpotBugs command-line invocation when the stage runs.
4. **Include its bug codes:** Add the custom bug codes to `spot_bugs_include.xml` so that the corresponding findings are included in the results.
5. **Configure the detectors:** If the plugin supports configuration, update `detector_config.yml` as required.

#### Configuring Custom Detectors

Refer to `detector_config.yml` for documentation on configuring individual detectors. For example, `ModularWeightedCouplingDetector` supports configurable thresholds and other options that control how coupling is measured and reported.

#### Creating Variants of Existing Detectors

To create a separate variant of an existing detector, duplicate its implementation and give the detector a unique name (simply re-name the detector). Repackage the plugin using Maven, place the resulting JAR in the `lib` folder, and register the plugin in `config.yml`.

Ensure that any new bug codes are also added to `spot_bugs_include.xml`.   If the variant requires separate configuration, update `detector_config.yml` accordingly with the top level being the name of the detector.

This allows multiple versions of a detector to run with different configurations or detection rules.


## Copyright

ChalkBox is copyright Brae Webb, Emily Bennett, Ella de Lore, Max Miller,
Millie Hughes and Nicholas Lambourne.

The ChalkBox logo is copyright Anna Truffet.
