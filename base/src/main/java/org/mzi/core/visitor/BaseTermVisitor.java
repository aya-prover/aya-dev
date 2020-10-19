package org.mzi.core.visitor;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.tele.Tele;
import org.mzi.core.term.*;

import java.util.Optional;

/**
 * @author ice1000
 */
public interface BaseTermVisitor<P> extends Term.Visitor<P, @NotNull Term>, Tele.Visitor<P, @NotNull Tele> {
  default @NotNull Tele visitTele(@NotNull Tele tele, P p) {
    return tele.accept(this, p);
  }

  @Override default @NotNull Tele visitNamed(Tele.@NotNull NamedTele named, P p) {
    return new Tele.NamedTele(named.ref(), named.next().accept(this, p));
  }

  @Override default @NotNull Tele visitTyped(Tele.@NotNull TypedTele typed, P p) {
    var next = Optional.ofNullable(typed.next()).map(tele -> tele.accept(this, p)).orElse(null);
    return new Tele.TypedTele(typed.ref(), typed.type().accept(this, p), typed.explicit(), next);
  }

  @Override default @NotNull Term visitLam(@NotNull LamTerm term, P p) {
    return new LamTerm(term.telescope().accept(this, p), term.body().accept(this, p));
  }

  @Override default @NotNull Term visitUniv(@NotNull UnivTerm term, P p) {
    return term;
  }

  @Override default @NotNull Term visitPi(@NotNull PiTerm term, P p) {
    return new PiTerm(term.telescope().accept(this, p));
  }

  @Override default @NotNull Term visitRef(@NotNull RefTerm term, P p) {
    return term;
  }

  default @NotNull Arg visitArg(@NotNull Arg arg, P p) {
    return new Arg(arg.term().accept(this, p), arg.explicit());
  }

  @Override default @NotNull Term visitApp(@NotNull AppTerm.Apply term, P p) {
    return new AppTerm.Apply(term.function().accept(this, p), visitArg(term.argument(), p));
  }
}
