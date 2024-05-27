// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

@SuppressWarnings("unused")
public interface ScopeError {
  @Language("Aya") String testDidYouMeanDisamb = """
    open data Nat1 | zero
    open data Nat2 | zero
    def one => zero
    """;
  @Language("Aya") String testDidYouMean = """
    data Nat | zero | suc Nat
    def one => suc zero
    """;
  @Language("Aya") String testImportDefineShadow = """
    open import Arith::Bool
    module A { def foo => true }
    open A
    def foo => false
    """;
  @Language("Aya") String testImportUsing = """
    open import Arith::Bool
    module A { def foo => true }
    open A using (foo as bruh)
    open A using (bar)
    """;
  @Language("Aya") String testImportHiding = """
    open import Arith::Bool
    module A { def foo => true }
    open A hiding (foo)
    open A hiding (bar)
    """;
  @Language("Aya") String testImportDefineShadow2 = """
    open data Bool | true | false
    module A { def foo => true }
    def foo => false
    open A
    """;
  @Language("Aya") String testInfRec = "def undefined => undefined";
  @Language("Aya") String testIssue247 = """
    data Z : Type
    | zero
    | zero
    """;
  @Language("Aya") String testRedefPrim = "prim I prim I";
  @Language("Aya") String testUnknownPrim = "prim senpaiSuki";
  @Language("Aya") String testUnknownVar = """
    open data Nat : Type | zero
    def p => Nat::suc Nat::zero
    """;
  // This should pass
  @Language("Aya") String testLetOpen = """
    open import Paths using (=, refl)
    data Nat | O | S Nat
    def zero : Nat => let open Nat using (O) in O
    def suc (n : Nat) : Nat => let open Nat hiding (O) in S n
    def they-are : suc zero = Nat::S Nat::O => refl
    """;
}
