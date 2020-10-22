package org.mzi.core.visitor;

import asia.kala.EmptyTuple;
import asia.kala.Tuple;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.term.*;
import org.mzi.generic.Arg;
import org.mzi.generic.Tele;

import java.util.Optional;

public interface TermConsumer<P> extends Term.Visitor<P, EmptyTuple>, Tele.Visitor<Term, P, EmptyTuple> {
  @Override default EmptyTuple visitNamed(Tele.@NotNull NamedTele<Term> named, P p) {
    return named.next().accept(this, p);
  }

  @Override default EmptyTuple visitTyped(Tele.@NotNull TypedTele<Term> typed, P p) {
    Optional.ofNullable(typed.next()).map(tele -> tele.accept(this, p));
    return typed.type().accept(this, p);
  }

  @Override default EmptyTuple visitLam(@NotNull LamTerm term, P p) {
    term.tele().accept(this, p);
    return term.body().accept(this, p);
  }

  @Override default EmptyTuple visitUniv(@NotNull UnivTerm term, P p) {
    return Tuple.of();
  }

  @Override default EmptyTuple visitDT(@NotNull DT term, P p) {
    return term.telescope().accept(this, p);
  }

  @Override default EmptyTuple visitRef(@NotNull RefTerm term, P p) {
    return Tuple.of();
  }

  default void visitArg(@NotNull Arg<Term> arg, P p) {
    arg.term().accept(this, p);
  }

  @Override default EmptyTuple visitApp(@NotNull AppTerm.Apply term, P p) {
    visitArg(term.arg(), p);
    return term.fn().accept(this, p);
  }

  @Override default EmptyTuple visitFnCall(@NotNull AppTerm.FnCall fnCall, P p) {
    fnCall.args().forEach(arg -> visitArg(arg, p));
    return fnCall.fn().accept(this, p);
  }

  @Override default EmptyTuple visitTup(@NotNull TupTerm term, P p) {
    term.items().forEach(item -> item.accept(this, p));
    return term.accept(this, p);
  }

  @Override default EmptyTuple visitProj(@NotNull ProjTerm term, P p) { return term.tup().accept(this, p); }
}
