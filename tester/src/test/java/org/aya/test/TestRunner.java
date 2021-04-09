// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.test;

import org.aya.api.Global;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.Problem;
import org.aya.api.error.StreamReporter;
import org.aya.cli.CompilerFlags;
import org.aya.cli.SingleFileCompiler;
import org.glavo.kala.collection.immutable.ImmutableSeq;
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
  @BeforeAll public static void enterTestMode() {
    Global.enterTestMode();
  }

  @Test
  void runAllAyaTests() throws IOException {
    var testSourceDir = Paths.get("src", "test", "aya");
    runDir(testSourceDir.resolve("success"), true);
    runDir(testSourceDir.resolve("failure"), false);
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
    var expectedOutFile = file.resolveSibling(file.getFileName() + ".txt");

    var hookOut = new ByteArrayOutputStream();
    final var reporter = new CountingReporter(new StreamReporter(
      file, Problem.readSourceCode(file), new PrintStream(hookOut, true, StandardCharsets.UTF_8)));

    System.out.print(file.getFileName() + " ---> ");

    try {
      new SingleFileCompiler(reporter, file, null)
        .compile(new CompilerFlags(CompilerFlags.Message.ASCII, false, null, ImmutableSeq.of()));
    } catch (IOException e) {
      fail("error reading file " + file.toAbsolutePath());
    }

    if (Files.exists(expectedOutFile)) {
      checkOutput(file, expectedOutFile, hookOut.toString(StandardCharsets.UTF_8));
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
            hookOut);
          fail("The test case <" + file.getFileName() + "> should pass, but it fails.");
        }
      } else {
        System.out.println(); // add line break after `--->`
        generateWorkflow(file, expectedOutFile, hookOut.toString(StandardCharsets.UTF_8));
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
