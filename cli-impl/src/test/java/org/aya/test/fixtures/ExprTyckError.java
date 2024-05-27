// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

@SuppressWarnings("unused")
public interface ExprTyckError {
  @Language("Aya") String testTypeMismatch = """
    open import Arith::Nat
    def test => 1 + Type
    """;

  @Language("Aya") String testIllTypedApp = """
    open import Arith::Nat
    def test (a : Nat) => a 1
    """;

  @Language("Aya") String testWantButNo = """
    open import Arith::Nat
    def test : Type => \\ x => x
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
    data X : Set
    data Test : Type | con X
    """;

  @Language("Aya") String testPiDomMeta = """
    data X : Set
    data infix = (a b : X) : Type
    data Test : Type
    | con (x : _) (y : X) (x = y)
    """;
}
