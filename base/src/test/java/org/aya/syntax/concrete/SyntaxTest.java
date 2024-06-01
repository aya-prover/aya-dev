// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete;

import org.aya.prettier.AyaPrettierOptions;
import org.aya.syntax.SyntaxTestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SyntaxTest {
  @Test public void test0() {
    var res = SyntaxTestUtil.parse("""
      import Prelude
      module MyMod {}
      prim I
      def foo (f : Type -> Type 0) (a : Type 0) =>
        [ f a ]
      def bar (A : Type 0) : A -> A => fn x => {? x ?}
      open inductive Nat | O | S Nat
      open inductive Fin Nat
      | S n => FZ
      | S n => FS (Fin n)
      def infixl + Nat Nat : Nat
      | 0, a => a
      | S a, b => S (a + b)
      def infixl +' Nat Nat : Nat => fn a b =>
        let open Nat in
        let n := a + b in n
      tighter + looser +
      """);
    for (var stmt : res) {
      assertNotNull(stmt.toDoc(AyaPrettierOptions.debug()).debugRender());
    }
  }
}
