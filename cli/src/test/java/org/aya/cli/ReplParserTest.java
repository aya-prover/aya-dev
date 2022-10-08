// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.parse.AyaGKParserImpl;
import org.aya.concrete.Expr;
import org.aya.util.reporter.ThrowingReporter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReplParserTest {
  public static final AyaGKParserImpl AYA_GK_PARSER = new AyaGKParserImpl(ThrowingReporter.INSTANCE);

  @Test public void issue358() {
    var sucZero = AYA_GK_PARSER.repl("suc zero").getRightValue();
    assertTrue(sucZero instanceof Expr.BinOpSeq);
  }
}
