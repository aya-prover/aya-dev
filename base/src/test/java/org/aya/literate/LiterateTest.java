// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.literate;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.Global;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.cli.utils.MainArgs;
import org.aya.test.TestRunner;
import org.aya.test.ThrowingReporter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LiterateTest {
  @BeforeAll public static void enter() {
    Global.NO_RANDOM_NAME = true;
  }

  @AfterAll public static void exit() {
    Global.reset();
  }

  @Test public void literate() throws IOException {
    var literate = TestRunner.TEST_SOURCE_DIR.resolve("literate");
    var distillInfo = new CompilerFlags.DistillInfo(MainArgs.DistillStage.scoped, MainArgs.DistillFormat.plain, literate);
    var flags = new CompilerFlags(CompilerFlags.Message.ASCII, false, distillInfo, ImmutableSeq.empty());
    var compiler = new SingleFileCompiler(ThrowingReporter.INSTANCE, TestRunner.LOCATOR, null);
    compiler.compile(literate.resolve("test.aya"), flags, null);
    var strings = List.of("test.txt", "test.aya", "standard-test.txt");
    Seq.from(Files.list(literate).toList()).view()
      .filter(path -> !strings.contains(path.getFileName().toString()))
      .forEachChecked(Files::delete);
    var actual = literate.resolve("test.txt");
    var readString = Files.readAllLines(actual)
      .stream()
      .map(s -> s + "\n")
      .toList();
    Files.delete(actual);
    assertEquals(Files.readAllLines(literate.resolve("standard-test.txt"))
        .stream()
        .map(s -> s + "\n")
        .toList()
      , readString);
  }
}
