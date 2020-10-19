package org.mzi.core.visitor;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.LamTerm;
import org.mzi.core.term.PiTerm;
import org.mzi.core.term.Term;

public class NormalizeVisitor implements BaseTermVisitor<NormalizeMode> {
  public static final @NotNull NormalizeVisitor INSTANCE = new NormalizeVisitor();

  private NormalizeVisitor() {
  }

  @Override
  public @NotNull Term visitApp(AppTerm.@NotNull Apply term, NormalizeMode mode) {
    var function = term.function();
    if (function instanceof LamTerm lam) return AppTerm.make(lam, visitArg(term.argument(), mode));
    else return AppTerm.make(function, mode == NormalizeMode.WHNF ? term.argument() : visitArg(term.argument(), mode));
  }

  @Override
  public @NotNull Term visitLam(@NotNull LamTerm term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return BaseTermVisitor.super.visitLam(term, mode);
  }

  @Override
  public @NotNull Term visitPi(@NotNull PiTerm term, NormalizeMode mode) {
    if (mode != NormalizeMode.NF) return term;
    else return BaseTermVisitor.super.visitPi(term, mode);
  }
}
