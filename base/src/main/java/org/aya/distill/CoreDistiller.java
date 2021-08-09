// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.distill;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.tuple.Unit;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.core.Matching;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.generic.Level;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * It's called distiller, and it serves as the pretty printer.
 * Credit after <a
 * href="https://github.com/jonsterling/dreamtt/blob/master/frontend/Distiller.ml">Jon Sterling</a>
 *
 * @author ice1000, kiva
 * @see ConcreteDistiller
 */
public record CoreDistiller(@NotNull DistillerOptions options) implements
  Pat.Visitor<Boolean, Doc>,
  Def.Visitor<Unit, @NotNull Doc>,
  Term.Visitor<Boolean, Doc> {
  public static final @NotNull Style KEYWORD = Style.preset("aya:Keyword");
  public static final @NotNull Style FN_CALL = Style.preset("aya:FnCall");
  public static final @NotNull Style DATA_CALL = Style.preset("aya:DataCall");
  public static final @NotNull Style STRUCT_CALL = Style.preset("aya:StructCall");
  public static final @NotNull Style CON_CALL = Style.preset("aya:ConCall");
  public static final @NotNull Style FIELD_CALL = Style.preset("aya:FieldCall");
  public static final @NotNull Style GENERALIZED = Style.preset("aya:Generalized");

  @Override public Doc visitRef(@NotNull RefTerm term, Boolean nestedCall) {
    return varDoc(term.var());
  }

  public static @NotNull Doc varDoc(@NotNull Var ref) {
    return Doc.linkRef(Doc.plain(ref.name()), ref.hashCode());
  }

  @Override public Doc visitLam(@NotNull IntroTerm.Lambda term, Boolean nestedCall) {
    var doc = Doc.sep(
      Doc.styled(KEYWORD, Doc.symbol("\\")),
      term.param().toDoc(options),
      Doc.symbol("=>"),
      term.body().toDoc(options)
    );
    return nestedCall ? Doc.parened(doc) : doc;
  }

  @Override public Doc visitPi(@NotNull FormTerm.Pi term, Boolean nestedCall) {
    var doc = Doc.sep(
      Doc.styled(KEYWORD, Doc.symbol("Pi")),
      term.param().toDoc(options),
      Doc.symbol("->"),
      term.body().toDoc(options)
    );
    return nestedCall ? Doc.parened(doc) : doc;
  }

  @Override public Doc visitSigma(@NotNull FormTerm.Sigma term, Boolean nestedCall) {
    var doc = Doc.sep(
      Doc.styled(KEYWORD, Doc.symbol("Sig")),
      visitTele(term.params().view().dropLast(1)),
      Doc.symbol("**"),
      term.params().last().toDoc(options)
    );
    return nestedCall ? Doc.parened(doc) : doc;
  }

  @Override public Doc visitUniv(@NotNull FormTerm.Univ term, Boolean nestedCall) {
    var sort = term.sort();
    var onlyH = sort.onlyH();
    if (onlyH instanceof Level.Constant<Sort.LvlVar> t) {
      if (t.value() == 1) return univDoc(nestedCall, "Prop", sort.uLevel());
      if (t.value() == 2) return univDoc(nestedCall, "Set", sort.uLevel());
    } else if (onlyH instanceof Level.Infinity<Sort.LvlVar> t)
      return univDoc(nestedCall, "ooType", sort.uLevel());
    return visitCalls(Doc.styled(KEYWORD, "Type"),
      Seq.of(sort.hLevel(), sort.uLevel()).view().map(Arg::explicit),
      (nest, t) -> t.toDoc(), nestedCall);
  }

  public static @NotNull Doc univDoc(Boolean nestedCall, String head, @NotNull Docile lvl) {
    return visitCalls(Doc.styled(KEYWORD, head),
      Seq.of(Arg.explicit(lvl)),
      (nc, l) -> l.toDoc(), nestedCall);
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
    return Doc.parened(Doc.commaList(term.items().view()
      .map(t -> t.toDoc(options))));
  }

  @Override public Doc visitNew(@NotNull IntroTerm.New newTerm, Boolean aBoolean) {
    return Doc.sep(
      Doc.styled(KEYWORD, "new"),
      Doc.symbol("{"),
      Doc.sep(newTerm.params().view()
        .map((k, v) -> Doc.sep(Doc.symbol("|"),
          linkRef(k, FIELD_CALL),
          Doc.symbol("=>"), v.toDoc(options)))
        .toImmutableSeq()),
      Doc.symbol("}")
    );
  }

  @Override public Doc visitProj(@NotNull ElimTerm.Proj term, Boolean nestedCall) {
    return Doc.cat(term.of().toDoc(options), Doc.symbol("."), Doc.plain(String.valueOf(term.ix())));
  }

  @Override public Doc visitAccess(CallTerm.@NotNull Access term, Boolean nestedCall) {
    var ref = term.ref();
    var doc = Doc.cat(term.of().toDoc(options), Doc.symbol("."),
      linkRef(ref, FIELD_CALL));
    return visitCalls(doc, term.fieldArgs(), (n, t) -> t.accept(this, n), nestedCall);
  }

  @Override public Doc visitHole(CallTerm.@NotNull Hole term, Boolean nestedCall) {
    var name = term.ref();
    var sol = name.core().body;
    var inner = sol == null ? varDoc(name) : sol.toDoc(options);
    return Doc.wrap("{?", "?}",
      visitCalls(inner, term.args(), (nest, t) -> t.accept(this, nest), nestedCall));
  }

  @Override public Doc visitError(@NotNull ErrorTerm term, Boolean aBoolean) {
    return !term.isReallyError() ? term.description() : Doc.angled(term.description());
  }

  private Doc visitCalls(@NotNull Term fn, @NotNull Arg<@NotNull Term> arg, boolean nestedCall) {
    return visitCalls(fn.toDoc(options), Seq.of(arg),
      (nest, term) -> term.accept(this, nest), nestedCall);
  }

  private Doc visitCalls(
    @NotNull DefVar<?, ?> fn, @NotNull Style style,
    @NotNull SeqLike<@NotNull Arg<@NotNull Term>> args,
    boolean nestedCall
  ) {
    var hyperLink = linkRef(fn, style);
    return visitCalls(hyperLink, args, (nest, term) -> term.accept(this, nest), nestedCall);
  }

  public static <T extends Docile> @NotNull Doc visitCalls(
    @NotNull Doc fn, @NotNull SeqLike<@NotNull Arg<@NotNull T>> args,
    @NotNull BiFunction<Boolean, T, Doc> formatter, boolean nestedCall
  ) {
    if (args.isEmpty()) return fn;
    var call = Doc.sep(
      fn, Doc.sep(args.view().map(arg -> {
        // Do not use `arg.term().toDoc()` because we want to
        // wrap args in parens if we are inside a nested call
        // such as `suc (suc (suc n))`
        return arg.explicit()
          ? formatter.apply(true, arg.term())
          : Doc.braced(formatter.apply(false, arg.term()));
      }))
    );
    return nestedCall ? Doc.parened(call) : call;
  }

  private Doc visitTele(@NotNull SeqLike<Term.Param> telescope) {
    return Doc.sep(telescope.view().map(param -> param.toDoc(options)));
  }

  @Override public Doc visitTuple(Pat.@NotNull Tuple tuple, Boolean nested) {
    boolean ex = tuple.explicit();
    var tup = Doc.wrap(ex ? "(" : "{", ex ? ")" : "}",
      Doc.commaList(tuple.pats().view().map(Pat::toDoc)));
    return tuple.as() == null ? tup
      : Doc.sep(tup, Doc.styled(KEYWORD, "as"), linkDef(tuple.as()));
  }

  @Override public Doc visitBind(Pat.@NotNull Bind bind, Boolean aBoolean) {
    var doc = linkDef(bind.as());
    return bind.explicit() ? doc : Doc.braced(doc);
  }

  @Override public Doc visitAbsurd(Pat.@NotNull Absurd absurd, Boolean aBoolean) {
    var doc = Doc.styled(KEYWORD, "impossible");
    return absurd.explicit() ? doc : Doc.braced(doc);
  }

  @Override public Doc visitPrim(Pat.@NotNull Prim prim, Boolean aBoolean) {
    var link = linkRef(prim.ref(), CON_CALL);
    return prim.explicit() ? link : Doc.braced(link);
  }

  @Override public Doc visitCtor(Pat.@NotNull Ctor ctor, Boolean nestedCall) {
    var ctorDoc = Doc.cat(linkRef(ctor.ref(), CON_CALL), visitMaybeCtorPatterns(ctor.params(), true, Doc.ONE_WS));
    return ctorDoc(nestedCall, ctor.explicit(), ctorDoc, ctor.as(), ctor.params().isEmpty());
  }

  public static @NotNull Doc ctorDoc(boolean nestedCall, boolean ex, Doc ctorDoc, LocalVar ctorAs, boolean noParams) {
    boolean as = ctorAs != null;
    var withEx = ex ? ctorDoc : Doc.braced(ctorDoc);
    var withAs = !as ? withEx :
      Doc.sep(Doc.parened(withEx), Doc.plain("as"), linkDef(ctorAs));
    return !ex && !as ? withAs : nestedCall && !noParams ? Doc.parened(withAs) : withAs;
  }

  public Doc visitMaybeCtorPatterns(SeqLike<Pat> patterns, boolean nestedCall, @NotNull Doc delim) {
    return Doc.emptyIf(patterns.isEmpty(), () -> Doc.cat(Doc.ONE_WS, Doc.join(delim,
      patterns.view().map(p -> p.accept(this, nestedCall)))));
  }

  @Override public Doc visitFn(@NotNull FnDef def, Unit unit) {
    var line1 = Buffer.of(Doc.styled(KEYWORD, "def"),
      linkDef(def.ref(), FN_CALL),
      visitTele(def.telescope()),
      Doc.symbol(":"),
      def.result().toDoc(options));
    return def.body.fold(
      term -> Doc.sep(Doc.sepNonEmpty(line1), Doc.symbol("=>"), term.toDoc(options)),
      clauses -> Doc.vcat(Doc.sepNonEmpty(line1), Doc.nest(2, visitClauses(clauses))));
  }

  /*package-private*/ Doc visitTele(@NotNull ImmutableSeq<Term.Param> telescope) {
    if (telescope.isEmpty()) return Doc.empty();
    var last = telescope.first();
    var buf = Buffer.<Doc>of();
    var names = Buffer.of(last.nameDoc());
    for (var param : telescope.view().drop(1)) {
      if (!Objects.equals(param.type(), last.type())) {
        buf.append(last.toDoc(Doc.sep(names), options));
        names.clear();
        last = param;
      }
      names.append(param.nameDoc());
    }
    buf.append(last.toDoc(Doc.sep(names), options));
    return Doc.sep(buf);
  }

  private Doc visitConditions(Doc line1, @NotNull ImmutableSeq<Matching> clauses) {
    if (clauses.isEmpty()) return line1;
    return Doc.vcat(
      Doc.sep(line1, Doc.symbol("{")),
      Doc.nest(2, visitClauses(clauses)),
      Doc.symbol("}"));
  }

  private Doc visitClauses(@NotNull ImmutableSeq<Matching> clauses) {
    return Doc.vcat(clauses.view()
      .map(matching -> matching.toDoc(options))
      .map(doc -> Doc.cat(Doc.symbol("|"), doc)));
  }

  @Override public Doc visitData(@NotNull DataDef def, Unit unit) {
    var line1 = Buffer.of(Doc.styled(KEYWORD, "data"),
      linkDef(def.ref(), DATA_CALL),
      visitTele(def.telescope()),
      Doc.symbol(":"),
      def.result().toDoc(options));
    return Doc.vcat(Doc.sepNonEmpty(line1), Doc.nest(2, Doc.vcat(
      def.body.view().map(ctor -> ctor.accept(this, Unit.unit())))));
  }

  public static @NotNull Doc linkDef(@NotNull Var ref, @NotNull Style color) {
    return Doc.linkDef(Doc.styled(color, ref.name()), ref.hashCode());
  }

  public static @NotNull Doc linkRef(@NotNull Var ref, @NotNull Style color) {
    return Doc.linkRef(Doc.styled(color, ref.name()), ref.hashCode());
  }

  public static @NotNull Doc linkDef(@NotNull Var ref) {
    return Doc.linkDef(Doc.plain(ref.name()), ref.hashCode());
  }

  @Override public Doc visitCtor(@NotNull CtorDef ctor, Unit unit) {
    var doc = Doc.sepNonEmpty(coe(ctor.coerce),
      linkDef(ctor.ref(), CON_CALL),
      visitTele(ctor.selfTele));
    Doc line1;
    if (ctor.pats.isNotEmpty()) {
      var pats = Doc.commaList(ctor.pats.view().map(pat -> pat.toDoc(options)));
      line1 = Doc.sep(Doc.symbol("|"), pats, Doc.symbol("=>"), doc);
    } else line1 = Doc.sep(Doc.symbol("|"), doc);
    return visitConditions(line1, ctor.clauses);
  }

  public static @NotNull Doc coe(boolean coerce) {
    return coerce ? Doc.styled(KEYWORD, "coerce") : Doc.empty();
  }

  @Override public Doc visitStruct(@NotNull StructDef def, Unit unit) {
    return Doc.vcat(Doc.sepNonEmpty(Doc.styled(KEYWORD, "struct"),
      linkDef(def.ref(), STRUCT_CALL),
      visitTele(def.telescope()),
      Doc.plain(":"),
      def.result().toDoc(options)
    ), Doc.nest(2, Doc.vcat(
      def.fields.view().map(field -> field.accept(this, Unit.unit())))));
  }

  @Override public Doc visitField(@NotNull FieldDef field, Unit unit) {
    return visitConditions(Doc.sep(Doc.symbol("|"),
      coe(field.coerce),
      linkDef(field.ref(), FIELD_CALL),
      visitTele(field.selfTele)), field.clauses);
  }

  @Override public @NotNull Doc visitPrim(@NotNull PrimDef def, Unit unit) {
    return primDoc(def.ref());
  }

  public static @NotNull Doc primDoc(Var ref) {
    return Doc.sep(Doc.styled(KEYWORD, "prim"), linkDef(ref, FN_CALL));
  }
}
