// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import org.aya.core.def.FnDef;
import org.aya.core.def.PrimDef;
import org.aya.core.term.CallTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.generic.util.NormalizeMode;
import org.aya.tyck.TyckDeclTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NormalizeTest {
  @BeforeAll public static void enter() {
    PrimDef.Factory.INSTANCE.clear();
  }

  @Test public void unfoldPatterns() {
    var defs = TyckDeclTest.successTyckDecls("""
      open data Nat : Type | zero | suc Nat
      def overlap tracy (a b : Nat) : Nat
       | zero, a => a
       | a, zero => a
       | suc a, b => suc (tracy a b)
       | a, suc b => suc (tracy a b)
      def xyr : Nat => tracy zero (suc zero)
      def kiva : Nat => tracy (suc zero) zero
      def overlap1 (a : Nat) : Nat => tracy a zero
      def overlap2 (a : Nat) : Nat => tracy zero a""");
    IntFunction<Term> normalizer = i -> ((FnDef) defs.get(i)).body.getLeftValue().normalize(null, NormalizeMode.NF);
    assertTrue(normalizer.apply(2) instanceof CallTerm.Con conCall
      && Objects.equals(conCall.ref().name(), "suc"));
    assertTrue(normalizer.apply(3) instanceof CallTerm.Con conCall
      && Objects.equals(conCall.ref().name(), "suc"));
    assertTrue(normalizer.apply(4) instanceof RefTerm ref
      && Objects.equals(ref.var().name(), "a"));
    assertTrue(normalizer.apply(5) instanceof RefTerm ref
      && Objects.equals(ref.var().name(), "a"));
  }

  @Test public void unfoldPrim() {
    var defs = TyckDeclTest.successTyckDecls("""
      data Nat : Type 0 | zero | suc Nat
      prim I
      prim left
      prim right
      prim arcoe
      def xyr : Nat => arcoe (\\ i => Nat) Nat::zero left
      def kiva : Nat => arcoe (\\ i => Nat) (Nat::suc Nat::zero) right""");
    IntFunction<Term> normalizer = i -> ((FnDef) defs.get(i)).body.getLeftValue().normalize(null, NormalizeMode.NF);
    assertTrue(normalizer.apply(5) instanceof CallTerm.Con conCall
      && Objects.equals(conCall.ref().name(), "zero")
      && conCall.conArgs().isEmpty());
    assertTrue(normalizer.apply(6) instanceof CallTerm.Con conCall
      && Objects.equals(conCall.ref().name(), "suc"));
  }
}
