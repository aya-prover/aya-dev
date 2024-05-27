// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

@SuppressWarnings("unused")
public interface PatCohError {
  @Language("Aya") String testUnsureMissing = """
    open import Arith::Nat
    open data Fin+1 (n : Nat) : Type
    | m => fzero
    | suc m => fsuc (Fin+1 m)
    
    def finToNat (n' : Nat) (att : Fin+1 n') : Nat
    | n, fzero => zero
    | suc n, fsuc a => suc (finToNat n a)
    
    def addF {m n : Nat} (a : Fin+1 m) (b : Fin+1 n) : Fin+1 (finToNat m a + n)
    | fzero, a' => a'
    """;

  @Language("Aya") String testConfl = """
    open import Arith::Nat
    overlap def addN Nat Nat : Nat
    | zero, a => suc a
    | a, zero => a
    | suc a, b => suc (addN a b)
    | a, suc b => suc (addN a b)
    """;

  @Language("Aya") String testConflLiteral = """
    open import Arith::Nat
    overlap def test Nat : Nat
    | 0 => 0
    | a => a
    | suc a => suc a
    | suc (suc a) => a
    | 2147483647 => 3
    | 2147483647 => 4
    | 114514 => 1919
    """;

  @Language("Aya") String testConflLiteral2 = """
    open import Arith::Nat
    overlap def largeInt1 Nat Nat : Nat
    | a, b => a
    | 114514, 1919810 => 1
    
    overlap def largeInt2 Nat Nat : Nat
    | a, b => b
    | a, 1919810 => 1
    
    overlap def largeInt3 Nat Nat : Nat
    | a, b => b
    | a, suc b => b
    | a, 1919810 => 1
    
    overlap def largeInt1-inv Nat Nat : Nat
    | a, b => a
    | 114514, 1919810 => 1
    
    overlap def largeInt2-inv Nat Nat : Nat
    | b, a => b
    | 1919810, a => 1
    
    overlap def largeInt3-inv Nat Nat : Nat
    | b, a => b
    | suc b, a => b
    | 1919810, a => 1
    
    overlap def multi-nodes Nat Nat : Nat
    | 114, 0 => 0
    | 114, suc b => suc b
    | 114, 514 => 515
    | 115, 514 => 514
    | a, b => b
    //  ^ should be 3 groups: [0, 4], [1, 2, 4], [3, 4]
    """;

  @Language("Aya") String testFirstMatchDomination = """
    open import Arith::Nat
    def addN Nat Nat : Nat
    | zero, a => a
    | a, zero => a
    | suc a, b => suc (addN a b)
    | a, suc b => suc (addN a b)
    """;

  @Language("Aya") String testLiteralConfluence = """
    open import Arith::Nat
    overlap def not-conf Nat : Nat
    | zero => 1
    | 0 => 0
    | 1 => 1
    | suc 1 => 1
    | suc n => n
    """;

  @Language("Aya") String testNestedMissing = """
    open import Arith::Nat
    def add {m n : Nat} : Nat
    | {0}, {0} => 0
    | {0}, {suc (suc a)} => 0
    | {suc a}, {0} => 0
    | {suc a}, {b} => 0
    | {suc a}, {suc b} => 0
    """;

  @Language("Aya") String testIApplyConfluence = """
    open import Arith::Nat
    open import Arith::Int
    open import Arith::Bool
    def test Int : Nat
    | signed true _ => 1
    | signed false _ => 0
    | posneg _ => 1
    """;

  @Language("Aya") String testCoverage = """
    open import Arith::Nat
    def cov (x x' x'' x''' : Nat) : Nat
    | zero, a, b, c => 0
    | a, zero, b, c => 0
    | a, b, zero, c => 0
    """;

  @Language("Aya") String testCoverageLiteral = """
    open import Arith::Nat
    def cov (x x' x'' x''' : Nat) : Nat
    | 0, a, b, c => 0
    | a, 0, b, c => 0
    | a, b, 0, c => 0
    """;

  @Language("Aya") String testIApplyConflReduce = """
    open import Arith::Nat
    open import Paths
    open data WrongInt
    | pos Nat
    | neg Nat
    | posneg (n : Nat) : pos n = neg n
    
    def abs WrongInt : Nat
    | pos (suc n) => 1
    | pos zero => 1
    | neg n => 0
    | posneg n i => 0
    """;
}
