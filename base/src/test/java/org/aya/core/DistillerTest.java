// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import org.aya.core.def.FnDef;
import org.aya.core.def.PrimDef;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.TyckDeclTest;
import org.aya.util.distill.DistillerOptions;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    @Language("TEXT") var code = """
      open data Nat : Type | zero | suc Nat
      open data Int : Type | pos Nat | neg Nat { | zero => pos zero }
      open data Fin (n : Nat) : Type | suc m => fzero | suc m => fsuc (Fin m)
      """;
    assertFalse(declDoc(code).renderToHtml().isEmpty());
    assertFalse(declCDoc(code).renderToHtml().isEmpty());
  }

  @Test public void neo() {
    assertFalse(declDoc("""
      prim I prim left
      prim right

      struct Pair (A : Type) (B : Type) : Type
        | fst : A
        | snd : B
        | we-are-together : Sig A ** B => (fst, snd)

      def test-nat-pair : Pair I I =>
        new Pair I I { | fst => left | snd => left }

      def make-pair (A B : Type) (a : A) (b : B) : Pair A B =>
        new Pair A B { | fst => a | snd => b }
      def sigPat (A B : Type) (x : Sig A ** B) : Sig B ** A
        | A, B, (a, b) => (b, a)
      """).renderToHtml().isEmpty());
  }

  @Test public void path() {
    @Language("TEXT") var code = """
      prim I prim left prim right
      struct Path (A : Pi I -> Type) (a : A left) (b : A right) : Type
       | at (i : I) : A i {
         | left => a
         | right => b
       }
      def path {A : Pi I -> Type} (p : Pi (i : I) -> A i)
        => new Path A (p left) (p right) { | at i => p i }
      def infix = {A : Type} (a b : A) : Type => Path (\\ i => A) a b
      struct Monoid {A : Type} (op : A -> A -> A): Type
        | id : A
        | assoc (a b c : A) : op (op a b) c = op a (op b c)
        | id_r (a: A) : op a id = a
        | id_l (a: A) : op id a = a
      """;
    assertFalse(declDoc(code).renderToTeX().isEmpty());
    tearDown();
    assertFalse(declCDoc(code).renderToTeX().isEmpty());
  }

  @Test public void nestedPi() {
    var decls = TyckDeclTest.successTyckDecls("""
      def infix = (A B : Type) => A
      def test1 (X : Type) => Pi (A : Type) -> A = X
      def test2 (X : Type) => (Pi (A : Type) -> A) = X
      """);
    var test1 = ((FnDef) decls.get(1)).body.getLeftValue();
    var test2 = ((FnDef) decls.get(2)).body.getLeftValue();
    assertEquals("Pi (A : Type) -> A = X", test1.toDoc(DistillerOptions.informative()).debugRender());
    assertEquals("(Pi (A : Type) -> A) = X", test2.toDoc(DistillerOptions.informative()).debugRender());
  }

  @AfterEach public void tearDown() {
    PrimDef.Factory.INSTANCE.clear();
  }

  private @NotNull Doc declDoc(@Language("TEXT") String text) {
    return Doc.vcat(TyckDeclTest.successTyckDecls(text).map(d -> d.toDoc(DistillerOptions.debug())));
  }

  private @NotNull Doc declCDoc(@Language("TEXT") String text) {
    return Doc.vcat(TyckDeclTest.successDesugarDecls(text).map(s -> s.toDoc(DistillerOptions.debug())));
  }
}
