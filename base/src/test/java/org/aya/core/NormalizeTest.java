// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import org.aya.core.def.FnDef;
import org.aya.core.term.ConCall;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.generic.util.NormalizeMode;
import org.aya.tyck.TyckDeclTest;
import org.aya.tyck.TyckState;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NormalizeTest {
  @Test public void unfoldPatterns() {
    var res = TyckDeclTest.successTyckDecls("""
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
    var defs = res._2;
    var state = new TyckState(res._1);
    IntFunction<Term> normalizer = i -> ((FnDef) defs.get(i)).body.getLeftValue().normalize(state, NormalizeMode.NF);
    assertTrue(normalizer.apply(2) instanceof ConCall conCall
      && Objects.equals(conCall.ref().name(), "suc"));
    assertTrue(normalizer.apply(3) instanceof ConCall conCall
      && Objects.equals(conCall.ref().name(), "suc"));
    assertTrue(normalizer.apply(4) instanceof RefTerm ref
      && Objects.equals(ref.var().name(), "a"));
    assertTrue(normalizer.apply(5) instanceof RefTerm ref
      && Objects.equals(ref.var().name(), "a"));
  }

  @Test public void unfoldPrim() {
    var res = TyckDeclTest.successTyckDecls("""
      prim I
      open data Nat : Type | zero | suc Nat
      prim coe
      def xyr : Nat => (\\ i => Nat).coe zero freeze 1
      def kiva : Nat => (\\ i => Nat).coe (suc zero) freeze 1""");
    var state = new TyckState(res._1);
    var defs = res._2;
    IntFunction<Term> normalizer = i -> ((FnDef) defs.get(i)).body.getLeftValue().normalize(state, NormalizeMode.NF);
    assertTrue(normalizer.apply(3) instanceof ConCall conCall
      && Objects.equals(conCall.ref().name(), "zero")
      && conCall.conArgs().isEmpty());
    assertTrue(normalizer.apply(4) instanceof ConCall conCall
      && Objects.equals(conCall.ref().name(), "suc"));
  }

  @Test public void recommitLamApp() {
    var res = TyckDeclTest.successTyckDecls("""
      open data Nat | zero | suc Nat
      open data List (A : Type) : Type
        | nil
        | infixr :< A (List A)
      def overlap infixl + (a b : Nat) : Nat
        | zero, a => a
        | a, zero => a
        | suc a, b => suc (a + b)
        | a, suc b => suc (a + b)
      def overlap infixr ++ {A : Type} (xs ys : List A) : List A
        | nil, ys => ys
        | xs, nil => xs
        | a :< xs, ys => a :< (xs ++ ys)
      def join {A : Type} (l : List (List A)) : List A
        | nil => nil
        | xs :< xss => xs ++ join xss
      def map {A B : Type} (f : A -> B) (l : List A) : List B
        | f, nil => nil
        | f, a :< l => f a :< map f l
      def pure {A : Type} (x : A) : List A => x :< nil
      def infix >>= {A B : Type} (l : List A) (f : A -> List B) : List B => join (map f l)
      
      def t1 => do {
        a <- pure zero,
        b <- pure (suc zero),
        pure (a + b)
      }
      def t2 => pure (zero + suc zero)
      """);
    var state = new TyckState(res._1);
    var defs = res._2;
    IntFunction<Term> normalizer = i -> ((FnDef) defs.get(i)).body.getLeftValue().normalize(state, NormalizeMode.NF);
    assertEquals("suc zero :< nil", normalizer.apply(defs.size() - 2).toDoc(AyaPrettierOptions.debug()).debugRender());
    assertEquals("suc zero :< nil", normalizer.apply(defs.size() - 1).toDoc(AyaPrettierOptions.debug()).debugRender());
  }
}
