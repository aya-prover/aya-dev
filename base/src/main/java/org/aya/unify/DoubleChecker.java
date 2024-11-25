// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.unify;

import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.xtt.DimTyTerm;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.TyckState;
import org.aya.tyck.error.BadExprError;
import org.aya.tyck.tycker.Contextful;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.error.Panic;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @apiNote {@link Unifier#localCtx()} should be the same object as {@link Synthesizer#localCtx()}
 */
public record DoubleChecker(
  @NotNull Unifier unifier,
  @NotNull Synthesizer synthesizer
) implements Stateful, Contextful, Problematic {
  public DoubleChecker(@NotNull Unifier unifier) { this(unifier, new Synthesizer(unifier.nameGen, unifier)); }

  public boolean inherit(@NotNull Term preterm, @NotNull Term expected) {
    return switch (preterm) {
      case ErrorTerm _ -> true;
      case PiTerm(var pParam, var pBody) -> {
        if (!(whnf(expected) instanceof SortTerm expectedTy)) yield Panic.unreachable();
        yield synthesizer.inheritPiDom(pParam, expectedTy) && subscoped(pParam, param ->
          inherit(pBody.apply(param), expectedTy));
      }
      case SigmaTerm (var sParam, var sBody) -> inherit(sParam, expected) && subscoped(sParam, param ->
        inherit(sBody.apply(param), expected));
      case TupTerm(var lhs, var rhs) when whnf(expected) instanceof SigmaTerm (var lhsT, var rhsTClos) ->
        inherit(lhs, lhsT) && inherit(rhs, rhsTClos.apply(lhs));
      case LamTerm(var body) -> switch (whnf(expected)) {
        case PiTerm(var dom, var cod) -> subscoped(dom, param ->
          inherit(body.apply(param), cod.apply(param)));
        case EqTerm eq -> subscoped(DimTyTerm.INSTANCE, param -> {
          // TODO: check boundaries
          return inherit(body.apply(param), eq.A().apply(param));
        });
        default -> failF(new BadExprError(preterm, unifier.pos, expected));
      };
      case TupTerm _ -> failF(new BadExprError(preterm, unifier.pos, expected));
      default -> unifier.compare(synthesizer.synthDontNormalize(preterm), expected, null);
    };
  }
  private boolean failF(@NotNull Problem problem) {
    fail(problem);
    return false;
  }

  @Override public @NotNull LocalCtx localCtx() { return unifier.localCtx(); }
  @Override public @NotNull LocalCtx setLocalCtx(@NotNull LocalCtx ctx) { return unifier.setLocalCtx(ctx); }
  @Override public @NotNull TyckState state() { return unifier.state(); }
  @Override public @NotNull Reporter reporter() { return unifier.reporter(); }
  public <R> R subscoped(@NotNull Term type, @NotNull Function<LocalVar, R> action) {
    return unifier.subscoped(type, action);
  }
}
