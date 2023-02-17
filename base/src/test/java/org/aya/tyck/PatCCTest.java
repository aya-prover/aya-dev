// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.FnDef;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.test.AyaThrowingReporter;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.Arg;
import org.aya.util.error.SourcePos;
import org.aya.util.tyck.pat.PatClass;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CC = coverage and confluence
 */
public class PatCCTest {
  public static @NotNull ImmutableSeq<PatClass<ImmutableSeq<Arg<Term>>>>
  testClassify(@NotNull PrimDef.Factory factory, @NotNull FnDef fnDef) {
    var clauses = fnDef.body.getRightValue().map(Pat.Preclause::weaken);
    return PatClassifier.classify(clauses, fnDef.telescope, new TyckState(factory), AyaThrowingReporter.INSTANCE, SourcePos.NONE, null);
  }

  @Test public void addCC() {
    var res = TyckDeclTest.successTyckDecls("""
      open data Nat : Type | zero | suc Nat
      overlap def add (a b : Nat) : Nat
       | zero, b => b
       | a, zero => a
       | suc a, b => suc (add a b)
       | a, suc b => suc (add a b)""");
    var decls = res.component2();
    var classified = testClassify(res.component1(), (FnDef) decls.get(1));
    assertEquals(4, classified.size());
    classified.forEach(cls ->
      assertEquals(2, cls.cls().size()));
  }

  @Test public void nmIfElse() {
    var res = TyckDeclTest.successHeadFirstTyckDecls("""
      open data CExp
      | at
      | if CExp CExp CExp
      
      open data nmAcc CExp : Type
      | at => atAcc
      | if at t e => ifAtAcc (nmAcc t) (nmAcc e)
      | if (if u v w) y z =>
        ifIfAcc (h1 : nmAcc (if v y z)) (h2 : nmAcc (if w y z))
                (nmAcc (if u (nm (if v y z) h1) (nm (if w y z) h2)))
      
      def nm (e : CExp) (nmAcc e) : CExp
      | at, atAcc => at
      | if at t e, ifAtAcc tAcc eAcc => if at (nm t tAcc) (nm e eAcc)
      | if (if u v w) y z, ifIfAcc h1 h2 h3 =>
         nm (if u (nm (if v y z) h1) (nm (if w y z) h2)) h3
      """);
    var decls = res.component2();
    var classified = testClassify(res.component1(), (FnDef) decls.last());
    assertEquals(3, classified.size());
    classified.forEach(cls ->
      assertEquals(1, cls.cls().size()));
  }

  @Test public void maxCC() {
    var res = TyckDeclTest.successTyckDecls("""
      open data Nat : Type | zero | suc Nat
      def max (a b : Nat) : Nat
       | zero, b => b
       | a, zero => a
       | suc a, suc b => suc (max a b)""");
    var decls = res.component2();
    var classified = testClassify(res.component1(), (FnDef) decls.get(1));
    assertEquals(4, classified.size());
    assertEquals(3, classified.filter(patClass -> patClass.cls().sizeEquals(1)).size());
    assertEquals(1, classified.filter(patClass -> patClass.cls().sizeEquals(2)).size());
  }

  @Test public void tupleCC() {
    var res = TyckDeclTest.successTyckDecls("""
      open data Nat : Type | zero | suc Nat
      open data Unit : Type | unit Nat
      def max (a : Sig Nat ** Nat) (b : Unit) : Nat
       | (zero, b), unit x => b
       | (a, zero), y => a
       | (suc a, suc b), unit y => suc (max (a, b) (unit zero))""");
    var decls = res.component2();
    var classified = testClassify(res.component1(), (FnDef) decls.get(2));
    assertEquals(4, classified.size());
    assertEquals(3, classified.filter(patClass -> patClass.cls().sizeEquals(1)).size());
    assertEquals(1, classified.filter(patClass -> patClass.cls().sizeEquals(2)).size());
  }
}
