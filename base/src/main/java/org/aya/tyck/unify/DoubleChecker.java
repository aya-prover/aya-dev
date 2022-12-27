// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.unify;

import org.aya.core.term.*;
import org.aya.generic.util.NormalizeMode;
import org.aya.tyck.Synthesizer;
import org.aya.tyck.error.BadExprError;
import org.aya.tyck.error.TupleError;
import org.aya.tyck.unify.TermComparator.Sub;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record DoubleChecker(@NotNull Unifier unifier, @NotNull Synthesizer synthesizer, @NotNull Sub lr,
                            @NotNull Sub rl) {
  public DoubleChecker {
    assert unifier.cmp == Ordering.Lt;
  }

  public DoubleChecker(@NotNull Unifier unifier) {
    this(unifier, new Sub(), new Sub());
  }

  public DoubleChecker(@NotNull Unifier unifier, @NotNull Sub lr, @NotNull Sub rl) {
    this(unifier, new Synthesizer(unifier.state, unifier.ctx), lr, rl);
  }

  private @NotNull Term whnf(Term x) {
    return x.normalize(unifier.state, NormalizeMode.WHNF);
  }

  public boolean inherit(@NotNull Term preterm, @NotNull Term expected) {
    return switch (preterm) {
      case ErrorTerm term -> true;
      case SigmaTerm sigma -> sigma.params().view()
        .allMatch(param -> inherit(param.type(), expected));
      case LamTerm(var param, var body)when whnf(expected) instanceof PiTerm(var tparam, var tbody) ->
        unifier.ctx.with(param.ref(), tparam.type(), () -> inherit(body, tbody));
      case LamTerm lambda -> {
        unifier.reporter.report(new BadExprError(lambda, unifier.pos, expected));
        yield false;
      }
      case PartialTerm(var par, var rhsTy)when whnf(expected) instanceof PartialTyTerm(var ty, var phi) -> {
        if (!unifier.compareRestr(par.restr(), phi)) yield false;
        yield compare(rhsTy, ty, null);
      }
      case TupTerm(var items)when whnf(expected) instanceof SigmaTerm sigma -> {
        var res = sigma.check(items, (e, t) -> {
          if (!inherit(e.term(), t)) return ErrorTerm.unexpected(e.term());
          return e.term();
        });
        if (res == null) unifier.reporter.report(new TupleError.ElemMismatchError(
          unifier.pos, sigma.params().size(), items.size()));
        yield res != null && res.items().allMatch(i -> !(i.term() instanceof ErrorTerm));
      }
      case PiTerm(var dom, var cod) -> {
        var domSort = synthesizer.press(dom.type());
        // TODO^: make sure the above is a type. Need an extra "isType"
        yield inherit(cod, expected);
      }
      case default -> compare(synthesizer.press(preterm), expected, null);
    };
  }

  private boolean compare(@NotNull Term lhs, @NotNull Term expected, @Nullable Term type) {
    return unifier.compare(lhs, expected, lr, rl, type);
  }
}
