// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.value;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.api.ref.LocalVar;
import org.aya.core.Meta;
import org.jetbrains.annotations.NotNull;

public record Unifier(@NotNull MutableMap<Meta, Value> metaCtx) {
  private boolean unify(Value.Segment leftSeg, Value.Segment rightSeg) {
    return switch (leftSeg) {
      case Value.Segment.Apply lApply && rightSeg instanceof Value.Segment.Apply rApply -> unify(lApply.arg().value(), rApply.arg().value());
      case Value.Segment.ProjL ignore && rightSeg instanceof Value.Segment.ProjL -> true;
      case Value.Segment.ProjR ignore && rightSeg instanceof Value.Segment.ProjR -> true;
      case Value.Segment.Access lAccess && rightSeg instanceof Value.Segment.Access rAccess -> lAccess.field() == rAccess.field();
      default -> false;
    };
  }

  private boolean unify(ImmutableSeq<Value.Segment> leftSpine, ImmutableSeq<Value.Segment> rightSpine) {
    return leftSpine.sizeEquals(rightSpine)
      && leftSpine.zip(rightSpine).allMatch(lr -> unify(lr._1, lr._2));
  }

  public boolean unify(Value left, Value right) {
    final var l = left.force();
    final var r = right.force();
    return switch (l) {
      case FormValue.Unit ignore && r instanceof FormValue.Unit -> true;
      case FormValue.Sigma lSigma && r instanceof FormValue.Sigma rSigma -> {
        var v = new RefValue.Neu(new LocalVar(lSigma.param().ref().name()));
        yield unify(lSigma.param().type(), rSigma.param().type())
          && unify(lSigma.func().apply(v), rSigma.func().apply(v));
      }
      case FormValue.Pi lPi && r instanceof FormValue.Pi rPi -> {
        var v = new RefValue.Neu(new LocalVar(lPi.param().ref().name()));
        yield lPi.param().explicit() == rPi.param().explicit()
          && unify(lPi.param().type(), rPi.param().type())
          && unify(lPi.func().apply(v), rPi.func().apply(v));
      }
      case FormValue.Data lData && r instanceof FormValue.Data rData -> {
        yield false;
      }
      case FormValue.Struct lStruct && r instanceof FormValue.Struct rStruct -> {
        yield false;
      }
      case FormValue.Univ lUniv && r instanceof FormValue.Univ rUniv -> {
        yield false;
      }
      case IntroValue.TT ignore && r instanceof IntroValue.TT -> true;
      case IntroValue.Pair lPair && r instanceof IntroValue.Pair rPair -> unify(lPair.left(), rPair.left()) && unify(lPair.right(), rPair.right());
      case IntroValue.Lam lLam && r instanceof IntroValue.Lam rLam -> {
        var v = new RefValue.Neu(new LocalVar(lLam.param().ref().name()));
        yield unify(lLam.func().apply(v), rLam.func().apply(v));
      }
      case RefValue.Neu lNeu && r instanceof RefValue.Neu rNeu -> lNeu.var() == rNeu.var() && unify(lNeu.spine(), rNeu.spine());
      case RefValue.Flex lFlex && r instanceof RefValue.Flex rFlex -> {
        if (lFlex.var() instanceof Meta lMeta && rFlex.var() instanceof Meta rMeta) {
          if (lMeta == rMeta) {
            yield unify(lFlex.spine(), rFlex.spine());
          }
        }
        yield false;
      }
      default -> {
        if (l instanceof IntroValue.Pair || r instanceof IntroValue.Pair) {
          yield unify(l.projL(), r.projL()) && unify(l.projR(), r.projR());
        }
        if (l instanceof IntroValue.Lam lam) {
          var v = new RefValue.Neu(new LocalVar(lam.param().ref().name()));
          var arg = new Value.Arg(v, lam.param().explicit());
          yield unify(lam.func().apply(v), r.apply(arg));
        } else if (r instanceof IntroValue.Lam lam) {
          var v = new RefValue.Neu(new LocalVar(lam.param().ref().name()));
          var arg = new Value.Arg(v, lam.param().explicit());
          yield unify(l.apply(arg), lam.func().apply(v));
        }
        yield false;
      }
    };
  }
}
