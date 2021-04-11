// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pretty;

import org.aya.api.ref.DefVar;
import org.aya.api.util.Arg;
import org.aya.core.term.*;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.pretty.doc.Style;
import org.aya.pretty.doc.Styles;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqLike;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

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
  public static final @NotNull Style FIELD_CALL = Style.color("aya:FieldCall");

  private TermPrettier() {
  }

  @Override public Doc visitRef(@NotNull RefTerm term, Boolean nestedCall) {
    return DefPrettier.plainLink(term.var());
  }

  @Override public Doc visitLam(@NotNull IntroTerm.Lambda term, Boolean nestedCall) {
    var doc = Doc.cat(
      Doc.styled(KEYWORD, Doc.symbol("\\lam")),
      Doc.plain(" "),
      term.param().toDoc(),
      Doc.symbol(" => "),
      term.body().toDoc()
    );
    return nestedCall ? Doc.wrap("(", ")", doc) : doc;
  }

  @Override public Doc visitPi(@NotNull FormTerm.Pi term, Boolean nestedCall) {
    // TODO[kiva]: term.co
    var doc = Doc.cat(
      Doc.styled(KEYWORD, Doc.symbol("\\Pi")),
      Doc.plain(" "),
      term.param().toDoc(),
      Doc.symbol(" -> "),
      term.body().toDoc()
    );
    return nestedCall ? Doc.wrap("(", ")", doc) : doc;
  }

  @Override public Doc visitSigma(@NotNull FormTerm.Sigma term, Boolean nestedCall) {
    var doc = Doc.cat(
      Doc.styled(KEYWORD, Doc.symbol("\\Sig")),
      Doc.plain(" "),
      visitTele(term.params().view().dropLast(1)),
      Doc.plain(" ** "),
      term.params().last().toDoc()
    );
    return nestedCall ? Doc.wrap("(", ")", doc) : doc;
  }

  @Override public Doc visitUniv(@NotNull FormTerm.Univ term, Boolean nestedCall) {
    // TODO: level
    return Doc.styled(KEYWORD, "\\Type");
  }

  @Override public Doc visitApp(@NotNull ElimTerm.App term, Boolean nestedCall) {
    return visitCalls(term.of(), term.arg(), nestedCall);
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

  @Override public Doc visitTup(@NotNull IntroTerm.Tuple term, Boolean nestedCall) {
    var items = Doc.join(Doc.plain(", "), term.items().stream()
      .map(Term::toDoc));
    return Doc.cat(Doc.plain("("), items, Doc.plain(")"));
  }

  @Override public Doc visitNew(@NotNull IntroTerm.New newTerm, Boolean aBoolean) {
    return Doc.cat(
      Doc.styled(KEYWORD, "\\new"),
      Doc.symbol(" { "),
      Doc.hsep(newTerm.params().view()
        .map((k, v) -> Doc.hsep(Doc.plain("|"),
          Doc.linkRef(Doc.styled(FIELD_CALL, k.name()), k.hashCode()),
          Doc.symbol("=>"), v.toDoc()))
        .toImmutableSeq()),
      Doc.symbol(" }")
    );
  }

  @Override public Doc visitProj(@NotNull ElimTerm.Proj term, Boolean nestedCall) {
    return Doc.cat(term.of().toDoc(), Doc.symbol("."), Doc.plain(String.valueOf(term.ix())));
  }

  @Override public Doc visitAccess(CallTerm.@NotNull Access term, Boolean nestedCall) {
    var ref = term.ref();
    var doc = Doc.cat(term.of().toDoc(), Doc.symbol("."),
      Doc.linkRef(Doc.styled(TermPrettier.FIELD_CALL, ref.name()), ref.hashCode()));
    return visitCalls(doc, term.fieldArgs(), (n, t) -> t.accept(this, n), nestedCall);
  }

  @Override public Doc visitHole(CallTerm.@NotNull Hole term, Boolean nestedCall) {
    var name = term.ref().name();
    var filling = term.args().isEmpty() ? Doc.empty() : Doc.hsep(term.args().view()
      .map(t -> t.term().toDoc()));
    return Doc.hcat(Doc.symbol("{?"), filling, Doc.plain(name), Doc.symbol("?}"));
  }

  private Doc visitCalls(@NotNull Term fn, @NotNull Arg<@NotNull Term> arg, boolean nestedCall) {
    return visitCalls(fn.toDoc(), Seq.of(arg),
      (nest, term) -> term.accept(this, nest), nestedCall);
  }

  private Doc visitCalls(
    @NotNull DefVar<?, ?> fn, @NotNull Style style,
    @NotNull SeqLike<@NotNull Arg<@NotNull Term>> args,
    boolean nestedCall
  ) {
    var hyperLink = Doc.linkRef(Doc.styled(style, fn.name()), fn.hashCode());
    return visitCalls(hyperLink, args, (nest, term) -> term.accept(this, nest), nestedCall);
  }

  public <T extends Docile> @NotNull Doc visitCalls(
    @NotNull Doc fn, @NotNull SeqLike<@NotNull Arg<@NotNull T>> args,
    @NotNull BiFunction<Boolean, T, Doc> formatter, boolean nestedCall
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
          return arg.explicit()
            ? formatter.apply(true, arg.term())
            : Doc.wrap("{", "}", formatter.apply(false, arg.term()));
        }))
    );
    return nestedCall ? Doc.wrap("(", ")", call) : call;
  }

  private Doc visitTele(@NotNull SeqLike<Term.Param> telescope) {
    return telescope.isEmpty() ? Doc.empty() : Doc.hsep(telescope.view().map(Term.Param::toDoc));
  }
}
