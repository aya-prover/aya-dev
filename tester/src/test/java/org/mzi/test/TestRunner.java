// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.test;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mzi.api.Global;
import org.mzi.cli.CompilerFlags;
import org.mzi.cli.SingleFileCompiler;
import org.mzi.cli.StreamReporter;
import org.mzi.tyck.error.CountingReporter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
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
  void runAllMziTests() throws IOException {
    var testSourceDir = Paths.get("src", "test", "mzi");
    runDir(testSourceDir.resolve("success"), true);
    runDir(testSourceDir.resolve("failure"), false);
  }

  private void runDir(@NotNull Path path, boolean expectSuccess) throws IOException {
    System.out.println(":: Running tests under " + path.toAbsolutePath());
    assertTrue(path.toFile().isDirectory(), "should be a directory");

    Files.walk(path)
      .filter(Files::isRegularFile)
      .filter(f -> f.getFileName().toString().endsWith(".mzi"))
      .forEach(file -> runFile(file, expectSuccess));
  }

  private void runFile(@NotNull Path file, boolean expectSuccess) {
    var expectedOutFile = file.resolveSibling(file.getFileName() + ".txt");

    var hookOut = new ByteArrayOutputStream();
    final var reporter = new CountingReporter(new StreamReporter(file, new PrintStream(hookOut)));

    try {
      new SingleFileCompiler(reporter, file)
        .compile(CompilerFlags.asciiOnlyFlags());
    } catch (IOException e) {
      fail("error reading file " + file.toAbsolutePath());
    }

    if (!Files.exists(expectedOutFile)) {
      if (expectSuccess) {
        assertTrue(reporter.isEmpty(), "The test case <" + file.getFileName() + "> should pass, but it fails.");
      } else {
        var workflowFile = file.resolveSibling(file.getFileName() + ".txt.todo");
        var outputToConfirm = hookOut.toString();
        try {
          Files.writeString(workflowFile, outputToConfirm);
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
          outputToConfirm
        );
        showStatus(file.getFileName().toString(), "todo generated");
      }
    } else try {
      var output = trimCRLF(hookOut.toString());
      var expected = trimCRLF(Files.readString(expectedOutFile));
      assertEquals(expected, output, file.getFileName().toString());
      showStatus(file.getFileName().toString(), "success");
    } catch (IOException e) {
      fail("error reading file " + expectedOutFile.toAbsolutePath());
    }
  }

  private void showStatus(String testName, String status) {
    System.out.println(testName + " ---> " + status);
  }

  private String trimCRLF(String string) {
    return string.replaceAll("\\r\\n?", "\n");
  }
}
