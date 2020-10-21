package org.mzi.core.visitor;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.tele.Tele;
import org.mzi.core.term.*;
import org.mzi.generic.Arg;

import java.util.Optional;

/**
 * @author ice1000
 */
public interface TermFixpoint<P> extends Term.Visitor<P, @NotNull Term>, Tele.Visitor<P, @NotNull Tele> {
  @Override default @NotNull Tele visitNamed(Tele.@NotNull NamedTele named, P p) {
    var next = named.next().accept(this, p);
    if (next == named.next()) return named;
    return new Tele.NamedTele(named.ref(), next);
  }

  @Override default @NotNull Tele visitTyped(Tele.@NotNull TypedTele typed, P p) {
    var next = Optional.ofNullable(typed.next()).map(tele -> tele.accept(this, p)).orElse(null);
    var type = typed.type().accept(this, p);
    if (next == typed.next() && type == typed.type()) return typed;
    return new Tele.TypedTele(typed.ref(), type, typed.explicit(), next);
  }

  @Override default @NotNull Term visitLam(@NotNull LamTerm term, P p) {
    var telescope = term.telescope().accept(this, p);
    var body = term.body().accept(this, p);
    if (telescope == term.telescope() && body == term.body()) return term;
    return new LamTerm(telescope, body);
  }

  @Override default @NotNull Term visitUniv(@NotNull UnivTerm term, P p) {
    return term;
  }

  @Override default @NotNull Term visitDT(@NotNull DT term, P p) {
    var telescope = term.telescope().accept(this, p);
    if (telescope == term.telescope()) return term;
    return new DT(telescope, term.kind());
  }

  @Override default @NotNull Term visitRef(@NotNull RefTerm term, P p) {
    return term;
  }

  default @NotNull Arg<Term> visitArg(@NotNull Arg<Term> arg, P p) {
    var term = arg.term().accept(this, p);
    if (term == arg.term()) return arg;
    return new Arg<>(term, arg.explicit());
  }

  @Override default @NotNull Term visitApp(AppTerm.@NotNull Apply term, P p) {
    var function = term.fn().accept(this, p);
    var arg = visitArg(term.arg(), p);
    if (function == term.fn() && arg == term.arg()) return term;
    return new AppTerm.Apply(function, arg);
  }

  @Override default @NotNull Term visitFnCall(AppTerm.@NotNull FnCall fnCall, P p) {
    var args = fnCall.args().map(arg -> visitArg(arg, p));
    if (fnCall.args().sameElements(args)) return fnCall;
    return new AppTerm.FnCall(fnCall.fnRef(), args);
  }
}
