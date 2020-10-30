// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core;

import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mzi.api.error.CollectReporter;

import org.mzi.api.ref.Var;
import org.mzi.test.Lisp;
import org.mzi.tyck.sort.LevelEqn;
import org.mzi.tyck.unify.NaiveDefEq;
import org.mzi.util.Ordering;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DefEqTest {
  private final Map<String, @NotNull Var> vars = new HashMap<>();
  private final NaiveDefEq eq = new NaiveDefEq(Ordering.Eq, new LevelEqn.Set(new CollectReporter(), Buffer.of(), Buffer.of()));

  @Test
  public void identical() {
    assertTrue(eq.compare(Lisp.reallyParse("(proj (lam (a (U) ex null) a) 1)", vars), Lisp.reallyParse("(proj (lam (a (U) ex null) a) 1)", vars), null));
  }

  @Test
  public void reduceApp() {
    assertTrue(eq.compare(Lisp.reallyParse("(app (lam (a (U) ex null) a) a)", vars), Lisp.reallyParse("a", vars), null));
  }

  @Test
  public void etaLamLhs() {
    assertTrue(eq.compare(Lisp.reallyParse("(lam (x (U) ex null) (app f x))", vars), Lisp.reallyParse("f", vars), Lisp.reallyParse("(Pi (x (U) ex null) U)")));
  }

  // ref: commit 03befddc
  @Test
  public void etaLamRhs() {
    assertTrue(eq.compare(Lisp.reallyParse("f", vars), Lisp.reallyParse("(lam (x (U) ex null) (app f x))", vars), Lisp.reallyParse("(Pi (x (U) ex null) U)")));
  }

  // ref: commit e3601934, cbcee4cc
  @Test
  public void etaTupLhs() {
    assertTrue(eq.compare(Lisp.reallyParse("(tup (proj t 1) (proj t 2))", vars), Lisp.reallyParse("t", vars), Lisp.reallyParse("(Sigma (x (U) ex (y (U) ex null)))")));
  }

  @Test
  public void etaTupRhs() {
    assertTrue(eq.compare(Lisp.reallyParse("t", vars), Lisp.reallyParse("(tup (proj t 1) (proj t 2))", vars), Lisp.reallyParse("(Sigma (x (U) ex (y (U) ex null)")));
  }

  @Test
  public void projReduce() {
    assertTrue(eq.compare(Lisp.reallyParse("(proj (tup (app (lam (a (U) ex null) a) x) b) 1)", vars), Lisp.reallyParse("(app (lam (a (U) ex null) a) x)", vars), null));
  }

  @Test
  public void telescopeSplit() {
    assertTrue(eq.compare(
      Lisp.reallyParse("(lam (a (U) ex (b (U) ex null)) a)"),
      Lisp.reallyParse("(lam (a (U) ex null) (lam (b (U) ex null) a))"),
      null
    ));
  }
}
