// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.unify;

import org.aya.generic.term.DTKind;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.xtt.DimTyTerm;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.syntax.core.term.xtt.PartialTerm;
import org.aya.syntax.core.term.xtt.PartialTyTerm;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.MetaVar;
import org.aya.tyck.TyckState;
import org.aya.tyck.error.DoubleCheckError;
import org.aya.tyck.error.UnifyInfo;
import org.aya.tyck.tycker.AbstractTycker;
import org.aya.tyck.tycker.Contextful;
import org.aya.tyck.tycker.Unifiable;
import org.aya.util.Ordering;
import org.aya.util.Panic;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

/// @apiNote [#localCtx()] should be the same object as [#localCtx()]
/// @apiNote Should only be called in [Unifier]
/// @implSpec Since [Unifier] already reports error when this returns false,
/// We should avoid reporting _basic_ type errors here, but providing like
/// _smaller_ type errors in bidirectional typing rules should still be helpful.
public record DoubleChecker(
  @NotNull Unifier unifier,
  @NotNull Synthesizer synthesizer
) implements Contextful, Unifiable {
  public DoubleChecker(@NotNull Unifier unifier) { this(unifier, new Synthesizer(unifier.nameGen, unifier)); }

  public boolean inherit(@NotNull Term preterm, @NotNull Term expected) {
    return switch (preterm) {
      case ErrorTerm _ -> true;
      case DepTypeTerm(var kind, var pParam, var pBody) -> switch (kind) {
        case Pi -> {
          if (!(whnf(expected) instanceof SortTerm expectedTy)) yield Panic.unreachable();
          var b = synthesizer.inheritPiDom(pParam, expectedTy);
          try (var scope = subscope(pParam)) {
            var param = scope.var();
            yield b && inherit(pBody.apply(param), expectedTy);
          }
        }
        case Sigma -> {
          var b = inherit(pParam, expected);
          try (var scope = subscope(pParam)) {
            var param = scope.var();
            yield b && inherit(pBody.apply(param), expected);
          }
        }
      };
      case TupTerm(var lhs, var rhs) when whnf(expected) instanceof
        DepTypeTerm(var kind, var lhsT, var rhsTClos) && kind == DTKind.Sigma ->
        inherit(lhs, lhsT) && inherit(rhs, rhsTClos.apply(lhs));
      case LamTerm(var body) -> switch (whnf(expected)) {
        case DepTypeTerm(var kind, var dom, var cod) when kind == DTKind.Pi -> {
          try (var scope = subscope(dom)) {
            var param = scope.var();
            yield inherit(body.apply(param), cod.apply(param));
          }
        }
        case EqTerm eq -> {
          try (var scope = subscope(DimTyTerm.INSTANCE)) {
            var param = scope.var();
            if (!inherit(body.apply(param), eq.A().apply(param)))
              yield failF(new DoubleCheckError.RuleError(preterm, unifier.pos, expected));
            yield checkBoundaries(eq, body, unifier.pos, comparison ->
              new DoubleCheckError.BoundaryError(unifier.pos, new UnifyInfo(state()), comparison));
          }
        }
        default -> failF(new DoubleCheckError.RuleError(preterm, unifier.pos, expected));
      };
      case TupTerm _ -> failF(new DoubleCheckError.RuleError(preterm, unifier.pos, expected));
      case MetaCall(var ref, var args) when !(ref.req() instanceof MetaVar.OfType) -> {
        var newMeta = new MetaCall(new MetaVar(
          ref.name(), ref.pos(), ref.ctxSize(), new MetaVar.OfType(expected), false), args);
        // Intends for solving meta only, should always success
        unifier.compare(preterm, newMeta, null);
        yield true;
      }
      // Ideally we would imitate the let behavior in ExprTycker, but that requires too much change.
      // Hope we never reach this -- once this becomes a bottleneck in the slightest way we should
      // add `localLet` to this class.
      case LetTerm let -> inherit(let.make(), expected);
      case PartialTerm(var element) -> whnf(expected) instanceof PartialTyTerm(var r, var s, var A)
        ? withConnection(whnf(r), whnf(s), () -> inherit(element, A))
        : failF(new DoubleCheckError.RuleError(preterm, unifier.pos, expected));

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
  public AbstractTycker.@NotNull SubscopedVar subscope(@NotNull Term type) { return unifier.subscope(type); }
  @Override public @NotNull TermComparator unifier(@NotNull SourcePos pos, @NotNull Ordering order) {
    return unifier.derive(pos, order);
  }
}
