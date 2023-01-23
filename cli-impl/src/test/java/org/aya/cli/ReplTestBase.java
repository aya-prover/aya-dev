// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.cli.interactive.ReplConfig;
import org.aya.cli.repl.AyaRepl;
import org.aya.repl.IO;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;

import java.io.StringReader;
import java.io.StringWriter;

public class ReplTestBase {
  public static final @NotNull ReplConfig config = new ReplConfig(Option.none());

  @BeforeAll public static void setup() {
    config.enableUnicode = false;
    config.silent = true;
    config.prompt = "";
  }

  @NotNull protected Tuple2<String, String> repl(@Language("TEXT") @NotNull String input) {
    var out = new StringWriter();
    var err = new StringWriter();
    var reader = new StringReader(input + "\n:exit");
    var repl = new AyaRepl.PlainRepl(ImmutableSeq.empty(), config, new IO(reader, out, err));
    repl.run();
    return Tuple.of(out.toString(), err.toString());
  }
}
