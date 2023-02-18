// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.unify;

import org.aya.core.meta.MetaInfo;
import org.aya.core.term.*;
import org.aya.generic.util.NormalizeMode;
import org.aya.tyck.error.BadExprError;
import org.aya.tyck.error.TupleError;
import org.aya.tyck.unify.TermComparator.Sub;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;

public record DoubleChecker(
  @NotNull Unifier unifier, @NotNull Synthesizer synthesizer,
  @NotNull Sub lr, @NotNull Sub rl
) {
  public DoubleChecker {
    assert unifier.cmp == Ordering.Lt;
  }

  public DoubleChecker(@NotNull Unifier unifier) {
    this(unifier, new Sub(), new Sub());
  }

  public DoubleChecker(@NotNull Unifier unifier, @NotNull Sub lr, @NotNull Sub rl) {
    this(unifier, unifier.synthesizer(), lr, rl);
  }

  private @NotNull Term whnf(Term x) {
    return x.normalize(unifier.state, NormalizeMode.WHNF);
  }

  public boolean inherit(@NotNull Term preterm, @NotNull Term expected) {
    if (expected instanceof MetaTerm) {
      var lhs = synthesizer().synthesize(preterm);
      if (lhs == null) {
        assert false : "No rules -- unsure if reachable";
        return false;
      }
      return compare(lhs, expected);
    }
    return switch (preterm) {
      case ErrorTerm term -> true;
      case SigmaTerm sigma -> sigma.params().view()
        .allMatch(param -> inherit(param.type(), expected));
      case LamTerm(var param, var body) when whnf(expected) instanceof PiTerm pi ->
        unifier.ctx.with(param.ref(), pi.param().type(), () ->
          inherit(body, pi.substBody(new RefTerm(param.ref()))));
      case LamTerm lambda -> {
        unifier.reporter.report(new BadExprError(lambda, unifier.pos, expected));
        yield false;
      }
      // TODO[ice]: checkBoundaries
      case PLamTerm(var params, var body) when whnf(expected) instanceof PathTerm path ->
        unifier.ctx.withIntervals(params.view(), () ->
          inherit(body, path.substBody(params)));
      case PartialTerm(var par, var rhsTy) when whnf(expected) instanceof PartialTyTerm(var ty, var phi) -> {
        if (!PartialTerm.impliesCof(phi, par.restr(), unifier.state)) yield false;
        yield compare(rhsTy, ty);
      }
      case TupTerm(var items) when whnf(expected) instanceof SigmaTerm sigma -> {
        var res = sigma.check(items, (e, t) -> {
          if (!inherit(e.term(), t)) return ErrorTerm.unexpected(e.term());
          return e.term();
        });
        if (res == null) unifier.reporter.report(new TupleError.ElemMismatchError(
          unifier.pos, sigma.params().size(), items.size()));
        yield res != null && res.items().allMatch(i -> !(i.term() instanceof ErrorTerm));
      }
      case PiTerm(var dom, var cod) -> {
        if (!(whnf(expected) instanceof SortTerm sort)) yield Synthesizer.unreachable(expected);
        if (!synthesizer.inheritPiDom(dom.type(), sort)) yield false;
        yield unifier.ctx.with(dom, () -> inherit(cod, sort));
      }
      case MetaTerm meta when meta.ref().info instanceof MetaInfo.AnyType -> {
        var newMeta = meta.ref().clone(new MetaInfo.Result(expected));
        unifier.solveMeta(meta, new MetaTerm(newMeta, meta.contextArgs(), meta.args()), lr, rl, expected);
        yield true;
      }
      case default -> compare(synthesizer.press(preterm), expected);
    };
  }

  private boolean compare(@NotNull Term lhs, @NotNull Term expected) {
    return unifier.compare(lhs, expected, lr, rl, null);
  }
}
