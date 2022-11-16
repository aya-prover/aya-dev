// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import kala.collection.Seq;
import org.aya.core.def.FnDef;
import org.aya.core.def.PrimDef;
import org.aya.core.visitor.TermFolder;
import org.aya.ref.LocalVar;
import org.aya.tyck.TyckDeclTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UsagesTest {
  @Test public void refFinder() {
    assertTrue(TermFolder.RefFinder.HEADER_AND_BODY.withBody());
    TyckDeclTest.successTyckDecls("""
      prim I
      open data Nat : Type 0 | zero | suc Nat
      def one : Nat => suc zero
      open data Int : Type 0 | pos Nat | neg Nat | posneg (i : I) { i := pos 0 }
      def abs (a : Int) : Nat
       | pos n => n
       | neg n => n
       | posneg i => zero
      open data Fin (n : Nat) : Type | suc m => fzero | suc m => fsuc (Fin m)
      """)._2.forEach(def -> {
      if (!(def instanceof PrimDef))
        assertTrue(TermFolder.RefFinder.HEADER_AND_BODY.apply(def).isNotEmpty());
      var of = TermFolder.RefFinder.HEADER_ONLY.apply(def);
      if (Seq.of("Nat", "Int", "I").contains(def.ref().name())) assertTrue(of.isEmpty());
      else assertFalse(of.isEmpty());
      if (def instanceof FnDef fn && fn.body.isLeft())
        assertEquals(0, fn.body.getLeftValue().findUsages(new LocalVar("233")));
    });
  }
}
