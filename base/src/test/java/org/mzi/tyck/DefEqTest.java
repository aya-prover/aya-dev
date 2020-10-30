// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.mzi.test.Lisp;
import org.mzi.test.LispTestCase;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefEqTest extends LispTestCase {
  @Test
  public void identical() {
    identical("(proj (lam (a (U) ex null) a) 1)");
    identical("(lam (a (U) ex null) a)");
    identical("xyren");
    identical("(Pi (a (U) ex null) a)");
    identical("(Sigma (a (U) ex null))");
  }

  private void identical(@Language("TEXT") String code) {
    assertTrue(eq().compare(Lisp.reallyParse(code, vars), Lisp.reallyParse(code, vars), null));
  }

  @Test
  public void reduceApp() {
    assertTrue(eq().compare(Lisp.reallyParse("(app (lam (a (U) ex null) a) a)", vars), Lisp.reallyParse("a", vars), null));
  }

  @Test
  public void alphaLam() {
    assertTrue(eq().compare(
      Lisp.reallyParse("(lam (x (U) ex null) (app f x))", vars),
      Lisp.reallyParse("(lam (y (U) ex null) (app f y))", vars), Lisp.reallyParse("(Pi (x (U) ex null) U)")));
  }

  @Test
  public void etaLamLhs() {
    assertTrue(eq().compare(Lisp.reallyParse("(lam (x (U) ex null) (app f x))", vars), Lisp.reallyParse("f", vars), Lisp.reallyParse("(Pi (x (U) ex null) U)")));
  }

  // ref: commit 03befddc
  @Test
  public void etaLamRhs() {
    assertTrue(eq().compare(Lisp.reallyParse("f", vars), Lisp.reallyParse("(lam (x (U) ex null) (app f x))", vars), Lisp.reallyParse("(Pi (x (U) ex null) U)")));
  }

  // ref: commit e3601934, cbcee4cc
  @Test
  public void etaTup() {
    var etaed = Lisp.reallyParse("(tup (proj t 1) (proj t 2))", vars);
    assertTrue(eq().compare(etaed, Lisp.reallyParse("t", vars), Lisp.reallyParse("(Sigma (x (U) ex (y (U) ex null)))")));
    assertTrue(eq().compare(Lisp.reallyParse("t", vars), etaed, Lisp.reallyParse("(Sigma (x (U) ex (y (U) ex null)")));
  }

  @Test
  public void projReduce() {
    assertTrue(eq().compare(Lisp.reallyParse("(proj (tup (app (lam (a (U) ex null) a) x) b) 1)", vars), Lisp.reallyParse("(app (lam (a (U) ex null) a) x)", vars), null));
  }

  @Test
  public void telescopeSplit() {
    assertTrue(eq().compare(
      Lisp.reallyParse("(lam (a (U) ex (b (U) ex null)) a)"),
      Lisp.reallyParse("(lam (a (U) ex null) (lam (b (U) ex null) a))"),
      null
    ));
  }
}
