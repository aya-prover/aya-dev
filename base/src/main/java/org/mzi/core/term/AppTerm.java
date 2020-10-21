package org.mzi.core.term;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Ref;
import org.mzi.core.subst.TermSubst;
import org.mzi.generic.Arg;
import org.mzi.util.Decision;

/**
 * @author ice1000
 * @see org.mzi.core.term.AppTerm#make(Term, Arg)
 */
public sealed interface AppTerm extends Term {
  @NotNull Term fn();
  @NotNull ImmutableSeq<@NotNull Arg<Term>> args();

  @Override default @NotNull Decision whnf() {
    if (fn() instanceof LamTerm) return Decision.NO;
    return fn().whnf();
  }

  @Contract(pure = true) static @NotNull Term make(@NotNull Term f, @NotNull Arg<Term> arg) {
    if (!(f instanceof LamTerm lam)) return new Apply(f, arg);
    var tele = lam.telescope();
    var next = tele.next();
    return (next != null ? new LamTerm(next, lam.body()) : lam.body()).subst(new TermSubst(tele.ref(), arg.term()));
  }

  record Apply(
    @NotNull Term fn,
    @NotNull Arg<Term> arg
  ) implements AppTerm {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }

    @Contract(" -> new")
    @Override public @NotNull ImmutableSeq<@NotNull Arg<Term>> args() {
      return ImmutableSeq.of(arg());
    }
  }
}
