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
    assertFalse(eq().compare(Lisp.parse("(U)"), Lisp.parse("jojo"), null));
    assertFalse(eq().compare(Lisp.parse("(app (lam (a (U) ex) a) x)", vars), Lisp.parse("(app g y)", vars), null));
    assertFalse(eq().compare(Lisp.parse("(app (lam (a (U) ex) a) x)", vars), Lisp.parse("(app g x)", vars), null));
    assertFalse(eq().compare(Lisp.parse("(app (lam (a (U) ex) a) x)", vars), Lisp.parse("(app f y)", vars), null));
    assertFalse(eq().compare(Lisp.parse("(Pi (a (U) ex) (Pi (b (U) ex) a))"), Lisp.parse("(Pi (a (U) ex) a)"), null));
    assertFalse(eq().compare(Lisp.parse("(Sigma (a (U) ex (b (U) ex null)) a)"), Lisp.parse("(Sigma (a (U) ex null) a)"), null));
    assertFalse(eq().compare(Lisp.parse("(Pi (a (U) ex) (Pi (b (U) ex) a))"), Lisp.parse("(Pi (a (U) ex) (Pi (b a ex) a))"), null));
    assertFalse(eq().compare(Lisp.parse("(proj (tup (app (lam (a (U) ex) a) x) b) 1)"), Lisp.parse("(U)"), null));
    assertFalse(eq().compare(Lisp.parse("(proj t 1)"), Lisp.parse("(U)"), null));
    assertFalse(eq().compare(Lisp.parse("(proj t 1)", vars), Lisp.parse("(proj t 2)", vars ), null));
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
    assertTrue(eq().compare(Lisp.parse(code, vars), Lisp.parse(code, vars), null));
  }

  @Test
  public void reduceApp() {
    assertTrue(eq().compare(Lisp.parse("(app (lam (a (U) ex) a) a)", vars), Lisp.parse("a", vars), null));
  }

  @Test
  public void alphaLam() {
    assertTrue(eq().compare(
      Lisp.parse("(lam (x (U) ex) (app f x))", vars),
      Lisp.parse("(lam (y (U) ex) (app f y))", vars), Lisp.parse("(Pi (x (U) ex) U)")));
    assertFalse(eq().compare(
      Lisp.parse("(lam (x (U) ex) (app f x))", vars),
      Lisp.parse("(lam (y (U) ex) (app f z))", vars), Lisp.parse("(Pi (x (U) ex) U)")));
  }

  @Test
  public void etaLamLhs() {
    assertTrue(eq().compare(Lisp.parse("(lam (x (U) ex) (app f x))", vars), Lisp.parse("f", vars), Lisp.parse("(Pi (x (U) ex) U)")));
    assertFalse(eq().compare(Lisp.parse("(lam (x (U) ex) (app f y))", vars), Lisp.parse("f", vars), Lisp.parse("(Pi (x (U) ex) U)")));
    assertFalse(eq().compare(Lisp.parse("(lam (x (U) ex) (app g x))", vars), Lisp.parse("f", vars), Lisp.parse("(Pi (x (U) ex) U)")));
  }

  // ref: commit 03befddc
  @Test
  public void etaLamRhs() {
    assertTrue(eq().compare(Lisp.parse("f", vars), Lisp.parse("(lam (x (U) ex) (app f x))", vars), Lisp.parse("(Pi (x (U) ex) U)")));
    assertFalse(eq().compare(Lisp.parse("f", vars), Lisp.parse("(lam (x (U) ex) (app f y))", vars), Lisp.parse("(Pi (x (U) ex) U)")));
    assertFalse(eq().compare(Lisp.parse("f", vars), Lisp.parse("(lam (x (U) ex) (app g x))", vars), Lisp.parse("(Pi (x (U) ex) U)")));
  }

  // ref: commit e3601934, cbcee4cc
  @Test
  public void etaTup() {
    var etaed = Lisp.parse("(tup (proj t 1) (proj t 2))", vars);
    var type = Lisp.parse("(Sigma (x (U) ex null) U)");
    assertTrue(eq().compare(etaed, Lisp.parse("t", vars), type));
    assertTrue(eq().compare(Lisp.parse("t", vars), etaed, type));
    assertFalse(eq().compare(etaed, Lisp.parse("t", vars), null));
    assertFalse(eq().compare(etaed, Lisp.parse("tt", vars), type));
  }

  @Test
  public void projReduce() {
    assertTrue(eq().compare(Lisp.parse("(proj (tup (app (lam (a (U) ex) a) x) b) 1)", vars), Lisp.parse("(app (lam (a (U) ex) a) x)", vars), null));
  }

  @Test
  public void fnCall() {
    Lisp.parseDef("id",
      "(y (U) ex null)", "y", "y", vars);
    Lisp.parseDef("id2",
      "(y (U) ex null)", "y", "y", vars);
    var fnCall = Lisp.parse("(fncall id kiva)", vars);
    assertTrue(eq().compare(fnCall, Lisp.parse("kiva", vars), null));
    assertTrue(eq().compare(fnCall, Lisp.parse("(fncall id kiva)", vars), null));
    assertTrue(eq().compare(fnCall, Lisp.parse("(fncall id2 kiva)", vars), null));
    assertFalse(eq().compare(fnCall, Lisp.parse("(fncall id kiwa)", vars), null));
    assertFalse(eq().compare(fnCall, Lisp.parse("(app id kiva)"), null));
    assertFalse(eq().compare(fnCall, Lisp.parse("kiva"), null));
  }

  @Test
  public void telescopeNoSplitSigma() {
    var lhs = Lisp.parse("(Sigma (a (U) ex (b (U) ex null)) a)");
    var rhs = Lisp.parse("(Sigma (a (U) ex null) (Sigma (b (U) ex null) a))");
    var rhs2 = Lisp.parse("(Sigma (a (U) ex null) (Sigma (b (Pi (n (U) ex) U) ex null) a))");
    assertFalse(eq().compare(lhs, rhs, null));
    assertFalse(eq().compare(lhs, rhs2, null));
    assertFalse(eq().compare(rhs, lhs, null));
    assertFalse(eq().compare(rhs2, lhs, null));
  }
}
