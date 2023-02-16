// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.generic.util.AyaFiles;
import org.aya.prelude.GeneratedVersion;
import org.aya.util.StringUtil;
import org.aya.util.error.Global;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.reporter.CountingReporter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class TestRunner {
  public static final @NotNull Path DEFAULT_TEST_DIR = Paths.get("src", "test", "resources").toAbsolutePath();
  public static final @NotNull SourceFileLocator LOCATOR = new SourceFileLocator() {
  };

  @BeforeAll public static void startDash() {
    Global.NO_RANDOM_NAME = true;
  }

  @AfterAll public static void exit() {
    Global.reset();
  }

  /**
   * Run all tests under DEFAULT_TEST_DIR, using JUnit.
   * For running single test, use the main() function below.
   */
  @Test void runAllAyaTests() {
    System.out.println("Aya Test Runner: Running for commit " + GeneratedVersion.COMMIT_HASH);
    runDir(DEFAULT_TEST_DIR.resolve("failure"), false);
  }

  public static void main(String[] args) {
    TestRunner.startDash();
    var runner = new TestRunner();
    Arrays.stream(args).map(Paths::get).forEach(path -> {
      if (Files.isRegularFile(path)) runner.runFile(path, true);
      else if (Files.isDirectory(path)) runner.runDir(path, true);
      else if (Files.notExists(path)) fail("Test target not found: " + path.toAbsolutePath());
      else fail("Unsupported test target: " + path.toAbsolutePath());
    });
    TestRunner.exit();
  }

  private void runDir(@NotNull Path path, boolean expectSuccess) {
    System.out.println(":: Running tests under " + path.toAbsolutePath());
    assertTrue(path.toFile().isDirectory(), "should be a directory");

    var source = AyaFiles.collectAyaSourceFiles(path);
    assertTrue(source.isNotEmpty(), "should have at least one .aya file");
    source.forEach(file -> runFile(file, expectSuccess));
  }

  private void runFile(@NotNull Path file, boolean expectSuccess) {
    try {
      var hookOut = new ByteArrayOutputStream();
      var reporter = CountingReporter.delegate(new StreamReporter(new PrintStream(
        hookOut, true, StandardCharsets.UTF_8)));

      System.out.print(file.getFileName() + " ---> ");
      new SingleFileCompiler(reporter, LOCATOR, null)
        .compile(file, flags(), null);

      postRun(file, expectSuccess, hookOut.toString(StandardCharsets.UTF_8), reporter);
    } catch (IOException e) {
      fail("error reading file " + file.toAbsolutePath());
    }
  }

  public static @NotNull CompilerFlags flags() {
    var modulePaths = ImmutableSeq.of(
      DEFAULT_TEST_DIR.resolve("success/common/src"));
    return new CompilerFlags(CompilerFlags.Message.ASCII, false, false, null, modulePaths, null);
  }

  private void postRun(@NotNull Path file, boolean expectSuccess, String output, CountingReporter reporter) {
    var expectedOutFile = file.resolveSibling(file.getFileName() + ".txt");
    if (Files.exists(expectedOutFile)) {
      checkOutput(file, expectedOutFile, output);
      System.out.println("success");
    } else {
      if (expectSuccess) {
        if (reporter.noError()) {
          System.out.println("success");
        } else {
          System.out.println(); // add line break after `--->`
          System.err.printf(Locale.getDefault(),
            """
              ----------------------------------------
                %s
              ----------------------------------------
              """,
            output);
          fail("The test case <" + file.getFileName() + "> should pass, but it fails.");
        }
      } else {
        System.out.println(); // add line break after `--->`
        generateWorkflow(file, expectedOutFile, output);
      }
    }
  }

  private void generateWorkflow(@NotNull Path testFile, Path expectedOutFile, String hookOut) {
    hookOut = instantiateHoles(testFile, hookOut);
    var workflowFile = testFile.resolveSibling(testFile.getFileName() + ".txt");
    try {
      Files.writeString(workflowFile, hookOut, StandardCharsets.UTF_8);
    } catch (IOException e) {
      fail("error generating todo file " + workflowFile.toAbsolutePath());
    }
    System.out.printf(Locale.getDefault(),
      """
        NOTE: write the following output to `%s`
        Move it to `%s` to accept it as correct.
        ----------------------------------------
        %s
        ----------------------------------------
        """,
      workflowFile.getFileName(),
      expectedOutFile.getFileName(),
      hookOut
    );
  }

  private void checkOutput(@NotNull Path testFile, Path expectedOutFile, String hookOut) {
    try {
      var output = StringUtil.trimCRLF(hookOut);
      var expected = instantiateVars(testFile, StringUtil.trimCRLF(Files.readString(expectedOutFile, StandardCharsets.UTF_8)));
      assertEquals(expected, output, testFile.getFileName().toString());
    } catch (IOException e) {
      fail("error reading file " + expectedOutFile.toAbsolutePath());
    }
  }

  private String instantiateVars(@NotNull Path testFile, String template) {
    return template.replace("$FILE", testFile.toString());
  }

  private String instantiateHoles(@NotNull Path testFile, String template) {
    return template.replace(testFile.toString(), "$FILE");
  }
}
