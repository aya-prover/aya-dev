// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.test;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mzi.api.Global;
import org.mzi.cli.CompilerFlags;
import org.mzi.cli.SingleFileCompiler;
import org.mzi.cli.StreamReporter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class TestRunner {
  @BeforeAll public static void enterTestMode() {
    Global.enterTestMode();
  }

  @Test
  void runAllMziTests() throws IOException {
    var testSourceDir = Paths.get("src", "test", "mzi");
    runDir(testSourceDir.resolve("success"));
    runDir(testSourceDir.resolve("fail"));
  }

  private void runDir(@NotNull Path path) throws IOException {
    System.out.println(":: Running tests under " + path.toAbsolutePath());
    assertTrue(path.toFile().isDirectory(), "should be a directory");

    Files.walk(path)
      .filter(Files::isRegularFile)
      .filter(f -> f.getFileName().toString().endsWith(".mzi"))
      .forEach(this::runFile);
  }

  private void runFile(@NotNull Path file) {
    var expectedOutFile = file.resolveSibling(file.getFileName() + ".txt");

    var hookOut = new ByteArrayOutputStream();

    try {
      new SingleFileCompiler(new StreamReporter(file, new PrintStream(hookOut)), file)
        .compile(CompilerFlags.asciiOnlyFlags());
    } catch (IOException e) {
      fail("error reading file " + file.toAbsolutePath());
    }

    try {
      var output = trimCRLF(hookOut.toString());
      var expected = trimCRLF(Files.readString(expectedOutFile));
      assertEquals(expected, output, file.getFileName().toString());
    } catch (IOException e) {
      fail("error reading file " + expectedOutFile.toAbsolutePath());
    }

    System.out.println(file.getFileName() + " ---> " + " success");
  }

  private String trimCRLF(String string) {
    return string.replaceAll("\\r\\n?", "\n");
  }
}
