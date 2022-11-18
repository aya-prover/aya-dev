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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

public class LiterateTest {
  @BeforeAll public static void enter() {
    Global.NO_RANDOM_NAME = true;
  }

  @AfterAll public static void exit() {
    Global.reset();
  }

  @Test public void literate() throws IOException {
    var literate = TestRunner.DEFAULT_TEST_DIR.resolve("literate");
    var distillInfo = new CompilerFlags.DistillInfo(
      MainArgs.DistillStage.scoped,
      MainArgs.DistillFormat.plain,
      DistillerOptions.pretty(),
      new RenderOptions(),
      literate);
    var flags = new CompilerFlags(CompilerFlags.Message.ASCII, false, false, distillInfo, ImmutableSeq.empty(), null);
    var compiler = new SingleFileCompiler(ThrowingReporter.INSTANCE, TestRunner.LOCATOR, null);
    compiler.compile(literate.resolve("test.aya"), flags, null);
    var strings = List.of("test.txt", "test.aya", "standard-test.txt");
    Seq.from(Files.list(literate).toList()).view()
      .filter(path -> !strings.contains(path.getFileName().toString()))
      .forEachChecked(Files::delete);
    var actual = literate.resolve("test.txt");
    var readString = Files.readAllLines(actual);
    Files.delete(actual);
    assertLinesMatch(Files.readAllLines(literate.resolve("standard-test.txt")), readString);
  }
}
