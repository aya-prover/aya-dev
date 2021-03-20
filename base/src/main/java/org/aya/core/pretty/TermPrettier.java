// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pretty;

import org.aya.api.ref.Var;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class TermPrettier implements Term.Visitor<Boolean, Doc> {
  public static final TermPrettier INSTANCE = new TermPrettier();

  @Override
  public Doc visitRef(@NotNull RefTerm term, Boolean nestedCall) {
    return Doc.plain(term.var().name());
  }

  @Override
  public Doc visitLam(@NotNull LamTerm term, Boolean nestedCall) {
    return Doc.cat(
      Doc.plain("\\lam"),
      Doc.plain(" "),
      term.param().toDoc(),
      Doc.plain(" => "),
      term.body().toDoc()
    );
  }

  @Override
  public Doc visitPi(@NotNull PiTerm term, Boolean nestedCall) {
    // TODO[kiva]: term.co
    return Doc.cat(
      Doc.plain("\\Pi"),
      Doc.plain(" "),
      term.param().toDoc(),
      Doc.plain(" -> "),
      term.body().toDoc()
    );
  }

  @Override
  public Doc visitSigma(@NotNull SigmaTerm term, Boolean nestedCall) {
    return Doc.cat(
      Doc.plain("\\Sig"),
      Doc.plain(" "),
      visitTele(term.params()),
      Doc.plain(" ** "),
      term.body().toDoc()
    );
  }

  @Override
  public Doc visitUniv(@NotNull UnivTerm term, Boolean nestedCall) {
    // TODO: level
    return Doc.plain("\\oo-Type");
  }

  @Override
  public Doc visitApp(@NotNull AppTerm term, Boolean nestedCall) {
    return visitCalls(term.fn(), term.arg(), nestedCall);
  }

  @Override
  public Doc visitFnCall(@NotNull CallTerm.Fn fnCall, Boolean nestedCall) {
    return visitCalls(fnCall.ref(), fnCall.args(), nestedCall);
  }

  @Override
  public Doc visitDataCall(@NotNull CallTerm.Data dataCall, Boolean nestedCall) {
    return visitCalls(dataCall.ref(), dataCall.args(), nestedCall);
  }

  @Override public Doc visitStructCall(@NotNull CallTerm.Struct structCall, Boolean nestedCall) {
    return visitCalls(structCall.ref(), structCall.args(), nestedCall);
  }

  @Override public Doc visitConCall(@NotNull CallTerm.Con conCall, Boolean nestedCall) {
    return visitCalls(conCall.ref(), conCall.conArgs(), nestedCall);
  }

  @Override
  public Doc visitTup(@NotNull TupTerm term, Boolean nestedCall) {
    var items = Doc.join(Doc.plain(", "), term.items().stream()
      .map(Term::toDoc));
    return Doc.cat(Doc.plain("("), items, Doc.plain(")"));
  }

  @Override
  public Doc visitNew(@NotNull NewTerm newTerm, Boolean aBoolean) {
    return Doc.cat(
      Doc.plain("\\new { "),
      newTerm.params().stream().map(t ->
        Doc.hsep(Doc.plain("|"), Doc.plain(t._1), Doc.plain("=>"), t._2.toDoc())
      ).reduce(Doc.empty(), Doc::hsep),
      Doc.plain(" }")
    );
  }

  @Override
  public Doc visitProj(@NotNull ProjTerm term, Boolean nestedCall) {
    return Doc.cat(term.tup().toDoc(), Doc.plain("."), Doc.plain(String.valueOf(term.ix())));
  }

  @Override
  public Doc visitHole(CallTerm.@NotNull Hole term, Boolean nestedCall) {
    String name = term.ref().name();
    Doc filling = term.args().stream()
      .map(t -> t.term().toDoc())
      .reduce(Doc.empty(), Doc::hsep);
    return Doc.hsep(Doc.plain("{"), filling, Doc.plain(name + "?}"));
  }

  private Doc visitCalls(@NotNull Term fn,
                         @NotNull Arg<@NotNull Term> arg,
                         boolean nestedCall) {
    return visitCalls(fn.toDoc(), Seq.of(arg),
      (term -> term.accept(this, true)), nestedCall);
  }

  private Doc visitCalls(@NotNull Var fn,
                         @NotNull SeqLike<@NotNull Arg<@NotNull Term>> args,
                         boolean nestedCall) {
    return visitCalls(Doc.plain(fn.name()), args,
      (term -> term.accept(this, true)), nestedCall);
  }

  public <T> @NotNull Doc visitCalls(@NotNull Doc fn,
                                     @NotNull SeqLike<@NotNull Arg<@NotNull T>> args,
                                     @NotNull Function<T, Doc> formatter,
                                     boolean nestedCall) {
    if (args.isEmpty()) {
      return fn;
    }
    var call = Doc.cat(
      fn,
      Doc.plain(" "),
      args.stream()
        .map(arg -> {
          // Do not use `arg.term().toDoc()` because we want to
          // wrap args in parens if we are inside a nested call
          // such as `suc (suc (suc n))`
          var argDoc = formatter.apply(arg.term());
          return arg.explicit()
            ? argDoc
            : Doc.wrap("{", "}", argDoc);
        })
        .reduce(Doc.empty(), Doc::hsep)
    );
    return nestedCall ? Doc.wrap("(", ")", call) : call;
  }

  private Doc visitTele(@NotNull ImmutableSeq<Term.Param> telescope) {
    return telescope.stream()
      .map(Term.Param::toDoc)
      .reduce(Doc.empty(), Doc::hsep);
  }
}
