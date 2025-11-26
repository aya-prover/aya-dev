// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

@SuppressWarnings("unused")
public interface ExprTyckError {
  @Language("Aya") String testTypeMismatch = """
    open import arith::nat::base
    def test => 1 + Type
    """;

  @Language("Aya") String testIllTypedApp = """
    open import arith::nat::base
    def test (a : Nat) => a 1
    """;

  @Language("Aya") String testWantButNo = """
    open import arith::nat::base
    def test : Type => \\ x => x
    """;

  @Language("Aya") String testCringeReturnType = """
    def fr : Type -> Type => \\x => x
    def test : fr => Type
    """;

  @Language("Aya") String testBadInterval = """
    prim I
    def test : I => 2
    """;

  @Language("Aya") String testBadPrim = "prim I : Type";

  @Language("Aya") String testPrimNoResult = """
    prim I
    prim Path (A : I -> Type) (a b : A)
    """;

  @Language("Aya") String testPiDom = """
    inductive X : Set
    inductive Test : Type | con X
    """;

  @Language("Aya") String testPiDomMeta = """
    inductive X : Set
    inductive infix = (a b : X) : Type
    inductive Test : Type
    | con (x : _) (y : X) (x = y)
    """;

  @Language("Aya") String testConReturn = """
    inductive X
    inductive Y | con : X
    """;

  @Language("Aya") String testNoRule = "def x => \"114514\"";

  @Language("Aya") String testMatchMissingReturns = """
    open import arith::nat::base
    def test (a : Nat) : Nat =>
        match a { _ => fn x => x } 1
    """;

  @Language("Aya") String testIssue1238 = """
    open import relation::binary::path
    open inductive Wrap (A : Type) | wrap A
    def what (A : Type) (w : Wrap) : w = w => refl
    """;

  @Language("Aya") String testBadPartial = """
    open import arith::nat::base
    open import relation::binary::path
    def test (i : I) : Nat => partial [ i = 1 => 3 ]
    """;
}
