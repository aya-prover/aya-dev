// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.cli;

import org.aya.prettier.AyaPrettierOptions;
import org.aya.producer.AyaParserImpl;
import org.aya.syntax.concrete.Expr;
import org.aya.util.reporter.ThrowingReporter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ReplParserTest {
  public static final AyaParserImpl AYA_GK_PARSER = new AyaParserImpl(new ThrowingReporter(AyaPrettierOptions.informative()));

  @Test public void issue358() {
    var sucZero = AYA_GK_PARSER.repl("suc zero").getRightValue().data();
    assertInstanceOf(Expr.BinOpSeq.class, sucZero);
  }
}
