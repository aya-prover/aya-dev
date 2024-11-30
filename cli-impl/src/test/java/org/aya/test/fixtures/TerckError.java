// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

@SuppressWarnings("unused")
public interface TerckError {
  @Language("Aya") String testDirectNonTermination = """
    open import arith::nat::base
    def g Nat : Nat
    | 0 => 0
    | suc n => g (suc n)
    """;

  @Language("Aya") String testUnfoldNonTermination = """
    open import arith::nat::base
    def f Nat : Nat | n => g (suc n)
    def g Nat : Nat
    | 0 => 0
    | suc n => f n
    """;

  // This should pass
  @Language("Aya") String testSwapAddition = """
    open import arith::nat::base
    def swapAdd (a b : Nat) : Nat elim a
    | 0 => b
    | suc a' => suc (swapAdd b a')
    """;

  // This should pass
  @Language("Aya") String testPartialDef = """
    open import arith::nat::base
    partial def f Nat : Nat
    | a => f a
    """;

  @Language("Aya") String testSelfData = "inductive SelfData (A : SelfData)";

  @Language("Aya") String testSelfCon = "inductive SelfData | SelfCon SelfCon";

  @Language("Aya") String testSelfFn = """
    open import arith::nat::base
    def crazyAdd (a : Nat) : crazyAdd a
    | x => x
    """;
}
