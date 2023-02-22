// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import org.aya.core.serde.SerTerm;
import org.aya.core.serde.Serializer;
import org.aya.tyck.TyckDeclTest;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SuedeTest {
  @Test public void nat() {
    suedeAll("""
      open data Nat : Type | zero | suc Nat
      def add (a b : Nat) : Nat
       | zero, a => a
       | suc a, b => suc (add a b)
      def test (a : Nat) => \\x => add a (add x zero)""");
  }

  @Test public void piSig() {
    suedeAll("def test (y : Type 0) : Type 3 => Fn (x : Type 0 -> Type 2) -> Sig (x y) ** x y");
  }

  @Test public void adjunction() {
    suedeAll("""
      def curry (A B C : Type)
                 (f : (Sig A ** B) -> C)
                 (a : A) (b : B) : C
        => f (a, b)
      def uncurry (A : Type) (B : Type) (C : Type)
                   (f : Fn A B -> C)
                   (p : Sig A ** B) : C
        => f (p.1) (p.2)
      def fst {A B : Type} (t : Sig A ** B) : A
        | (a, b) => a
      """);
  }

  @Test public void path() {
    suedeAll("""
      prim I
      prim intervalInv
      inline def ~ => intervalInv
      def Path (A : I -> Type) (a : A 0) (b : A 1) : Type => [| i |] A i { ~ i := a | i := b }
      def Eq (A : Type) (a b : A) : Type => Path (\\ i => A) a b
      variable A : Type
      def infix = {A : Type} => Eq A
      def refl {a : A} : a = a => \\i => a
      prim coe
      def hcomp2d {a b c d : A}
        (p : a = b) (q : b = d) (r : a = c) : c = d
        => \\i => ((\\ k => r k = q k).coe p) i
      struct Monoid {A : Type} (op : A -> A -> A): Type
        | id : A
        | assoc (a b c : A) : op (op a b) c = op a (op b c)
        | id_r (a: A) : op a id = a
        | id_l (a: A) : op id a = a
      """);
  }

  @Test public void string() {
    suedeAll("""
      prim String: Type
      prim strcat (str1 str2: String) : String
      def c => strcat "123" "456"
      """);
  }

  private void suedeAll(@Language("Aya") @NotNull String code) {
    var res = TyckDeclTest.successTyckDecls(code);
    var state = new SerTerm.DeState(res.component1());
    var serializer = new Serializer(new Serializer.State());
    res.component2().view()
      .map(serializer::serialize)
      .map(ser -> ser.de(state))
      .forEach(Assertions::assertNotNull);
  }
}
