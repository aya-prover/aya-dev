// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.test.fixtures.*;
import org.aya.util.FileUtil;
import org.aya.util.error.Global;
import org.aya.util.error.SourceFileLocator;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestRunner {
  public static final @NotNull Path DEFAULT_TEST_DIR = Paths.get("src", "test", "resources").toAbsolutePath();
  public static final @NotNull Path TMP_FILE = DEFAULT_TEST_DIR.resolve("tmp.aya");
  public static final @NotNull SourceFileLocator LOCATOR = new SourceFileLocator() { };
  @BeforeAll public static void startDash() { Global.NO_RANDOM_NAME = true; }

  @Test public void negative() throws Exception {
    Seq.of(
      ExprTyckError.class,
      GoalAndMeta.class,
      ScopeError.class,
      PatTyckError.class,
      OperatorError.class,
      TerckError.class,
      PatCohError.class
    ).forEachChecked(TestRunner::expectFixture);
    Files.deleteIfExists(TMP_FILE);
  }

  @Test public void playground() throws IOException {
    // runSingleCase(GoalAndMeta.testLiteralAmbiguous3, System.out);
  }

  public static void main(String... args) throws Exception {
    TestRunner.startDash();
    new TestRunner().negative();
  }

  private static String instantiateVars(String template) {
    return template.replace("$FILE", TMP_FILE.toString());
  }

  private static String instantiateHoles(String template) {
    return template.replace(TMP_FILE.toString(), "$FILE");
  }

  private static void checkOutput(Path expectedOutFile, String hookOut) {
    try {
      var output = StringUtil.convertLineSeparators(hookOut);
      var expected = instantiateVars(StringUtil.convertLineSeparators(
        Files.readString(expectedOutFile, StandardCharsets.UTF_8)));
      assertEquals(expected, output, TMP_FILE.getFileName().toString());
    } catch (IOException e) {
      fail(STR."error reading file \{expectedOutFile.toAbsolutePath()}");
    }
  }

  private static void expectFixture(Class<?> fixturesClass) throws IllegalAccessException, IOException {
    var result = runFixtureClass(fixturesClass);
    var expectedOutFile = DEFAULT_TEST_DIR
      .resolve("negative")
      .resolve(fixturesClass.getSimpleName() + ".txt");
    if (Files.exists(expectedOutFile)) {
      checkOutput(expectedOutFile, result);
      System.out.println("success");
    } else {
      System.out.println(); // add line break after `--->`
      generateWorkflow(expectedOutFile, result);
    }
  }

  private static void generateWorkflow(Path expectedOutFile, String hookOut) {
    hookOut = instantiateHoles(hookOut);
    try {
      FileUtil.writeString(expectedOutFile, hookOut);
    } catch (IOException e) {
      fail(STR."error generating todo file \{expectedOutFile.toAbsolutePath()}");
    }
    System.out.printf(Locale.getDefault(),
      """
        NOTE: write the following output to `%s`.
        ----------------------------------------
        %s
        ----------------------------------------
        """,
      expectedOutFile.getFileName(),
      hookOut
    );
  }

  private static String runFixtureClass(Class<?> fixturesClass)
    throws IllegalAccessException, IOException {
    try (var output = new ByteArrayOutputStream();
         var stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
      var reporter = CountingReporter.delegate(new StreamReporter(stream));
      var compiler = new SingleFileCompiler(reporter, flags(), LOCATOR);
      for (var field : fixturesClass.getDeclaredFields()) {
        var name = field.getName();
        if (!name.startsWith("test")) continue;
        System.out.println("Running " + name.substring(4));
        stream.println(name.substring(4) + ":");
        var code = (String) field.get(null);
        runSingleCase(code, compiler);
        compiler.reporter.clear();
        stream.println();
      }
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
    var modulePaths = ImmutableSeq.of(DEFAULT_TEST_DIR.resolve("shared/src"));
    return new CompilerFlags(CompilerFlags.Message.ASCII, false, false, null, modulePaths, null);
  }
}
