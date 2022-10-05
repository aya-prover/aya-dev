// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import org.aya.core.def.FnDef;
import org.aya.core.term.ElimTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.generic.Arg;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.aya.tyck.TyckDeclTest;
import org.aya.util.distill.DistillerOptions;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("UnknownLanguage")
public class DistillerTest {
  @Test public void fn() {
    var doc1 = declDoc("def id {A : Type} (a : A) : A => a");
    var doc2 = declDoc("def id {A : Type} (a : A) => a");
    var doc3 = declDoc("""
      def curry3 (A  B  C  D : Type)
                  (f : Pi (x : Sig A B ** C) -> D)
                  (a : A) (b : B) (c : C) : D
        => f (a, b, c)
      def uncurry3 (A : Type) (B : Type) (C : Type) (D : Type)
                    (f : Pi A B C -> D)
                    (p : Sig A B ** C) : D
        => f (p.1) (p.2) (p.3)""");
    assertFalse(Doc.cat(doc1, doc2, doc3).renderToHtml().isEmpty());
  }

  @Test public void data() {
    @Language("Aya") var code = """
      open data Nat : Type | zero | suc Nat
      open data Int : Type | pos Nat | neg Nat { | zero => pos zero }
      open data Fin (n : Nat) : Type | suc m => fzero | suc m => fsuc (Fin m)
      """;
    assertFalse(declDoc(code).renderToHtml().isEmpty());
    assertFalse(declCDoc(code).renderToHtml().isEmpty());
  }

  @Test public void neo() {
    assertFalse(declDoc("""
      prim I
      struct Pair (A : Type) (B : Type) : Type
        | fst : A
        | snd : B
        | we-are-together : Sig A ** B => (fst, snd)

      def make-pair (A B : Type) (a : A) (b : B) : Pair A B =>
        new Pair A B { | fst => a | snd => b }
      def sigPat (A B : Type) (x : Sig A ** B) : Sig B ** A
        | A, B, (a, b) => (b, a)
      """).renderToHtml().isEmpty());
  }

  @Test public void path() {
    @Language("Aya") var code = """
      prim I
      prim coe
      prim intervalInv
      def inline ~ => intervalInv
      def Path (A : I -> Type) (a : A 0) (b : A 1) : Type => [| i |] A i {| ~ i := a | i := b |}
      def Eq (A : Type) (a b : A) : Type => Path (\\ i => A) a b
      variable A : Type
      def infix = {A : Type} => Eq A
      def refl {a : A} : a = a => \\i => a
      struct Monoid {A : Type} (op : A -> A -> A): Type
        | id : A
        | assoc (a b c : A) : op (op a b) c = op a (op b c)
        | id_r (a: A) : op a id = a
        | id_l (a: A) : op id a = a
      """;
    assertFalse(declDoc(code).renderToTeX().isEmpty());
    assertFalse(declCDoc(code).renderToTeX().isEmpty());
  }

  @Test public void nestedPi() {
    var decls = TyckDeclTest.successTyckDecls("""
      def infix = (A B : Type) => A
      def infix == (A B : Type) => A looser =
      def infix <= (A B : Type) => A tighter =
      def test1 (X : Type) => Pi (A : Type) -> A ulift = X
      def test2 (X : Type) => (Pi (A : Type) -> A) ulift = X

      def infix ?= : Type -> Type -> Type => \\ (A B : Type) => A
      def use (A B : Type) => A ?= B
      """)._2;
    var test1 = ((FnDef) decls.get(3)).body.getLeftValue();
    var test2 = ((FnDef) decls.get(4)).body.getLeftValue();
    var use = ((FnDef) decls.get(6)).body.getLeftValue();
    assertNotNull(decls.get(1).ref().concrete.toDoc(DistillerOptions.informative()));
    assertNotNull(decls.get(2).ref().concrete.toDoc(DistillerOptions.informative()));
    assertEquals("Pi (A : Type 0) -> A = X", test1.toDoc(DistillerOptions.informative()).debugRender());
    assertEquals("(Pi (A : Type 0) -> A) = X", test2.toDoc(DistillerOptions.informative()).debugRender());
    assertEquals("A ?= B", use.toDoc(DistillerOptions.informative()).debugRender());
  }

  @Test public void binop() {
    var decls = TyckDeclTest.successTyckDecls("""
      open data Nat | zero | suc Nat
      open data D   | infix · Nat Nat

      def g (h : Nat -> D) : Nat => zero
      def t (n : Nat) => g (n ·)
      """)._2;
    var t = ((FnDef) decls.get(3)).body.getLeftValue();
    assertEquals("g (n ·)", t.toDoc(DistillerOptions.informative()).debugRender());
  }

  @Test public void elimBinOP() {
    var decls = TyckDeclTest.successTyckDecls("""
      prim I
      prim intervalInv
      def inline ~ => intervalInv
      def Eq (A : Type) (a b : A) : Type => [| i |] A {| ~ i := a | i := b |}
      def infix = {A : Type} => Eq A
      open data Nat | zero | suc Nat
      def test => zero = zero
      """)._2;
    var t = ((FnDef) decls.get(6)).body.getLeftValue();
    assertEquals("(=) {Nat} zero zero", t.toDoc(DistillerOptions.informative()).debugRender());
    assertEquals("zero = zero", t.toDoc(DistillerOptions.pretty()).debugRender());
  }

  @Test public void intervalOp() {
    var decls = TyckDeclTest.successTyckDecls("""
      prim I
      prim intervalInv
      prim intervalMin
      prim intervalMax
      def inline ~ => intervalInv
      def inline infixl /\\ => intervalMin
      def inline infixl \\/ => intervalMax
      def Eq (A : Type) (a b : A) : Type => [| i |] A {| ~ i := a | i := b |}
      def infix = {A : Type} => Eq A
      def test1 {A : Type} {a : A} (p : a = a) (i j k : I) => p ((i \\/ j \\/ k) /\\ (k \\/ j \\/ i))
      def test2 {A : Type} {a : A} (p : a = a) (i j k : I) => p ((i /\\ j /\\ k) \\/ (k /\\ j /\\ i))
      """)._2;
    var t1 = ((FnDef) decls.get(9)).body.getLeftValue();
    var t2 = ((FnDef) decls.get(10)).body.getLeftValue();
    assertEquals("p ((i \\/ j \\/ k) /\\ (k \\/ j \\/ i))", t1.toDoc(DistillerOptions.informative()).debugRender());
    assertEquals("p (i /\\ j /\\ k \\/ k /\\ j /\\ i)", t2.toDoc(DistillerOptions.informative()).debugRender());
  }

  @Test public void lambdaApp() {
    var a = new LocalVar("a");
    var A = new LocalVar("A");
    var x = new LocalVar("x");
    var t = new ElimTerm.App(new IntroTerm.Lambda(new Term.Param(a, new RefTerm(A), true), new RefTerm(a)), new Arg<>(new RefTerm(x), true));
    assertEquals("(\\ a => a) x", t.toDoc(DistillerOptions.informative()).debugRender());
  }

  @Test public void pathApp() {
    var decls = TyckDeclTest.successTyckDecls("""
      prim I
      prim intervalInv
      def inline ~ => intervalInv
      def infix = {A : Type} (a b : A) : Type => [| i |] A {| ~ i := a | i := b |}
      def idp {A : Type} {a : A} : a = a => \\i => a
      def test {A : Type} {a b : A} (p : a = b) : a = b => \\i => p i
      """)._2;
    var t = ((FnDef) decls.get(5)).body.getLeftValue();
    assertEquals("\\ i => p i", t.toDoc(DistillerOptions.informative()).debugRender());
  }

  @Test public void literals() {
    var decls = TyckDeclTest.successTyckDecls("""
      open data Nat | zero | suc Nat
      def test1 : Nat => 0
      def test2 : Nat => 114514
      def test3 Nat : Nat
        | 0 => 1
        | 1 => 2
        | a => suc a
      """)._2;
    var t1 = ((FnDef) decls.get(1)).body.getLeftValue();
    var t2 = ((FnDef) decls.get(2)).body.getLeftValue();
    var t3 = ((FnDef) decls.get(3));
    assertEquals("0", t1.toDoc(DistillerOptions.informative()).debugRender());
    assertEquals("114514", t2.toDoc(DistillerOptions.informative()).debugRender());
    assertEquals("""
      def test3 (_7 : Nat) : Nat
        |  0 => 1
        |  1 => 2
        |  a => suc a""", t3.toDoc(DistillerOptions.informative()).debugRender());
  }

  private @NotNull Doc declDoc(@Language("Aya") String text) {
    return Doc.vcat(TyckDeclTest.successTyckDecls(text)._2.map(d -> d.toDoc(DistillerOptions.debug())));
  }

  private @NotNull Doc declCDoc(@Language("Aya") String text) {
    return Doc.vcat(TyckDeclTest.successDesugarDecls(text)._2.map(s -> s.toDoc(DistillerOptions.debug())));
  }
}
