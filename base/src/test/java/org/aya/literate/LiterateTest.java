// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.cli.utils.MainArgs;
import org.aya.test.TestRunner;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.Global;
import org.aya.util.reporter.ThrowingReporter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertLinesMatch;

public class LiterateTest {
  @BeforeAll public static void enter() {
    Global.NO_RANDOM_NAME = true;
  }

  @AfterAll public static void exit() {
    Global.reset();
  }

  @Test public void literate() throws IOException {
    record Case(String in, String out, String exp) {}
    var files = ImmutableSeq.of(
      new Case("test.aya", "test.txt", "standard-test.txt"),
      new Case("issue596.aya", "issue596.txt", "standard-issue596.txt")
    );

    var literate = TestRunner.DEFAULT_TEST_DIR.resolve("literate");
    var distillInfo = new CompilerFlags.DistillInfo(
      true,
      MainArgs.DistillStage.scoped,
      MainArgs.DistillFormat.plain,
      DistillerOptions.pretty(),
      new RenderOptions(),
      literate);
    var flags = new CompilerFlags(false, false, distillInfo, ImmutableSeq.empty(), null);
    var compiler = new SingleFileCompiler(ThrowingReporter.INSTANCE, TestRunner.LOCATOR, null);

    for (var f : files) {
      compiler.compile(literate.resolve(f.in), flags, null);
      Seq.from(Files.list(literate).toList()).view()
        .filter(path -> !files.contains(path.getFileName().toString()))
        .forEachChecked(Files::delete);
      var actual = literate.resolve(f.out);
      var readString = Files.readAllLines(actual);
      Files.delete(actual);
      assertLinesMatch(Files.readAllLines(literate.resolve(f.exp)), readString);
    }
  }
}
