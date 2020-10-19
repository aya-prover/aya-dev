package org.mzi.core.term;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.subst.TermSubst;
import org.mzi.util.Decision;

/**
 * @author ice1000
 * @see org.mzi.core.term.AppTerm#make(Term, Arg)
 */
public sealed interface AppTerm extends Term {
  @NotNull Term function();
  @NotNull ImmutableSeq<@NotNull Arg> arguments();

  @Override default @NotNull Decision whnf() {
    if (function() instanceof LamTerm) return Decision.NO;
    return Decision.MAYBE;
  }

  @Contract(pure = true) static @NotNull Term make(@NotNull Term f, @NotNull Arg arg) {
    if (!(f instanceof LamTerm lam)) return new Apply(f, arg);
    var tele = lam.telescope();
    var next = tele.next();
    return (next != null ? new LamTerm(next, lam.body()) : lam.body()).subst(new TermSubst(tele.ref(), arg.term()));
  }

  record Apply(
    @NotNull Term function,
    @NotNull Arg argument
  ) implements AppTerm {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }

    @Contract(" -> new")
    @Override public @NotNull ImmutableSeq<@NotNull Arg> arguments() {
      return ImmutableSeq.of(argument());
    }
  }
}
