// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core;

import org.aya.tyck.TyckTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PrettierTest {
  @Test public void clauses() {
    var result = TyckTest.tyck("""
      open inductive Nat | O | S Nat
      
      def plus (a : Nat) (b : Nat) : Nat
      | 0  , c   => b
      | S c, 0   => S (plus c b)
      | S c, S d => S (plus c b)
      
      def swap (a : Nat) : Nat => match a {
        | 0 => 1
        | S _ => 0
      }
      """);

    var fnPlus = result.find("plus");
    var fnSwap = result.find("swap");

    assertEquals("""
      def plus (a b : Nat) : Nat
        | 0, c => c
        | S c, 0 => S (plus c 0)
        | S c, S d => S (plus c (S d))""", fnPlus.debuggerOnlyToString());
    // no new line at the end!!
    assertEquals("""
      def swap (a : Nat) : Nat => match a {
        | 0 => 1
        | S _ => 0
      }""", fnSwap.debuggerOnlyToString());
  }
}
