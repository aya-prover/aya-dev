// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.parse.AyaParserImpl;
import org.aya.concrete.Expr;
import org.aya.util.reporter.ThrowingReporter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReplParserTest {
  @Test public void issue358() {
    var sucZero = AyaParserImpl.repl(ThrowingReporter.INSTANCE, "suc zero").getRightValue();
    assertTrue(sucZero instanceof Expr.BinOpSeq);
  }
}
