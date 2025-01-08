// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
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
      open MyMod using (foo)
      open Prelude hiding (bar)
      prim I
      variable i : I
      def foo (f : Type -> Type 0) (a : Type 0) =>
        [ f a ]
      def foo2 => ↑↑ foo
      def bar (A : Type 0) : A -> A => fn x => {? x ?}
      @suppress(LocalShadow)
      open inductive Nat | O | S Nat
      open inductive Fin Nat
      | S n => FZ
      | S n => FS (Fin n)
      open inductive Vec (A : Type) (n : Nat) elim n
      | O => vnil
      | S m => vcons A (Vec A m)
      def infixl + Nat Nat : Nat
      | 0, a => a
      | S a, b as b' => S (a + b')
      def patternFeatures : Nat
      | [ a::b ] => 1
      | (a, b) => 1
      def infixl +' Nat Nat : Nat => fn a b =>
        let open Nat in
        let n := a + b in n
      open class Cat
      | A : Type
      open class Monoid
      | A : Type
      | infixl + : Fn (a b : A) -> A
      """);
    for (var stmt : res) {
      assertNotNull(stmt.toDoc(AyaPrettierOptions.debug()).debugRender());
    }
  }

  @Test public void test1() {
    var moduleLoader = SyntaxTestUtil.moduleLoader();
    var stmts = SyntaxTestUtil.parse("""
      open inductive Nat | O | S Nat
      def infixl + Nat Nat : Nat
      | 0, a => a
      | S a, b => S (a + b)
      def infixl +' => + looser +
      def infixl +'' => + tighter +
      """);
    moduleLoader.resolve(stmts);
    for (var stmt : stmts) {
      assertNotNull(stmt.easyToString());
    }
  }
}
