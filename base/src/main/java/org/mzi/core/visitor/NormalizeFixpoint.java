package org.mzi.core.visitor;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.term.*;
import org.mzi.ref.EvalRef;

public final class NormalizeFixpoint implements TermFixpoint<NormalizeMode> {
  public static final @NotNull NormalizeFixpoint INSTANCE = new NormalizeFixpoint();

  private NormalizeFixpoint() {
  }

  @Override
  public @NotNull Term visitApp(AppTerm.@NotNull Apply term, NormalizeMode mode) {
    var function = term.function();
    if (function instanceof LamTerm lam) return AppTerm.make(lam, visitArg(term.argument(), mode));
    else return AppTerm.make(function, mode == NormalizeMode.WHNF ? term.argument() : visitArg(term.argument(), mode));
  }

  @Override
  public @NotNull Term visitRef(@NotNull RefTerm term, NormalizeMode mode) {
    if (!(term.ref() instanceof EvalRef eval)) return term;
    return eval.term().accept(this, mode);
  }

  @Override
  public @NotNull Term visitLam(@NotNull LamTerm term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return TermFixpoint.super.visitLam(term, mode);
  }

  @Override
  public @NotNull Term visitDT(@NotNull DT term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return TermFixpoint.super.visitDT(term, mode);
  }
}
