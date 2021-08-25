// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import kala.tuple.Unit;
import org.aya.core.def.PrimDef;
import org.aya.core.serde.SerTerm;
import org.aya.core.serde.Serializer;
import org.aya.tyck.TyckDeclTest;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SuedeTest {
  @BeforeEach public void cleanup() {
    PrimDef.PrimFactory.INSTANCE.clear();
  }

  @Test public void nat() {
    suedeAll("""
      open data Nat : Set | zero | suc Nat
      def add (a b : Nat) : Nat
       | zero, a => a
       | suc a, b => suc (add a b)
      def test (a : Nat) => \\x => add a (add x zero)""");
  }

  @Test public void piSig() {
    suedeAll("def test (y : Set) => Pi (x : Set -> Set (lsuc 1)) -> Sig (x y) ** x y");
  }

  @Test public void path() {
    suedeAll("""
      prim I prim left prim right
      struct Path (A : Pi I -> Type) (a : A left) (b : A right) : Type
       | at (i : I) : A i {
         | left => a
         | right => b
       }
      def `=` Eq {A : Type} (a b : A) : Type => Path (\\ i => A) a b
      bind = looser application
      prim arcoe
      def hfill2d {A : Type} {a b c d : A}
        (p : a = b) (q : b = d) (r : a = c)
        (i j : I) : A
        => (arcoe (\\ k => (r.at k) = (q.at k)) p i).at j
      """);
  }

  private void suedeAll(@Language("TEXT") @NotNull String code) {
    var state = new SerTerm.DeState();
    var serializer = new Serializer(new Serializer.State());
    TyckDeclTest.successTyckDecls(code)
      .map(def -> def.accept(serializer, Unit.unit()))
      .map(ser -> ser.de(state))
      .forEach(Assertions::assertNotNull);
  }
}
