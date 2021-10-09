// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import kala.collection.Seq;
import kala.collection.mutable.Buffer;
import org.aya.api.ref.LocalVar;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.visitor.RefFinder;
import org.aya.tyck.TyckDeclTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UsagesTest {
  @Test public void refFinder() {
    assertTrue(RefFinder.HEADER_AND_BODY.withBody());
    TyckDeclTest.successTyckDecls("""
      open data Nat : Type 0 | zero | suc Nat
      def one : Nat => suc zero
      open data Int : Type 0 | pos Nat | neg Nat { | zero => pos zero }
      def abs (a : Int) : Nat
       | pos n => n
       | neg n => n
      open data Fin (n : Nat) : Type | suc m => fzero | suc m => fsuc (Fin m)
      """).forEach(def -> {
      var of = Buffer.<Def>create();
      def.accept(RefFinder.HEADER_AND_BODY, of);
      assertFalse(of.isEmpty());
      of.clear();
      def.accept(RefFinder.HEADER_ONLY, of);
      if (Seq.of("Nat", "Int").contains(def.ref().name())) assertTrue(of.isEmpty());
      else assertFalse(of.isEmpty());
        if (def instanceof FnDef fn && fn.body.isLeft())
            assertEquals(0, fn.body.getLeftValue().findUsages(new LocalVar("233")));
    });
  }
}
