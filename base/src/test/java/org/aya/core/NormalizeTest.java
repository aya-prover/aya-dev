// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import org.aya.api.ref.LocalVar;
import org.aya.api.util.NormalizeMode;
import org.aya.core.def.FnDef;
import org.aya.core.term.*;
import org.aya.test.Lisp;
import org.aya.test.LispTestCase;
import org.aya.tyck.TyckDeclTest;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

public class NormalizeTest extends LispTestCase {
  private void unchanged(@Language("TEXT") String code) {
    var term = Lisp.parse(code);
    assertEquals(term, term.normalize(NormalizeMode.NF));
    assertEquals(term, term.normalize(NormalizeMode.WHNF));
  }

  @Test public void noNormalizeNeutral() {
    unchanged("(app f a)");
  }

  @Test public void noNormalizeCanonical() {
    unchanged("(lam (a (U) ex) a)");
    unchanged("(Pi (a (U) ex) a)");
    unchanged("(Sigma (a (U) ex null) a)");
  }

  @Test public void redexNormalize() {
    var term = Lisp.parse("(app (lam (a (U) ex) a) b)");
    assertTrue(term instanceof AppTerm);
    var whnf = term.normalize(NormalizeMode.WHNF);
    var nf = term.normalize(NormalizeMode.NF);
    assertTrue(whnf instanceof RefTerm);
    assertTrue(nf instanceof RefTerm);
    assertEquals(whnf, nf);
  }

  @Test public void whnfNoNormalize() {
    var term = Lisp.parse("(lam (x (U) ex) (app (lam (a (U) ex) a) b))");
    assertEquals(term, term.normalize(NormalizeMode.WHNF));
  }

  @Test public void nfNormalizeCanonical() {
    // \x : U. (\a : U. a) b
    var term = Lisp.parse("(lam (x (U) ex) (app (lam (a (U) ex) a) b))");
    assertTrue(((LamTerm) term).body() instanceof AppTerm);
    var nf = term.normalize(NormalizeMode.NF);
    assertNotEquals(term, nf);
    assertTrue(((LamTerm) nf).body() instanceof RefTerm);
  }

  @Test public void unfoldDef() {
    // (x y : U)
    var def = Lisp.parseDef("id",
      "(y (U) ex null)", "y", "y", vars);
    var term = Lisp.parse("(fncall id kiva)", vars);
    assertTrue(term instanceof CallTerm.Fn);
    assertEquals("id", def.ref().name());
    assertEquals(1, def.telescope().size());
    var norm = term.normalize(NormalizeMode.WHNF);
    assertNotEquals(term, norm);
    assertEquals(new RefTerm((LocalVar) vars.get("kiva")), norm);
  }

  @Test public void unfoldPatterns() {
    var defs = TyckDeclTest.successTyckDecls("""
      \\open \\data Nat : \\Set | zero | suc Nat
      \\def tracy (a, b : Nat) : Nat
       | zero, a => a
       | a, zero => a
       | suc a, b => suc (tracy a b)
       | a, suc b => suc (tracy a b)
      \\def xyr : Nat => tracy zero (suc zero)
      \\def kiva : Nat => tracy (suc zero) zero
      \\def overlap (a : Nat) : Nat => tracy a zero
      \\def overlap2 (a : Nat) : Nat => tracy zero a""");
    IntFunction<Term> normalizer = i -> ((FnDef) defs.get(i))
      .body().getLeftValue().normalize(NormalizeMode.NF);
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
      \\data Nat : \\Set | zero | suc Nat
      \\prim I
      \\prim left
      \\prim right
      \\prim arcoe
      \\def xyr : Nat => arcoe (\\lam i => Nat) Nat::zero left
      \\def kiva : Nat => arcoe (\\lam i => Nat) Nat::zero right""");
    IntFunction<Term> normalizer = i -> ((FnDef) defs.get(i))
      .body().getLeftValue().normalize(NormalizeMode.NF);
    assertTrue(normalizer.apply(5) instanceof CallTerm.Con conCall
      && Objects.equals(conCall.ref().name(), "zero")
      && conCall.conArgs().isEmpty());
    assertTrue(normalizer.apply(6) instanceof CallTerm.Con conCall
      && Objects.equals(conCall.ref().name(), "zero")
      && conCall.conArgs().isEmpty());
  }
}
