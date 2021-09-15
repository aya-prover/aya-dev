// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.util;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.core.term.ElimTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EtaConversionTest {
  // \ x -> f x
  @Test public void oneLambdaSimpleEta() {
    var xParamTerm = new Term.Param(new LocalVar("x"), FormTerm.Univ.ZERO, false);
    var xRefTerm = new RefTerm(new LocalVar("x"), FormTerm.Univ.ZERO);
    // It's rather tedious to construct a Fn here
    // So let's be lazy here as the type of f doesn't really matter
    var fRefTerm = new RefTerm(new LocalVar("f"), FormTerm.Univ.ZERO);
    var lambda = IntroTerm.Lambda.make(
      // Params
      ImmutableSeq.of(xParamTerm),
      // Body
      new ElimTerm.App(fRefTerm, new Arg<>(xRefTerm, false))
    );
    Term res = EtaConversion.simpleEtaReduction(lambda);
    assertTrue(EtaConversion.compareRefTerm(fRefTerm, res));
  }

  // \ x y -> f y x
  @Test public void twoLambdaSimpleEta() {
    var xParamTerm = new Term.Param(new LocalVar("x"), FormTerm.Univ.ZERO, false);
    var yParamTerm = new Term.Param(new LocalVar("y"), FormTerm.Univ.ZERO, false);
    var xRefTerm = new RefTerm(new LocalVar("x"), FormTerm.Univ.ZERO);
    var yRefTerm = new RefTerm(new LocalVar("y"), FormTerm.Univ.ZERO);
    var fRefTerm = new RefTerm(new LocalVar("f"), FormTerm.Univ.ZERO);
    var lambda = IntroTerm.Lambda.make(
      // Params
      ImmutableSeq.of(xParamTerm, yParamTerm),
      // Body
      new ElimTerm.App(
        new ElimTerm.App(fRefTerm, new Arg<>(yRefTerm, false)),
        new Arg<>(xRefTerm, false))
    );
    Term res = EtaConversion.simpleEtaReduction(lambda);
    assertTrue(EtaConversion.compareRefTerm(fRefTerm, res));
  }

  // (x.1, x.2)
  @Test public void tupleSimpleEta() {
    var sigmaTerm = new FormTerm.Sigma(ImmutableSeq.of(
      new Term.Param(new LocalVar("A"), FormTerm.Univ.ZERO, false),
      new Term.Param(new LocalVar("A"), FormTerm.Univ.ZERO, false)));
    var xRefTerm = new RefTerm(new LocalVar("x"), sigmaTerm);
    var firstTerm = new ElimTerm.Proj(xRefTerm, 1);
    var secondTerm = new ElimTerm.Proj(xRefTerm, 2);
    var tuple = new IntroTerm.Tuple(ImmutableSeq.of(firstTerm, secondTerm));
    Term res = EtaConversion.simpleEtaReduction(tuple);
    assertTrue(EtaConversion.compareRefTerm(xRefTerm, res));
  }

  // (x.1, (x.1, x.2).2)
  @Test public void nestTupleSimpleEta() {
    var sigmaTerm = new FormTerm.Sigma(ImmutableSeq.of(
      new Term.Param(new LocalVar("A"), FormTerm.Univ.ZERO, false),
      new Term.Param(new LocalVar("A"), FormTerm.Univ.ZERO, false)));
    var xRefTerm = new RefTerm(new LocalVar("x"), sigmaTerm);
    var firstTerm = new ElimTerm.Proj(xRefTerm, 1);
    var secondTerm = new ElimTerm.Proj(xRefTerm, 2);
    var tuple = new IntroTerm.Tuple(ImmutableSeq.of(firstTerm, secondTerm));
    var finalTuple = new IntroTerm.Tuple(ImmutableSeq.of(firstTerm, new ElimTerm.Proj(tuple, 2)));
    Term res = EtaConversion.simpleEtaReduction(finalTuple);
    assertTrue(EtaConversion.compareRefTerm(xRefTerm, res));
  }

  // \x -> f (x.1, x.2)
  @Test public void tupleAndLambdaSimpleEta() {
    var sigmaTerm = new FormTerm.Sigma(ImmutableSeq.of(
      new Term.Param(new LocalVar("A"), FormTerm.Univ.ZERO, false),
      new Term.Param(new LocalVar("B"), FormTerm.Univ.ZERO, false)));
    var xParamTerm = new Term.Param(new LocalVar("x"), sigmaTerm, false);
    var xRefTerm = new RefTerm(new LocalVar("x"), sigmaTerm);
    var fRefTerm = new RefTerm(new LocalVar("f"), FormTerm.Univ.ZERO);
    // construct lambda body: tuple term
    var firstTerm = new ElimTerm.Proj(xRefTerm, 1);
    var secondTerm = new ElimTerm.Proj(xRefTerm, 2);
    var tuple = new IntroTerm.Tuple(ImmutableSeq.of(firstTerm, secondTerm));
    var lambda = IntroTerm.Lambda.make(
      // Params
      ImmutableSeq.of(xParamTerm),
      // Body
      new ElimTerm.App(fRefTerm, new Arg<>(tuple, false))
    );

    Term res = EtaConversion.simpleEtaReduction(lambda);
    assertTrue(EtaConversion.compareRefTerm(fRefTerm, res));
  }
}
