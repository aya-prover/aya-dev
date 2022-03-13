// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.ops.Eta;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.ref.LocalVar;
import org.aya.tyck.env.MapLocalCtx;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EtaTest {
  private static final @NotNull Term SIGMA = new FormTerm.Sigma(ImmutableSeq.of(
    new Term.Param(new LocalVar("A"), FormTerm.Univ.ZERO, false),
    new Term.Param(new LocalVar("B"), FormTerm.Univ.ZERO, false)));
  private static final @NotNull LocalVar X = new LocalVar("x");
  private static final @NotNull LocalVar Y = new LocalVar("y");
  private static final @NotNull Eta ETA = new Eta(new MapLocalCtx());

  @BeforeAll public static void init() {
    ETA.ctx().put(X, SIGMA);
  }

  // \ x -> f x
  @Test public void oneLambdaUneta() {
    var xParamTerm = new Term.Param(X, FormTerm.Univ.ZERO, false);
    var xRefTerm = new RefTerm(X, 0);
    // It's rather tedious to construct a Fn here
    // So let's be lazy here as the type of f doesn't really matter
    var fRefTerm = new RefTerm(new LocalVar("f"), 0);
    var lambda = IntroTerm.Lambda.make(
      // Params
      ImmutableSeq.of(xParamTerm),
      // Body
      new ElimTerm.App(fRefTerm, 0, new Arg<>(xRefTerm, false))
    );
    assertTrue(Eta.compareRefTerm(fRefTerm, ETA.uneta(lambda)));
  }

  // \ x y -> f y x
  @Test public void twoLambdaUneta() {
    var xParamTerm = new Term.Param(X, FormTerm.Univ.ZERO, false);
    var yParamTerm = new Term.Param(Y, FormTerm.Univ.ZERO, false);
    var xRefTerm = new RefTerm(X, 0);
    var yRefTerm = new RefTerm(Y, 0);
    var fRefTerm = new RefTerm(new LocalVar("f"), 0);
    var lambda = IntroTerm.Lambda.make(
      // Params
      ImmutableSeq.of(xParamTerm, yParamTerm),
      // Body
      new ElimTerm.App(
        new ElimTerm.App(fRefTerm, 0, new Arg<>(yRefTerm, false)), 0,
        new Arg<>(xRefTerm, false))
    );
    assertTrue(Eta.compareRefTerm(fRefTerm, ETA.uneta(lambda)));
  }

  // (x.1, x.2)
  @Test public void tupleUneta() {
    var xRefTerm = new RefTerm(X, 0);
    var firstTerm = new ElimTerm.Proj(xRefTerm, 0, 1);
    var secondTerm = new ElimTerm.Proj(xRefTerm, 0, 2);
    var tuple = new IntroTerm.Tuple(ImmutableSeq.of(firstTerm, secondTerm));
    assertTrue(Eta.compareRefTerm(xRefTerm, ETA.uneta(tuple)));
  }

  // (x.1, (x.1, x.2).2)
  @Test public void nestTupleUneta() {
    var xRefTerm = new RefTerm(X, 0);
    var firstTerm = new ElimTerm.Proj(xRefTerm, 0, 1);
    var secondTerm = new ElimTerm.Proj(xRefTerm, 0, 2);
    var tuple = new IntroTerm.Tuple(ImmutableSeq.of(firstTerm, secondTerm));
    var finalTuple = new IntroTerm.Tuple(ImmutableSeq.of(firstTerm, new ElimTerm.Proj(tuple, 0, 2)));
    assertTrue(Eta.compareRefTerm(xRefTerm, ETA.uneta(finalTuple)));
  }

  // \x -> f (x.1, x.2)
  @Test public void tupleAndLambdaUneta() {
    var xParamTerm = new Term.Param(X, SIGMA, false);
    var xRefTerm = new RefTerm(X, 0);
    var fRefTerm = new RefTerm(new LocalVar("f"), 0);
    // construct lambda body: tuple term
    var firstTerm = new ElimTerm.Proj(xRefTerm, 0, 1);
    var secondTerm = new ElimTerm.Proj(xRefTerm, 0, 2);
    var tuple = new IntroTerm.Tuple(ImmutableSeq.of(firstTerm, secondTerm));
    var lambda = IntroTerm.Lambda.make(
      // Params
      ImmutableSeq.of(xParamTerm),
      // Body
      new ElimTerm.App(fRefTerm, 0, new Arg<>(tuple, false))
    );
    assertTrue(Eta.compareRefTerm(fRefTerm, ETA.uneta(lambda)));
  }
}
