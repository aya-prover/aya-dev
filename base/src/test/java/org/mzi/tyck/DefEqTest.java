// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.mzi.test.Lisp;
import org.mzi.test.LispTestCase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefEqTest extends LispTestCase {
  @Test
  public void basicFailure() {
    assertFalse(eq().compare(Lisp.reallyParse("(U)"), Lisp.reallyParse("jojo"), null));
    assertFalse(eq().compare(Lisp.reallyParse("(app (lam (a (U) ex) a) x)", vars), Lisp.reallyParse("(app g y)", vars), null));
    assertFalse(eq().compare(Lisp.reallyParse("(app (lam (a (U) ex) a) x)", vars), Lisp.reallyParse("(app g x)", vars), null));
    assertFalse(eq().compare(Lisp.reallyParse("(app (lam (a (U) ex) a) x)", vars), Lisp.reallyParse("(app f y)", vars), null));
    assertFalse(eq().compare(Lisp.reallyParse("(Pi (a (U) ex) (Pi (b (U) ex) a))"), Lisp.reallyParse("(Pi (a (U) ex) a)"), null));
    assertFalse(eq().compare(Lisp.reallyParse("(Sigma (a (U) ex (b (U) ex null)) a)"), Lisp.reallyParse("(Sigma (a (U) ex null) a)"), null));
    assertFalse(eq().compare(Lisp.reallyParse("(Pi (a (U) ex) (Pi (b (U) ex) a))"), Lisp.reallyParse("(Pi (a (U) ex) (Pi (b a ex) a))"), null));
    assertFalse(eq().compare(Lisp.reallyParse("(proj (tup (app (lam (a (U) ex) a) x) b) 1)"), Lisp.reallyParse("(U)"), null));
    assertFalse(eq().compare(Lisp.reallyParse("(proj t 1)"), Lisp.reallyParse("(U)"), null));
    assertFalse(eq().compare(Lisp.reallyParse("(proj t 1)", vars), Lisp.reallyParse("(proj t 2)", vars ), null));
  }

  @Test
  public void identical() {
    identical("(proj (lam (a (U) ex) a) 1)");
    identical("(lam (a (U) ex) a)");
    identical("xyren");
    identical("(Pi (a (U) ex) a)");
    identical("(Sigma (a (U) ex null) a)");
    identical("(U)");
    identical("(tup (proj t 1) (proj t 2))");
    identical("(proj t 1)");
    identical("(proj (tup (app (lam (a (U) ex) a) x) b) 1)");
  }

  private void identical(@Language("TEXT") String code) {
    assertTrue(eq().compare(Lisp.reallyParse(code, vars), Lisp.reallyParse(code, vars), null));
  }

  @Test
  public void reduceApp() {
    assertTrue(eq().compare(Lisp.reallyParse("(app (lam (a (U) ex) a) a)", vars), Lisp.reallyParse("a", vars), null));
  }

  @Test
  public void alphaLam() {
    assertTrue(eq().compare(
      Lisp.reallyParse("(lam (x (U) ex) (app f x))", vars),
      Lisp.reallyParse("(lam (y (U) ex) (app f y))", vars), Lisp.reallyParse("(Pi (x (U) ex) U)")));
    assertFalse(eq().compare(
      Lisp.reallyParse("(lam (x (U) ex) (app f x))", vars),
      Lisp.reallyParse("(lam (y (U) ex) (app f z))", vars), Lisp.reallyParse("(Pi (x (U) ex) U)")));
  }

  @Test
  public void etaLamLhs() {
    assertTrue(eq().compare(Lisp.reallyParse("(lam (x (U) ex) (app f x))", vars), Lisp.reallyParse("f", vars), Lisp.reallyParse("(Pi (x (U) ex) U)")));
    assertFalse(eq().compare(Lisp.reallyParse("(lam (x (U) ex) (app f y))", vars), Lisp.reallyParse("f", vars), Lisp.reallyParse("(Pi (x (U) ex) U)")));
    assertFalse(eq().compare(Lisp.reallyParse("(lam (x (U) ex) (app g x))", vars), Lisp.reallyParse("f", vars), Lisp.reallyParse("(Pi (x (U) ex) U)")));
  }

  // ref: commit 03befddc
  @Test
  public void etaLamRhs() {
    assertTrue(eq().compare(Lisp.reallyParse("f", vars), Lisp.reallyParse("(lam (x (U) ex) (app f x))", vars), Lisp.reallyParse("(Pi (x (U) ex) U)")));
    assertFalse(eq().compare(Lisp.reallyParse("f", vars), Lisp.reallyParse("(lam (x (U) ex) (app f y))", vars), Lisp.reallyParse("(Pi (x (U) ex) U)")));
    assertFalse(eq().compare(Lisp.reallyParse("f", vars), Lisp.reallyParse("(lam (x (U) ex) (app g x))", vars), Lisp.reallyParse("(Pi (x (U) ex) U)")));
  }

  // ref: commit e3601934, cbcee4cc
  @Test
  public void etaTup() {
    var etaed = Lisp.reallyParse("(tup (proj t 1) (proj t 2))", vars);
    var type = Lisp.reallyParse("(Sigma (x (U) ex null) U)");
    assertTrue(eq().compare(etaed, Lisp.reallyParse("t", vars), type));
    assertTrue(eq().compare(Lisp.reallyParse("t", vars), etaed, type));
    assertFalse(eq().compare(etaed, Lisp.reallyParse("t", vars), null));
    assertFalse(eq().compare(etaed, Lisp.reallyParse("tt", vars), type));
  }

  @Test
  public void projReduce() {
    assertTrue(eq().compare(Lisp.reallyParse("(proj (tup (app (lam (a (U) ex) a) x) b) 1)", vars), Lisp.reallyParse("(app (lam (a (U) ex) a) x)", vars), null));
  }

  @Test
  public void fnCall() {
    Lisp.reallyParseDef("id",
      "(y (U) ex null)", "y", "y", vars);
    Lisp.reallyParseDef("id2",
      "(y (U) ex null)", "y", "y", vars);
    var fnCall = Lisp.reallyParse("(fncall id kiva)", vars);
    assertTrue(eq().compare(fnCall, Lisp.reallyParse("kiva", vars), null));
    assertTrue(eq().compare(fnCall, Lisp.reallyParse("(fncall id kiva)", vars), null));
    assertTrue(eq().compare(fnCall, Lisp.reallyParse("(fncall id2 kiva)", vars), null));
    assertFalse(eq().compare(fnCall, Lisp.reallyParse("(fncall id kiwa)", vars), null));
    assertFalse(eq().compare(fnCall, Lisp.reallyParse("(app id kiva)"), null));
    assertFalse(eq().compare(fnCall, Lisp.reallyParse("kiva"), null));
  }

  @Test
  public void telescopeNoSplitSigma() {
    var lhs = Lisp.reallyParse("(Sigma (a (U) ex (b (U) ex null)) a)");
    var rhs = Lisp.reallyParse("(Sigma (a (U) ex null) (Sigma (b (U) ex null) a))");
    var rhs2 = Lisp.reallyParse("(Sigma (a (U) ex null) (Sigma (b (Pi (n (U) ex) U) ex null) a))");
    assertFalse(eq().compare(lhs, rhs, null));
    assertFalse(eq().compare(lhs, rhs2, null));
    assertFalse(eq().compare(rhs, lhs, null));
    assertFalse(eq().compare(rhs2, lhs, null));
  }
}
