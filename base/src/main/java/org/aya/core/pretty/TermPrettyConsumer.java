// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pretty;

import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public class TermPrettyConsumer implements Term.Visitor<Unit, Doc> {
  public static final TermPrettyConsumer INSTANCE = new TermPrettyConsumer();

  @Override
  public Doc visitRef(@NotNull RefTerm term, Unit unit) {
    return Doc.plain(term.var().name());
  }

  @Override
  public Doc visitLam(@NotNull LamTerm term, Unit unit) {
    return Doc.cat(
      Doc.plain("\\lam"),
      Doc.plain(" "),
      visitParam(term.param()),
      Doc.plain(" => "),
      term.body().toDoc()
    );
  }

  @Override
  public Doc visitPi(@NotNull PiTerm term, Unit unit) {
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
  public Doc visitSigma(@NotNull SigmaTerm term, Unit unit) {
    return Doc.cat(
      Doc.plain("\\Sig"),
      Doc.plain(" "),
      visitTele(term.params()),
      Doc.plain(" ** "),
      term.body().toDoc()
    );
  }

  @Override
  public Doc visitUniv(@NotNull UnivTerm term, Unit unit) {
    // TODO: level
    return Doc.plain("\\oo-Type");
  }

  @Override
  public Doc visitApp(@NotNull AppTerm.Apply term, Unit unit) {
    return visitCalls(term.fn(), term.args());
  }

  @Override
  public Doc visitFnCall(@NotNull AppTerm.FnCall fnCall, Unit unit) {
    return visitCalls(fnCall.fn(), fnCall.args());
  }

  @Override
  public Doc visitDataCall(@NotNull AppTerm.DataCall dataCall, Unit unit) {
    return visitCalls(dataCall.fn(), dataCall.args());
  }

  @Override public Doc visitConCall(@NotNull AppTerm.ConCall conCall, Unit unit) {
    return visitCalls(conCall.fn(), conCall.conArgs());
  }

  @Override
  public Doc visitTup(@NotNull TupTerm term, Unit unit) {
    var items = term.items().stream()
      .map(Term::toDoc)
      .reduce(Doc.empty(), (acc, doc) -> Doc.join(Doc.plain(","), acc, doc));
    return Doc.cat(Doc.plain("("), items, Doc.plain(")"));
  }

  @Override
  public Doc visitProj(@NotNull ProjTerm term, Unit unit) {
    return Doc.cat(term.tup().toDoc(), Doc.plain("."), Doc.plain(String.valueOf(term.ix())));
  }

  @Override
  public Doc visitHole(AppTerm.@NotNull HoleApp term, Unit unit) {
    String name = term.var().name();
    Doc filling = term.args().stream()
      .map(t -> t.term().toDoc())
      .reduce(Doc.empty(), Doc::hsep);
    return Doc.hsep(Doc.plain("{"), filling, Doc.plain(name + "?}"));
  }

  private Doc visitCalls(@NotNull Term fn,
                         @NotNull SeqLike<@NotNull ? extends @NotNull Arg<? extends Term>> args) {
    if (args.isEmpty()) {
      return fn.toDoc();
    }
    return Doc.cat(
      fn.toDoc(),
      Doc.plain(" "),
      args.stream()
        .map(arg -> arg.explicit()
          ? arg.term().toDoc()
          : Doc.cat(Doc.plain("{"), arg.term().toDoc(), Doc.plain("}")))
        .reduce(Doc.empty(), Doc::hsep)
    );
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
