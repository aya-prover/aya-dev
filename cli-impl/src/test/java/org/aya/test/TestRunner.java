// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import kala.collection.Seq;
import kala.collection.SeqView;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.test.fixtures.*;
import org.aya.util.FileUtil;
import org.aya.util.Global;
import org.aya.util.position.SourceFileLocator;
import org.aya.util.reporter.CountingReporter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestRunner {
  public static final @NotNull Path TEST_DIR = Paths.get("src", "test", "resources").toAbsolutePath();
  private static final @NotNull Path FIXTURE_DIR = TEST_DIR.resolve("negative");
  private static final @NotNull Path TMP_FILE = TEST_DIR.resolve("tmp.aya");
  public static final @NotNull SourceFileLocator LOCATOR = new SourceFileLocator() { };
  @BeforeAll public static void startDash() { Global.NO_RANDOM_NAME = true; }

  @Test public void negative() throws Exception {
    var toCheck = Seq.of(
      ParseError.class,
      ExprTyckError.class,
      GoalAndMeta.class,
      ScopeError.class,
      PatTyckError.class,
      OperatorError.class,
      TerckError.class,
      PatCohError.class,
      ClassError.class,
      TailRecError.class
    ).mapNotNullChecked(TestRunner::expectFixture);
    if (toCheck.isNotEmpty()) {
      new ProcessBuilder("git", "add", FIXTURE_DIR.toString())
        .inheritIO()
        .start()
        .waitFor();
    }
    for (var file : toCheck) {
      var name = file.toString();
      var proc = new ProcessBuilder("git", "diff", "--cached", name).start();
      var output = new String(proc.getInputStream().readAllBytes());
      assertTrue(output.isBlank(), output);
    }
    Files.deleteIfExists(TMP_FILE);
  }

  interface Rabbit {
  }
  @Test public void playground() throws IOException, IllegalAccessException {
    // System.out.println(runFixtureClass(Rabbit.class));
  }

  static void main() throws Exception {
    TestRunner.startDash();
    new TestRunner().negative();
  }

  private static String replaceFileName(String template) {
    return template.replace(TMP_FILE.toString(), "$FILE");
  }

  /// @return not null for a file to check, null if we're good to go
  private static Path expectFixture(Class<?> fixturesClass) throws IllegalAccessException, IOException, InterruptedException {
    var result = replaceFileName(runFixtureClass(fixturesClass));
    var expectedOutFile = FIXTURE_DIR.resolve(fixturesClass.getSimpleName() + ".txt");
    if (Files.exists(expectedOutFile)) {
      writeWorkflow(expectedOutFile, result);
      return expectedOutFile;
    } else {
      System.out.println(); // add line break before `NOTE`
      writeWorkflow(expectedOutFile, result);
      System.out.printf(Locale.getDefault(),
        """
          NOTE: write the following output to `%s`.
          ----------------------------------------
          %s
          ----------------------------------------
          """,
        expectedOutFile.getFileName(),
        result
      );
    }
    return null;
  }

  private static void writeWorkflow(Path expectedOutFile, String hookOut) {
    try {
      FileUtil.writeString(expectedOutFile, hookOut);
    } catch (IOException e) {
      fail("error generating todo file " + expectedOutFile.toAbsolutePath());
    }
  }

  private static String runFixtureClass(Class<?> fixturesClass)
    throws IllegalAccessException, IOException {
    try (var output = new ByteArrayOutputStream();
         var stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
      var reporter = CountingReporter.delegate(new StreamReporter(stream));
      var compiler = new SingleFileCompiler(reporter, flags(), LOCATOR);
      System.out.print("Running: ");
      for (var field : fixturesClass.getDeclaredFields()) {
        var name = field.getName();
        if (!name.startsWith("test")) continue;
        System.out.print(name.substring(4) + ", ");
        stream.println(name.substring(4) + ":");
        var code = (String) field.get(null);
        runSingleCase(code, compiler);
        compiler.collectingReporter.reset();
        compiler.countingReporter.clearCounts();
        stream.println();
      }
      System.out.println("Done!");
      return output.toString(StandardCharsets.UTF_8);
    }
  }

  private static void runSingleCase(String code, SingleFileCompiler compiler) throws IOException {
    Files.deleteIfExists(TMP_FILE);
    Files.createFile(TMP_FILE);
    Files.writeString(TMP_FILE, code);
    compiler.compile(TMP_FILE, null);
  }

  public static @NotNull CompilerFlags flags() {
    var modulePaths = SeqView.of(TEST_DIR.resolve("shared/src"));
    return new CompilerFlags(CompilerFlags.Message.ASCII,
      false, false, null, modulePaths, null);
  }
}
