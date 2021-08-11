// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.test;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.error.StreamReporter;
import org.aya.cli.CompilerFlags;
import org.aya.cli.SingleFileCompiler;
import org.aya.core.def.PrimDef;
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

import static org.junit.jupiter.api.Assertions.*;

public class TestRunner {
  public static final @NotNull Path TEST_SOURCE_DIR = Paths.get("src", "test", "resources").toAbsolutePath();
  public static @NotNull SourceFileLocator LOCATOR = new SourceFileLocator() {
  };

  @BeforeAll public static void startDash() {
    PrimDef.PrimFactory.INSTANCE.clear();
  }

  @Test void runAllAyaTests() throws IOException {
    runDir(TEST_SOURCE_DIR.resolve("success"), true);
    runDir(TEST_SOURCE_DIR.resolve("failure"), false);
  }

  private void runDir(@NotNull Path path, boolean expectSuccess) throws IOException {
    System.out.println(":: Running tests under " + path.toAbsolutePath());
    assertTrue(path.toFile().isDirectory(), "should be a directory");

    Files.walk(path)
      .filter(Files::isRegularFile)
      .filter(f -> f.getFileName().toString().endsWith(".aya"))
      .forEach(file -> runFile(file, expectSuccess));
  }

  private void runFile(@NotNull Path file, boolean expectSuccess) {
    try {
      var hookOut = new ByteArrayOutputStream();
      var reporter = new CountingReporter(new StreamReporter(new PrintStream(
        hookOut, true, StandardCharsets.UTF_8)));

      System.out.print(file.getFileName() + " ---> ");
      new SingleFileCompiler(reporter, LOCATOR, null)
        .compile(file, new CompilerFlags(CompilerFlags.Message.ASCII, false, null, ImmutableSeq.of()));

      postRun(file, expectSuccess, hookOut.toString(StandardCharsets.UTF_8), reporter);
    } catch (IOException e) {
      fail("error reading file " + file.toAbsolutePath());
    }
  }

  private void postRun(@NotNull Path file, boolean expectSuccess, String output, CountingReporter reporter) {
    var expectedOutFile = file.resolveSibling(file.getFileName() + ".txt");
    if (Files.exists(expectedOutFile)) {
      checkOutput(file, expectedOutFile, output);
      System.out.println("success");
    } else {
      if (expectSuccess) {
        if (reporter.isEmpty()) {
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
    var workflowFile = testFile.resolveSibling(testFile.getFileName() + ".txt.todo");
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
      var output = trimCRLF(hookOut);
      var expected = instantiateVars(testFile, trimCRLF(Files.readString(expectedOutFile, StandardCharsets.UTF_8)));
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

  private String trimCRLF(String string) {
    return string.replaceAll("\\r\\n?", "\n");
  }
}
