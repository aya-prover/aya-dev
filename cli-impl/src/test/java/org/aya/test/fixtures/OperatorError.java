// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

public interface OperatorError {
  @Language("Aya") String testAmbiguous = """
    open data Nat | zero | suc Nat
    // Do not replace with the library definition, we need our own fixity
    def infix + (a b: Nat) : Nat => 0
    def infix == (a b: Nat) : Nat => 0
    def fail : Nat => 1 + 1 == 2
    """;

  @Language("Aya") String testCyclic = """
    def infix a => Type tighter b
    def infix b => Type tighter c
    def infix c => Type tighter d
    def infix d => Type tighter b
    def infix e => Type
    """;

  @Language("Aya") String testIssue677 = """
    data False
    def fixl ¬ (A : Type) => A -> False
    def NonEmpty (A : Type) => ¬ ¬ A
    """;

  @Language("Aya") String testNoAssoc = """
    open data Nat | zero | suc Nat
    // Do not replace with the library definition, we need our own fixity
    def infixl + (a b: Nat) => 0
    def infixr ^ (a b : Nat) => 0
    def infix  = (a b : Nat) => 0
    
    def test1 : Nat => 1 + 2 + 3
    def test2 : Nat => 1 ^ 2 ^ 3
    def test3 : Nat => 1 = 2 = 3
    """;

  @Language("Aya") String testSelfBind = """
    def infix + : Type => +
      looser +
    """;

  // This should pass
  @Language("Aya") String testModuleImportRename = """
    open data Nat | zero | suc Nat
    module A {
      def infixl + (a b: Nat) => 0
    }
    def infix  = (a b : Nat) => 0
    open A using (+ as infixr +' tighter =)
    def test => 1 +' 2 = 3
    """;
}
