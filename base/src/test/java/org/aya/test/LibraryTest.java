// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.LibraryCompiler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;

public class LibraryTest {
  @Test public void test() throws IOException {
    main();
  }

  public static void main(String... args) throws IOException {
    var dir = TestRunner.DEFAULT_TEST_DIR.resolve("success");
    try (var walk = Files.walk(dir.resolve("build"))) {
      walk.sorted(Comparator.reverseOrder())
        .collect(ImmutableSeq.factory())
        .forEachChecked(Files::delete);
    }
    LibraryCompiler.compile(ThrowingReporter.INSTANCE, TestRunner.flags(), dir);
  }
}
