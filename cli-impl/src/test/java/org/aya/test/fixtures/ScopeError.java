// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

@SuppressWarnings("unused")
public interface ScopeError {
  @Language("Aya") String testDidYouMeanDisamb = """
    private open inductive Nat1 | zero
    private open inductive Nat2 | zero
    def one => zero
    """;
  @Language("Aya") String testExportClashes = """
    open inductive Nat1 | zero
    open inductive Nat2 | zero
    """;
  @Language("Aya") String testDidYouMean = """
    inductive Nat | zero | suc Nat
    def one => suc zero
    """;
  @Language("Aya") String testImportDefineShadow = """
    open import arith::bool::base
    module A { def foo => true }
    open A
    def foo => false
    """;
  @Language("Aya") String testImportUsing = """
    open import arith::bool::base
    module A { def foo => true }
    open A using (foo as bruh)
    open A using (bar)
    """;
  @Language("Aya") String testImportHiding = """
    open import arith::bool::base
    module A { def foo => true }
    open A hiding (foo)
    open A hiding (bar)
    """;
  @Language("Aya") String testImportDefineShadow2 = """
    open inductive Bool | true | false
    module A { def foo => true }
    def foo => false
    open A
    """;
  @Language("Aya") String testIssue247 = """
    inductive Z : Type
    | zero
    | zero
    """;
  @Language("Aya") String testRedefPrim = "prim I prim I";
  @Language("Aya") String testPrimDeps = "prim Path";
  @Language("Aya") String testUnknownPrim = "prim senpaiSuki";
  @Language("Aya") String testUnknownVar = """
    open inductive Nat : Type | zero
    def p => Nat::suc Nat::zero
    """;
  // This should pass
  @Language("Aya") String testLetOpen = """
    open import relation::binary::path using (=, refl)
    inductive Nat | O | S Nat
    def zero : Nat => let open Nat using (O) in O
    def suc (n : Nat) : Nat => let open Nat hiding (O) in S n
    def they-are : suc zero = Nat::S Nat::O => refl
    """;
  @Language("Aya") String testUnknownElimVar = """
    open import arith::bool::base
    def b => true
    def p (a : Bool) : Bool elim b
    | true => false
    """;
  @Language("Aya") String testGeneralizedDisallowed = """
    variable A : Type
    def test Type : Type
    | _ => A
    """;
  @Language("Aya") String testDuplicateModName = """
    module A {}
    module A {}
    """;
  @Language("Aya") String testImportNoneExistMod = "import hopefullyThisModuleWillNeverExist";
  @Language("Aya") String testOpenNoneExistMod = "open hopefullyThisModuleWillNeverExist";
  @Language("Aya") String testDuplicateExport = """
    module A { def t => Type }
    module B { def t => Type }
    public open A
    public open B
    """;
  @Language("Aya") String testUnknownProjMem = """
    open class Cls | A : Type
    def test (c : Cls) => c.B
    """;
  @Language("Aya") String testLocalShadow = """
    def test (A : Type) (a : A) : A =>
      let | x : A := a
          | x : A := a
      in x
    """;
  @Language("Aya") String testLocalShadowSuppress = """
    @suppress(LocalShadow)
    def test (A : Type) (a : A) : A =>
      let | x : A := a
          | x : A := a
      in x
    """;
}
