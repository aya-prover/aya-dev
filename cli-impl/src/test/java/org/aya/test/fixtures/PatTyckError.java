// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

@SuppressWarnings("unused")
public interface PatTyckError {
  // Issue2 746
  @Language("Aya") String testUnknownCon = """
    open inductive Test1 | test1
    open inductive Test2 | test2
    
    def test Test1 : Test1
    | test2 => test1
    """;

  @Language("Aya") String testSelectionFailed = """
    open import arith::nat::base
    open import data::vec::base
    def mapImpl {A B : Type} {n : Nat} (f : A -> B) (xs : Vec (n + n) A) : Vec (n + n) B elim xs
    | [] => []
    | _ :> _ => _
    """;

  @Language("Aya") String testSelectionBlocked = """
    open import arith::nat::base
    open import data::vec::base
    def mapImpl {A B : Type} {n : Nat} (f : A -> B) (xs : Vec (n + n) A) : Vec (n + n) B elim xs
    | () => []
    """;

  @Language("Aya") String testSplitOnNonData = """
    open inductive Unit | unit
    def test (a : Type) : Type
     | unit y => a
    """;

  @Language("Aya") String testTupleOnNonSigma = """
    def test (a' : Type) : Type
     | (a, b) => a

    def Alias => Type
    def test2 (a' : Alias) : Type
     | (a, b) => a
    """;

  @Language("Aya") String testBadLiteral = """
    open inductive Test | t
    def not-conf Test : Test
    | 1 => t
    """;

  @Language("Aya") String testNotEnoughPattern = """
    open import arith::bool::base
    def ifElse {A : Type} (b : Bool) A A : A
    | true, x => x
    | false, x, y => y
    """;

  @Language("Aya") String testTooManyPattern = """
    open import arith::bool::base
    def ifElse {A : Type} (b : Bool) A A : A
    | true, x, {y} => x
    | false, x, y => y
    """;

  @Language("Aya") String testTooManyPattern2 = """
    open import arith::bool::base
    def ifElse {A : Type} (b : Bool) A A : A
    | true, x, y, z => x
    | false, x, y => y
    """;

  @Language("Aya") String testInvalidEmptyBody = """
    open import arith::bool::base
    def test Bool : Bool
    | true
    | false
    """;

  @Language("Aya") String testInvalidAbsurdPattern = """
    open import arith::bool::base
    def test Bool : Bool | ()
    """;

  @Language("Aya") String testNoPattern = """
    open import relation::binary::path hiding (funExt)
    
    variable A B : Type
    def funExt (f g : A -> B) (p : forall a -> f a = g a) : f = g
    """;

  @Language("Aya") String testNewRepoIssue597 = """
    open inductive Nat | O | S Nat
    def bad Nat : Nat | S S O => O | _ => O
    """;

  @Language("Aya") String testNewRepoIssue746 = """
    open inductive Test1 | test1
    open inductive Test2 | test2
    def test Test1 : Test1
    | test2 => test1
    """;

  @Language("Aya") String testNewRepoIssue384 = "def test : Type";
  @Language("Aya") String testNewRepoIssue1245 = """
    open inductive Wrap (A B : Type)
    | _ => wrap B
    // Should be skipped by the orga tycker
    def test => wrap
    """;

  @Language("Aya") String testImplicitPatWithElim = """
    def foo (A : Type) A : A elim A
    | _, {a} => a
    """;

  @Language("Aya") String testUnimportedCon = """
    open import arith::bool using (Bool)
    
    def not (b : Bool) : Bool
    | true => Bool::false
    | false => Bool::true
    
    inductive RealCase (b : Bool)
    | true => real_true
    """;
}
