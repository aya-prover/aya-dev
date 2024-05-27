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

/**
 * @apiNote {@link Unifier#localCtx()} should be the same object as {@link Synthesizer#localCtx()}
 */
public record DoubleChecker(
  @NotNull Unifier unifier,
  @NotNull Synthesizer synthesizer
) implements Stateful, Contextful, Problematic {
  public DoubleChecker(@NotNull Unifier unifier) { this(unifier, new Synthesizer(unifier)); }

  public boolean inherit(@NotNull Term preterm, @NotNull Term expected) {
    return switch (preterm) {
      case ErrorTerm _ -> true;
      case PiTerm(var pParam, var pBody) -> {
        if (!(whnf(expected) instanceof SortTerm expectedTy)) yield Panic.unreachable();
        yield synthesizer.inheritPiDom(pParam, expectedTy) && subscoped(() -> {
          var param = putIndex(pParam);
          return inherit(pBody.apply(param), expectedTy);
        });
      }
      case SigmaTerm sigma -> {
        if (!(whnf(expected) instanceof SortTerm expectedTy)) yield Panic.unreachable();
        yield subscoped(() -> sigma.view(i -> new FreeTerm(putIndex(i)))
          .allMatch(param -> inherit(param, expectedTy)));
      }
      case TupTerm(var elems) when whnf(expected) instanceof SigmaTerm sigmaTy -> {
        // This is not an assertion because the input is not guaranteed to be well-typed
        if (!elems.sizeEquals(sigmaTy.params())) yield false;

        yield sigmaTy.check(elems, (elem, param) -> {
          if (inherit(whnf(elem), param)) return elem;
          return null;
        }).isOk();
      }
      case LamTerm(var body) -> subscoped(() -> switch (whnf(expected)) {
        case PiTerm(var dom, var cod) -> {
          var param = putIndex(dom);
          yield inherit(body.apply(param), cod.apply(param));
        }
        case EqTerm eq -> {
          var param = putIndex(DimTyTerm.INSTANCE);
          // TODO: check boundaries
          yield inherit(body.apply(param), eq.A().apply(param));
        }
        default -> failF(new BadExprError(preterm, unifier.pos, expected));
      });
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
  public @NotNull LocalVar putIndex(@NotNull Term type) { return unifier.putIndex(type); }
}
