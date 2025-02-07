# Hacking Aya

> [!IMPORTANT]
>
> Since you need [Java 22] to set this project up, in case your choice
> of IDE is IntelliJ IDEA, version 2024.2 or higher is required.

We use gradle to build the compiler. It comes with a wrapper script (`gradlew` or `gradlew.bat` in the root of the
repository) which downloads appropriate version of gradle automatically if you have JDK installed.

## Common Gradle Tasks

All gradle tasks are case-insensitive.
You may also use the camelCase shorthand if there is no ambiguity, for example `fatJar` can be `fJ`,
`testCodeCoverageReport` can be `tCCR`, etc.

| Command                         | Description                                                                                          |
|:--------------------------------|:-----------------------------------------------------------------------------------------------------|
| `./gradlew :cli-console:fatJar` | build a jar file which includes all the dependencies which can be found at `cli-console/build/libs`. |
| `./gradlew install`             | build the jlink image to the directory specified by `installDir` in `gradle.properties`.             |
| `./gradlew test`                | run all tests.                                                                                       |
| `./gradlew showCCR`             | run all tests with coverage and, if on Windows/Linux, display the coverage report.                   |

On Windows with cmd.exe, you may replace `./gradlew` with `gradlew` or `.\gradlew`,
but with powershell you should use `./gradlew`.

To see the command line options of the application, run `java -jar [file name].jar --help`
after running the `fatJar` task.

## Developing in IntelliJ IDEA

Here's an [instruction](https://www.jetbrains.com/help/idea/gradle.html)
on how to work with gradle projects in IntelliJ IDEA.

You may also need the following plugins:
+ Gradle -- bundled plugins, needed for building the project
+ [Grammar-Kit](https://plugins.jetbrains.com/plugin/6606) for editing the parser
+ [Kotlin](https://plugins.jetbrains.com/plugin/6954) for editing the build scripts
+ [IntelliJ Aya](https://github.com/aya-prover/intellij-aya) (unpublished yet) for editing Aya code

## More information

There is the [note](../note) directory, which contains some development notes about naming conventions,
specification of certain subroutines, literature review of certain features of existing proof assistants,
things to keep in mind when working on parts of the project, etc.
Unfortunately, most of them are in Chinese, but you can ask a Chinese-speaking developer for translation.
