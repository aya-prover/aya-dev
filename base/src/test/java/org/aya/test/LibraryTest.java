// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.core.def.PrimDef;
import org.aya.util.FileUtil;
import org.aya.util.reporter.ThrowingReporter;
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
    new LibraryTest().test();
  }

  private static int compile() throws IOException {
    return LibraryCompiler.compile(new PrimDef.Factory(), ThrowingReporter.INSTANCE, TestRunner.flags(), CompilerAdvisor.onDisk(), DIR);
  }
}
