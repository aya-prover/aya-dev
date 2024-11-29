// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core;

import org.aya.tyck.TyckTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PrettierTest {
  @Test
  public void clauses() {
    var result = TyckTest.tyck("""
      open inductive Nat | O | S Nat
      
      def plus (a : Nat) (b : Nat) : Nat
      | 0  , c   => b
      | S c, 0   => S (plus c b)
      | S c, S d => S (plus c b)
      """);

    var fnPlus = result.defs()
      .findFirst(x -> x.ref().name().equals("plus"))
      .get();

    assertEquals("""
        def plus (a b : Nat) : Nat
          | 0, c => c
          | S c, 0 => S (plus c 0)
          | S c, S d => S (plus c (S d))""",      // no new line here!!
      fnPlus.debuggerOnlyToString());
  }
}
