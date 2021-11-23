// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import org.aya.cli.library.LibraryCompiler;
import org.aya.util.FileUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class LibraryTest {
  @Test public void test() throws IOException {
    main();
  }

  public static void main(String... args) throws IOException {
    var dir = TestRunner.DEFAULT_TEST_DIR.resolve("success");
    FileUtil.deleteRecursively(dir.resolve("build"));
    LibraryCompiler.compile(ThrowingReporter.INSTANCE, TestRunner.flags(), dir);
  }
}
