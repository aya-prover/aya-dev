// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pretty;

import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.pretty.doc.Doc;
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
      visitParam(term.param()),
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
      visitParam(term.param()),
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
  public Doc visitApp(@NotNull AppTerm.Apply term, Boolean nestedCall) {
    return visitCalls(term.fn(), term.args(), nestedCall);
  }

  @Override
  public Doc visitFnCall(@NotNull AppTerm.FnCall fnCall, Boolean nestedCall) {
    return visitCalls(fnCall.fn(), fnCall.args(), nestedCall);
  }

  @Override
  public Doc visitDataCall(@NotNull AppTerm.DataCall dataCall, Boolean nestedCall) {
    return visitCalls(dataCall.fn(), dataCall.args(), nestedCall);
  }

  @Override
  public Doc visitStructCall(@NotNull AppTerm.StructCall structCall, Boolean nestedCall) {
    return visitCalls(structCall.fn(), structCall.args(), nestedCall);
  }

  @Override public Doc visitConCall(@NotNull AppTerm.ConCall conCall, Boolean nestedCall) {
    return visitCalls(conCall.fn(), conCall.conArgs(), nestedCall);
  }

  @Override
  public Doc visitTup(@NotNull TupTerm term, Boolean nestedCall) {
    var items = Doc.join(Doc.plain(", "), term.items().stream()
      .map(Term::toDoc));
    return Doc.cat(Doc.plain("("), items, Doc.plain(")"));
  }

  @Override
  public Doc visitStruct(@NotNull NewTerm newTerm, Boolean aBoolean) {
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
  public Doc visitHole(AppTerm.@NotNull HoleApp term, Boolean nestedCall) {
    String name = term.var().name();
    Doc filling = term.args().stream()
      .map(t -> t.term().toDoc())
      .reduce(Doc.empty(), Doc::hsep);
    return Doc.hsep(Doc.plain("{"), filling, Doc.plain(name + "?}"));
  }

  private Doc visitCalls(@NotNull Term fn,
                         @NotNull SeqLike<@NotNull Arg<@NotNull Term>> args,
                         boolean nestedCall) {
    return visitCalls(fn.toDoc(), args,
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
      .map(this::visitParam)
      .reduce(Doc.empty(), Doc::hsep);
  }

  private Doc visitParam(@NotNull Term.Param param) {
    return Doc.cat(
      param.explicit() ? Doc.plain("(") : Doc.plain("{"),
      Doc.plain(param.ref().name()),
      Doc.cat(Doc.plain(" : "), param.type().toDoc()),
      param.explicit() ? Doc.plain(")") : Doc.plain("}")
    );
  }
}
