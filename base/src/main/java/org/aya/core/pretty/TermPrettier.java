// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pretty;

import org.aya.api.ref.DefVar;
import org.aya.api.util.Arg;
import org.aya.core.term.*;
import org.aya.pretty.backend.string.StringLink;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.pretty.doc.Styles;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqLike;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @author ice1000, kiva
 * @see org.aya.concrete.pretty.ExprPrettier
 */
public final class TermPrettier implements Term.Visitor<Boolean, Doc> {
  public static final @NotNull TermPrettier INSTANCE = new TermPrettier();
  public static final @NotNull Styles KEYWORD = Style.bold().and().color("aya:Keyword");
  public static final @NotNull Style FN_CALL = Style.color("aya:FnCall");
  public static final @NotNull Style DATA_CALL = Style.color("aya:DataCall");
  public static final @NotNull Style STRUCT_CALL = Style.color("aya:StructCall");
  public static final @NotNull Style CON_CALL = Style.color("aya:ConCall");

  private TermPrettier() {
  }

  @Override public Doc visitRef(@NotNull RefTerm term, Boolean nestedCall) {
    return DefPrettier.plainLink(term.var());
  }

  @Override
  public Doc visitLam(@NotNull LamTerm term, Boolean nestedCall) {
    return Doc.cat(
      Doc.styled(KEYWORD, "\\lam"),
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
      Doc.styled(KEYWORD, "\\Pi"),
      Doc.plain(" "),
      term.param().toDoc(),
      Doc.plain(" -> "),
      term.body().toDoc()
    );
  }

  @Override
  public Doc visitSigma(@NotNull SigmaTerm term, Boolean nestedCall) {
    return Doc.cat(
      Doc.styled(KEYWORD, "\\Sig"),
      Doc.plain(" "),
      visitTele(term.params()),
      Doc.plain(" ** "),
      term.body().toDoc()
    );
  }

  @Override public Doc visitUniv(@NotNull UnivTerm term, Boolean nestedCall) {
    // TODO: level
    return Doc.styled(KEYWORD, "\\Type");
  }

  @Override public Doc visitApp(@NotNull AppTerm term, Boolean nestedCall) {
    return visitCalls(term.fn(), term.arg(), nestedCall);
  }

  @Override public Doc visitFnCall(@NotNull CallTerm.Fn fnCall, Boolean nestedCall) {
    return visitCalls(fnCall.ref(), FN_CALL, fnCall.args(), nestedCall);
  }

  @Override public Doc visitPrimCall(CallTerm.@NotNull Prim prim, Boolean nestedCall) {
    return visitCalls(prim.ref(), FN_CALL, prim.args(), nestedCall);
  }

  @Override public Doc visitDataCall(@NotNull CallTerm.Data dataCall, Boolean nestedCall) {
    return visitCalls(dataCall.ref(), DATA_CALL, dataCall.args(), nestedCall);
  }

  @Override public Doc visitStructCall(@NotNull CallTerm.Struct structCall, Boolean nestedCall) {
    return visitCalls(structCall.ref(), STRUCT_CALL, structCall.args(), nestedCall);
  }

  @Override public Doc visitConCall(@NotNull CallTerm.Con conCall, Boolean nestedCall) {
    return visitCalls(conCall.ref(), CON_CALL, conCall.conArgs(), nestedCall);
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
      Doc.styled(KEYWORD, "\\new"),
      Doc.plain(" { "),
      Doc.hsep(newTerm.params().view().map(t ->
        Doc.hsep(Doc.plain("|"), Doc.plain(t._1), Doc.plain("=>"), t._2.toDoc())
      )),
      Doc.plain(" }")
    );
  }

  @Override
  public Doc visitProj(@NotNull ProjTerm term, Boolean nestedCall) {
    return Doc.cat(term.tup().toDoc(), Doc.plain("."), Doc.plain(String.valueOf(term.ix())));
  }

  @Override
  public Doc visitHole(CallTerm.@NotNull Hole term, Boolean nestedCall) {
    var name = term.ref().name();
    var filling = term.args().isEmpty() ? Doc.empty() : Doc.hsep(term.args().view()
      .map(t -> t.term().toDoc()));
    return Doc.hsep(Doc.plain("{"), filling, Doc.plain(name + "?}"));
  }

  private Doc visitCalls(@NotNull Term fn, @NotNull Arg<@NotNull Term> arg, boolean nestedCall) {
    return visitCalls(fn.toDoc(), Seq.of(arg),
      (term -> term.accept(this, true)), nestedCall);
  }

  private Doc visitCalls(
    @NotNull DefVar<?, ?> fn, @NotNull Style style,
    @NotNull SeqLike<@NotNull Arg<@NotNull Term>> args,
    boolean nestedCall
  ) {
    var hyperLink = Doc.hyperLink(Doc.styled(style, fn.name()), new StringLink("#" + fn.hashCode()), null);
    return visitCalls(hyperLink, args, (term -> term.accept(this, true)), nestedCall);
  }

  public <T> @NotNull Doc visitCalls(
    @NotNull Doc fn, @NotNull SeqLike<@NotNull Arg<@NotNull T>> args,
    @NotNull Function<T, Doc> formatter, boolean nestedCall
  ) {
    if (args.isEmpty()) {
      return fn;
    }
    var call = Doc.cat(
      fn,
      Doc.plain(" "),
      Doc.hsep(args.view()
        .map(arg -> {
          // Do not use `arg.term().toDoc()` because we want to
          // wrap args in parens if we are inside a nested call
          // such as `suc (suc (suc n))`
          var argDoc = formatter.apply(arg.term());
          return arg.explicit()
            ? argDoc
            : Doc.wrap("{", "}", argDoc);
        }))
    );
    return nestedCall ? Doc.wrap("(", ")", call) : call;
  }

  private Doc visitTele(@NotNull SeqLike<Term.Param> telescope) {
    return telescope.isEmpty() ? Doc.empty() : Doc.hsep(telescope.view().map(Term.Param::toDoc));
  }
}
