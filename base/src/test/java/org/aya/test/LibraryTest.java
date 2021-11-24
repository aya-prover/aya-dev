// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import org.aya.cli.library.LibraryCompiler;
import org.aya.util.FileUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LibraryTest {
  @Test public void test() throws IOException {
    FileUtil.deleteRecursively(DIR.resolve("build"));
    // Full rebuild
    assertEquals(0, compile());
    FileUtil.deleteRecursively(DIR.resolve("build").resolve("out"));
    // The second time should load the cache of 'common'.
    assertEquals(0, compile());
    // The third time should do nothing.
    assertEquals(0, compile());
  }

  public static final Path DIR = TestRunner.DEFAULT_TEST_DIR.resolve("success");

  public static void main(String... args) throws IOException {
    FileUtil.deleteRecursively(DIR.resolve("build"));
    compile();
  }

  private static int compile() throws IOException {
    return LibraryCompiler.compile(ThrowingReporter.INSTANCE, TestRunner.flags(), DIR);
  }
}
