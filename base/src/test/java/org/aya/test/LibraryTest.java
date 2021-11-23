// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import org.aya.cli.library.LibraryCompiler;
import org.aya.util.FileUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

public class LibraryTest {
  @Test public void test() throws IOException {
    compile();
    // Run twice, the second time should load the cache directly.
    compile();
  }

  public static final Path DIR = TestRunner.DEFAULT_TEST_DIR.resolve("success");

  public static void main(String... args) throws IOException {
    clear();
    compile();
  }

  private static void compile() throws IOException {
    LibraryCompiler.compile(ThrowingReporter.INSTANCE, TestRunner.flags(), DIR);
  }

  @BeforeAll private static void clear() throws IOException {
    FileUtil.deleteRecursively(DIR.resolve("build"));
  }
}
