// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

@SuppressWarnings("unused")
public interface GoalAndMeta {
  @Language("Aya") String testUnsolved = """
    open import arith::nat::base
    def test : Nat => _
    """;

  @Language("Aya") String testGoal = """
    open import arith::nat::base
    def test (a : Nat) : Nat => {? a ?}
    """;

  @Language("Aya") String testUnsolvedMetaLit = """
    open import arith::nat::base
    open inductive Nat2 | OO | SS Nat2
    open inductive Option (A : Type)
      | some A
    def test => some 114514
    """;

  @Language("Aya") String dontTestUnsolvedMetaLit = """
    open import arith::nat::base
    open inductive Nat2 | OO | SS Nat2
    open inductive Empty
    
    def take Empty => Empty
    def test => take 114514
    """;

  @Language("Aya") String testDaylily = """
    open import arith::bool::base
    
    def wow {A : Type 1} {B : A -> Type} (a b : A) (x : B a) (y : B b) : Bool => true
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
    open import arith::nat::base
    inductive Empty
    def Neg (T : Type) => T -> Empty
    // Ulf's counterexample
    def test
     (F : Type -> Type)
     (g : ∀ (X : F _) -> F (Neg X)) : Nat => g 0
    """;

  @Language("Aya") String testScopeCheck = """
    open import relation::binary::path
    variable A : Type
    
    // https://cstheory.stackexchange.com/a/49160/50892
    def test (a : _) (B : Type) (b : B) (p : a = b) : I => 0
    """;

  @Language("Aya") String testLiteralAmbiguous3 = """
    open inductive List (A : Type) | nil | cons A (List A)
    open inductive List2 (A : Type) | nil2 | cons2 A (List2 A)
    open inductive Unit | unit
    
    def good : List Unit => [ ]
    def bad => [ unit ]
    """;

  @Language("Aya") String testNonPattern = """
    open import data::vec::base
    open import arith::nat::base
    open import relation::binary::path
    variable n m o : Nat
    variable A : Type
    def ++-assoc-type (xs : Vec n A) (ys : Vec m A) (zs : Vec o A)
      => Path (fn i => Vec (+-assoc i) A)
      (xs ++ (ys ++ zs))
      ((xs ++ ys) ++ zs)
    """;

  @Language("Aya") String testUtensilFullFile = """
    open import data::vec::base
    open import arith::nat::base
    open import relation::binary::path
    variable n m o : Nat
    variable A : Type
    
    def ++-assoc' (xs : Vec n A) (ys : Vec m A) (zs : Vec o A)
    : Path (fn i ⇒ Vec (+-assoc i) A)
      (xs ++ (ys ++ zs)) ((xs ++ ys) ++ zs) elim xs
    | [] ⇒ refl
    | x :> _ => pmap (x :>) (++-assoc' _ _ _)
    """;
}
