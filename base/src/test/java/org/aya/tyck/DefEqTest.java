// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.core.term.Term;
import org.aya.ref.LocalVar;
import org.aya.test.Lisp;
import org.aya.test.LispTestCase;
import org.aya.tyck.unify.TypedDefEq;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.tuple.Tuple2;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefEqTest extends LispTestCase {
  private final Term typeU = Lisp.parse("(U)");
  private final Term typePi = Lisp.parse("(Pi (m (U) ex) (U))");
  private final Term typeSigma = Lisp.parse("(Sigma (n (U) ex null) (U))");

  @NotNull LocalVar getLocal(String name) {
    return (LocalVar) vars.get(name);
  }

  public DefEqTest() {
    ImmutableSeq.of("x", "y", "f", "g", "jojo", "xyren", "kiva", "kiwa", "t", "tt").forEach(name ->
      vars.put(name, new LocalVar(name))
    );
    eq = eq(MutableHashMap.ofEntries(
      Tuple2.of(getLocal("x"), typeU),
      Tuple2.of(getLocal("y"), typeU),
      Tuple2.of(getLocal("f"), typePi),
      Tuple2.of(getLocal("g"), typePi),
      Tuple2.of(getLocal("jojo"), typeU),
      Tuple2.of(getLocal("xyren"), typeU),
      Tuple2.of(getLocal("kiva"), typeU),
      Tuple2.of(getLocal("kiwa"), typeU),
      Tuple2.of(getLocal("t"), typeSigma),
      Tuple2.of(getLocal("tt"), typeSigma)
    ));
  }

  private final TypedDefEq eq;

  @Test
  public void basicFailure() {
    assertFalse(eq.compare(typeU, Lisp.parse("jojo"), typeU));
    assertFalse(eq.compare(Lisp.parse("(app (lam (a (U) ex) a) x)", vars), Lisp.parse("(app g y)", vars), typeU));
    assertFalse(eq.compare(Lisp.parse("(app (lam (a (U) ex) a) x)", vars), Lisp.parse("(app g x)", vars), typeU));
    assertFalse(eq.compare(Lisp.parse("(app (lam (a (U) ex) a) x)", vars), Lisp.parse("(app f y)", vars), typeU));
    assertFalse(eq.compare(Lisp.parse("(Pi (a (U) ex) (Pi (b (U) ex) a))"), Lisp.parse("(Pi (a (U) ex) a)"), typeU));
    assertFalse(eq.compare(Lisp.parse("(Sigma (a (U) ex (b (U) ex null)) a)"), Lisp.parse("(Sigma (a (U) ex null) a)"), typeU));
    assertFalse(eq.compare(Lisp.parse("(Pi (a (U) ex) (Pi (b (U) ex) a))"), Lisp.parse("(Pi (a (U) ex) (Pi (b a ex) a))"), typeU));
    assertFalse(eq.compare(Lisp.parse("(proj (tup (app (lam (a (U) ex) a) x) b) 1)"), typeU, typeU));
    assertFalse(eq.compare(Lisp.parse("(proj t 1)"), typeU, typeU));
    assertFalse(eq.compare(Lisp.parse("(proj t 1)", vars), Lisp.parse("(proj t 2)", vars ), typeU));
  }

  @Test
  public void identical() {
    identical("(lam (a (U) ex) a)", "(Pi (a (U) ex) (U))");
    identical("xyren", "(U)");
    identical("(Pi (b (U) ex) b)", "(U)");
    identical("(Sigma (a (U) ex null) a)", "(U)");
    identical("(U)", "(U)");
    identical("(tup (proj t 1) (proj t 2))", "(Sigma (c (U) ex null) (U))");
    identical("(proj t 1)", "(U)");
    identical("(proj (tup (app (lam (a (U) ex) a) x) b) 1)", "(U)");
  }

  private void identical(@Language("TEXT") String code, @Language("TEXT") String type) {
    assertTrue(eq.compare(Lisp.parse(code, vars), Lisp.parse(code, vars), Lisp.parse(type, vars)));
  }

  @Test
  public void reduceApp() {
    assertTrue(eq.compare(Lisp.parse("(app (lam (a (U) ex) a) x)", vars), Lisp.parse("x", vars), typeU));
  }

  @Test
  public void alphaLam() {
    assertTrue(eq.compare(
      Lisp.parse("(lam (x (U) ex) (app f x))", vars),
      Lisp.parse("(lam (y (U) ex) (app f y))", vars), typePi));
    assertFalse(eq.compare(
      Lisp.parse("(lam (x (U) ex) (app f x))", vars),
      Lisp.parse("(lam (y (U) ex) (app f z))", vars), typePi));
  }

  @Test
  public void etaLamLhs() {
    assertTrue(eq.compare(Lisp.parse("(lam (x (U) ex) (app f x))", vars), Lisp.parse("f", vars), typePi));
    assertFalse(eq.compare(Lisp.parse("(lam (x (U) ex) (app f y))", vars), Lisp.parse("f", vars), typePi));
    assertFalse(eq.compare(Lisp.parse("(lam (x (U) ex) (app g x))", vars), Lisp.parse("f", vars), typePi));
  }

  // ref: commit 03befddc
  @Test
  public void etaLamRhs() {
    assertTrue(eq.compare(Lisp.parse("f", vars), Lisp.parse("(lam (x (U) ex) (app f x))", vars), typePi));
    assertFalse(eq.compare(Lisp.parse("f", vars), Lisp.parse("(lam (x (U) ex) (app f y))", vars), typePi));
    assertFalse(eq.compare(Lisp.parse("f", vars), Lisp.parse("(lam (x (U) ex) (app g x))", vars), typePi));
  }

  // ref: commit e3601934, cbcee4cc
  @Test
  public void etaTup() {
    var etaed = Lisp.parse("(tup (proj t 1) (proj t 2))", vars);
    var type = typeSigma;
    assertTrue(eq.compare(etaed, Lisp.parse("t", vars), type));
    assertTrue(eq.compare(Lisp.parse("t", vars), etaed, type));
    assertFalse(eq.compare(etaed, Lisp.parse("tt", vars), type));
  }

  @Test
  public void projReduce() {
    assertTrue(eq.compare(Lisp.parse("(proj (tup (app (lam (a (U) ex) a) x) y) 1)", vars), Lisp.parse("(app (lam (a (U) ex) a) x)", vars), typeU));
  }

  @Test
  public void fnCall() {
    Lisp.parseDef("id",
      "(y (U) ex null)", "y", "y", vars);
    Lisp.parseDef("id2",
      "(y (U) ex null)", "y", "y", vars);
    var fnCall = Lisp.parse("(fncall id kiva)", vars);
    assertTrue(eq.compare(fnCall, Lisp.parse("kiva", vars), typeU));
    assertTrue(eq.compare(fnCall, Lisp.parse("(fncall id kiva)", vars), typeU));
    assertTrue(eq.compare(fnCall, Lisp.parse("(fncall id2 kiva)", vars), typeU));
    assertFalse(eq.compare(fnCall, Lisp.parse("(fncall id kiwa)", vars), typeU));
    assertFalse(eq.compare(fnCall, Lisp.parse("(app id kiva)"), typeU));
    assertFalse(eq.compare(fnCall, Lisp.parse("kiva"), typeU));
  }

  @Test
  public void telescopeNoSplitSigma() {
    var lhs = Lisp.parse("(Sigma (a (U) ex (b (U) ex null)) a)");
    var rhs = Lisp.parse("(Sigma (a (U) ex null) (Sigma (b (U) ex null) a))");
    var rhs2 = Lisp.parse("(Sigma (a (U) ex null) (Sigma (b (Pi (n (U) ex) U) ex null) a))");
    assertFalse(eq.compare(lhs, rhs, typeU));
    assertFalse(eq.compare(lhs, rhs2, typeU));
    assertFalse(eq.compare(rhs, lhs, typeU));
    assertFalse(eq.compare(rhs2, lhs, typeU));
  }
}
