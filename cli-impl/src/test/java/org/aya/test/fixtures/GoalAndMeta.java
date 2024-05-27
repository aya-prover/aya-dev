// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

@SuppressWarnings("unused")
public interface GoalAndMeta {
  @Language("Aya") String testUnsolved = """
    open import Arith::Nat
    def test : Nat => _
    """;

  @Language("Aya") String testGoal = """
    open import Arith::Nat
    def test (a : Nat) : Nat => {? a ?}
    """;

  @Language("Aya") String testUnsolvedMetaLit = """
    open import Arith::Nat
    open data Nat2 | OO | SS Nat2
    open data Option (A : Type)
      | some A
    def test => some 114514
    """;

  @Language("Aya") String dontTestUnsolvedMetaLit = """
    open import Arith::Nat
    open data Nat2 | OO | SS Nat2
    open data Empty
    
    def take Empty => Empty
    def test => take 114514
    """;

  @Language("Aya") String testDaylily = """
    open import Arith::Nat
    
    def wow {A : Type 1} {B : A -> Type} (a b : A) (x : B a) (y : B b) : Nat => 0
    example def test1 (A B : Type) (x : A) (y : B) =>
      wow A B x y
    example def test2 (A B : Type) (x : A) (y : B) =>
      wow A B x x
    example def test4 (A B : Type) (x : A) (y : B) =>
      wow A B y y
    
    // ^ see issue608.aya, issue602.aya in proto repo
    // https://github.com/aya-prover/aya-prover-proto/issues/608
    """;

  @Language("Aya") String testNorell = """
    open import Arith::Nat
    data Empty
    def Neg (T : Type) => T -> Empty
    // Ulf's counterexample
    def test
     (F : Type -> Type)
     (g : ∀ (X : F _) -> F (Neg X)) : Nat => g 0
    """;

  @Language("Aya") String testScopeCheck = """
    open import Paths
    variable A : Type
    
    // https://cstheory.stackexchange.com/a/49160/50892
    def test (a : _) (B : Type) (b : B) (p : a = b) : I => 0
    """;

  @Language("Aya") String testLiteralAmbiguous3 = """
    open data List (A : Type) | nil | cons A (List A)
    open data List2 (A : Type) | nil2 | cons2 A (List2 A)
    open data Unit | unit
    
    def good : List Unit => [ ]
    def bad => [ unit ]
    """;
}
