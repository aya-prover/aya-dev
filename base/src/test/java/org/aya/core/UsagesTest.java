// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import org.aya.api.ref.LocalVar;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.visitor.RefFinder;
import org.aya.tyck.TyckDeclTest;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.Buffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UsagesTest {
  @Test public void refFinder() {
    assertTrue(RefFinder.HEADER_AND_BODY.withBody());
    TyckDeclTest.successTyckDecls("""
      open data Nat : Set 0 | zero | suc Nat
      def one : Nat => suc zero
      open data Int : Set 0 | pos Nat | neg Nat { | zero => pos zero }
      def abs (a : Int) : Nat
       | pos n => n
       | neg n => n
      open data Fin (n : Nat) : Set | suc m => fzero | suc m => fsuc (Fin m)
      """).forEach(def -> {
      var of = Buffer.<Def>of();
      def.accept(RefFinder.HEADER_AND_BODY, of);
      assertFalse(of.isEmpty());
      of.clear();
      def.accept(RefFinder.HEADER_ONLY, of);
      if (Seq.of("Nat", "Int").contains(def.ref().name())) assertTrue(of.isEmpty());
      else assertFalse(of.isEmpty());
      if (def instanceof FnDef fn && fn.body().isLeft())
        assertEquals(0, fn.body().getLeftValue().findUsages(new LocalVar("233")));
    });
  }
}
