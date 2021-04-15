// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.concrete.visitor.ConcreteDistiller;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.generic.Matching;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.pretty.doc.Style;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * It's called distiller and it serves as the pretty printer.
 * Credit after <a
 * href="https://github.com/jonsterling/dreamtt/blob/master/frontend/Distiller.ml">Jon Sterling</a>
 *
 * @author ice1000, kiva
 * @see ConcreteDistiller
 */
public final class CoreDistiller implements
  Pat.Visitor<Boolean, Doc>,
  Def.Visitor<Unit, @NotNull Doc>,
  Term.Visitor<Boolean, Doc> {
  public static final @NotNull CoreDistiller INSTANCE = new CoreDistiller();
  public static final @NotNull Style KEYWORD = Style.preset("aya:Keyword");
  public static final @NotNull Style FN_CALL = Style.preset("aya:FnCall");
  public static final @NotNull Style DATA_CALL = Style.preset("aya:DataCall");
  public static final @NotNull Style STRUCT_CALL = Style.preset("aya:StructCall");
  public static final @NotNull Style CON_CALL = Style.preset("aya:ConCall");
  public static final @NotNull Style FIELD_CALL = Style.preset("aya:FieldCall");
  public static final @NotNull Style GENERALIZED = Style.preset("aya:Generalized");

  private CoreDistiller() {
  }

  @Override public Doc visitRef(@NotNull RefTerm term, Boolean nestedCall) {
    var ref = term.var();
    return Doc.linkRef(Doc.plain(ref.name()), ref.hashCode());
  }

  @Override public Doc visitLam(@NotNull IntroTerm.Lambda term, Boolean nestedCall) {
    var doc = Doc.cat(
      Doc.styled(KEYWORD, Doc.symbol("\\")),
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
      Doc.styled(KEYWORD, Doc.symbol("Pi")),
      Doc.plain(" "),
      term.param().toDoc(),
      Doc.symbol(" -> "),
      term.body().toDoc()
    );
    return nestedCall ? Doc.wrap("(", ")", doc) : doc;
  }

  @Override public Doc visitSigma(@NotNull FormTerm.Sigma term, Boolean nestedCall) {
    var doc = Doc.cat(
      Doc.styled(KEYWORD, Doc.symbol("Sig")),
      Doc.plain(" "),
      visitTele(term.params().view().dropLast(1)),
      Doc.plain(" ** "),
      term.params().last().toDoc()
    );
    return nestedCall ? Doc.wrap("(", ")", doc) : doc;
  }

  @Override public Doc visitUniv(@NotNull FormTerm.Univ term, Boolean nestedCall) {
    // TODO: level
    return Doc.styled(KEYWORD, "Type");
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
      Doc.styled(KEYWORD, "new"),
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
      Doc.linkRef(Doc.styled(CoreDistiller.FIELD_CALL, ref.name()), ref.hashCode()));
    return visitCalls(doc, term.fieldArgs(), (n, t) -> t.accept(this, n), nestedCall);
  }

  @Override public Doc visitHole(CallTerm.@NotNull Hole term, Boolean nestedCall) {
    var name = term.ref().name();
    var sol = term.ref().core().body;
    var filling = sol == null ? Doc.plain(name) : sol.toDoc();
    return Doc.hcat(Doc.symbol("{?"), filling, Doc.symbol("?}"));
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
    if (args.isEmpty()) return fn;
    var call = Doc.cat(
      fn,
      Doc.plain(" "),
      Doc.hsep(args.view().map(arg -> {
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

  @Override public Doc visitTuple(Pat.@NotNull Tuple tuple, Boolean nested) {
    boolean ex = tuple.explicit();
    var tup = Doc.wrap(ex ? "(" : "{", ex ? ")" : "}",
      Doc.join(Doc.plain(", "), tuple.pats().stream().map(Pat::toDoc)));
    return tuple.as() == null ? tup
      : Doc.cat(tup, Doc.styled(CoreDistiller.KEYWORD, " as "), Doc.plain(tuple.as().name()));
  }

  @Override public Doc visitBind(Pat.@NotNull Bind bind, Boolean aBoolean) {
    boolean ex = bind.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      plainLinkDef(bind.as()));
  }

  @Override public Doc visitAbsurd(Pat.@NotNull Absurd absurd, Boolean aBoolean) {
    boolean ex = absurd.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.styled(CoreDistiller.KEYWORD, "impossible"));
  }

  @Override public Doc visitPrim(Pat.@NotNull Prim prim, Boolean aBoolean) {
    boolean ex = prim.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}", hyperLink(prim.ref()));
  }

  @Override public Doc visitCtor(Pat.@NotNull Ctor ctor, Boolean nestedCall) {
    var ctorDoc = Doc.cat(hyperLink(ctor.ref()), visitMaybeCtorPatterns(ctor.params(), true, Doc.plain(" ")));
    return ctorDoc(nestedCall, ctor.explicit(), ctorDoc, ctor.as(), ctor.params().isEmpty());
  }

  @NotNull private Doc hyperLink(DefVar<?, ?> ref) {
    return Doc.linkRef(Doc.styled(CoreDistiller.CON_CALL, ref.name()), ref.hashCode());
  }

  public static @NotNull Doc ctorDoc(boolean nestedCall, boolean ex, Doc ctorDoc, LocalVar ctorAs, boolean noParams) {
    boolean as = ctorAs != null;
    var withEx = Doc.wrap(ex ? "" : "{", ex ? "" : "}", ctorDoc);
    var withAs = !as ? withEx :
      Doc.cat(Doc.wrap("(", ")", withEx), Doc.plain(" as "), Doc.plain(ctorAs.name()));
    return !ex && !as ? withAs : nestedCall && !noParams ? Doc.wrap("(", ")", withAs) : withAs;
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Pat> patterns, boolean nestedCall, @NotNull Doc delim) {
    return patterns.isEmpty() ? Doc.empty() : Doc.cat(Doc.plain(" "), Doc.join(delim,
      patterns.view().map(p -> p.accept(this, nestedCall))));
  }

  public Doc matchy(@NotNull Matching<Pat, Term> match) {
    var doc = visitMaybeCtorPatterns(match.patterns(), false, Doc.plain(", "));
    return Doc.cat(doc, Doc.symbol(" => "), match.body().toDoc());
  }


  @Override public Doc visitFn(@NotNull FnDef def, Unit unit) {
    var line1 = Doc.hcat(
      Doc.styled(CoreDistiller.KEYWORD, "def "),
      linkDef(def.ref(), CoreDistiller.FN_CALL),
      visitTele(def.telescope()),
      Doc.plain(" : "), def.result().toDoc());
    return def.body().fold(
      term -> Doc.hcat(line1, Doc.symbol(" => "), term.toDoc()),
      clauses -> Doc.vcat(line1, Doc.nest(2, visitClauses(clauses))));
  }

  /*package-private*/ Doc visitTele(@NotNull ImmutableSeq<Term.Param> telescope) {
    if (telescope.isEmpty()) return Doc.empty();
    var last = telescope.first();
    var buf = Buffer.<Doc>of();
    var names = Buffer.of(last.nameDoc());
    for (var param : telescope.view().drop(1)) {
      if (!Objects.equals(param.type(), last.type())) {
        buf.append(last.toDoc(Doc.hsep(names)));
        names.clear();
        last = param;
      }
      names.append(param.nameDoc());
    }
    buf.append(last.toDoc(Doc.hsep(names)));
    return Doc.cat(Doc.plain(" "), Doc.hsep(buf));
  }

  private Doc visitConditions(Doc line1, @NotNull ImmutableSeq<Matching<Pat, Term>> clauses) {
    if (clauses.isEmpty()) return line1;
    return Doc.vcat(
      Doc.hcat(line1, Doc.symbol(" {")),
      Doc.nest(2, visitClauses(clauses)),
      Doc.symbol("}"));
  }

  private Doc visitClauses(@NotNull ImmutableSeq<Matching<Pat, Term>> clauses) {
    return Doc.vcat(clauses.view()
      .map(this::matchy)
      .map(doc -> Doc.hcat(Doc.plain("|"), doc)));
  }

  @Override public Doc visitData(@NotNull DataDef def, Unit unit) {
    var line1 = Doc.hcat(
      Doc.styled(CoreDistiller.KEYWORD, "data"),
      Doc.plain(" "),
      linkDef(def.ref(), CoreDistiller.DATA_CALL),
      visitTele(def.telescope()),
      Doc.plain(" : "), def.result().toDoc());
    return Doc.vcat(line1, Doc.nest(2, Doc.vcat(
      def.body().view().map(ctor -> ctor.accept(this, Unit.unit())))));
  }

  public static @NotNull Doc linkDef(@NotNull Var ref, @NotNull Style color) {
    return Doc.linkDef(Doc.styled(color, ref.name()), ref.hashCode());
  }

  public static @NotNull Doc plainLinkDef(@NotNull Var ref) {
    return Doc.linkDef(Doc.plain(ref.name()), ref.hashCode());
  }

  @Override public Doc visitCtor(@NotNull DataDef.Ctor ctor, Unit unit) {
    var doc = Doc.cat(
      ctor.coerce() ? Doc.styled(CoreDistiller.KEYWORD, "\\coerce ") : Doc.empty(),
      linkDef(ctor.ref(), CoreDistiller.CON_CALL),
      visitTele(ctor.conTele())
    );
    Doc line1;
    if (ctor.pats().isNotEmpty()) {
      var pats = Doc.join(Doc.plain(", "), ctor.pats().stream().map(Pat::toDoc));
      line1 = Doc.hcat(Doc.plain("| "), pats, Doc.symbol(" => "), doc);
    } else line1 = Doc.hcat(Doc.plain("| "), doc);
    return visitConditions(line1, ctor.clauses());
  }

  @Override public Doc visitStruct(@NotNull StructDef def, Unit unit) {
    return Doc.vcat(Doc.hcat(
      Doc.styled(CoreDistiller.KEYWORD, "struct"),
      Doc.plain(" "),
      linkDef(def.ref(), CoreDistiller.STRUCT_CALL),
      visitTele(def.telescope()),
      Doc.plain(" : "), def.result().toDoc()), Doc.nest(2, Doc.vcat(
      def.fields().view().map(field -> field.accept(this, Unit.unit())))));
  }

  @Override public Doc visitField(@NotNull StructDef.Field field, Unit unit) {
    return visitConditions(Doc.hcat(
      Doc.plain("| "),
      field.coerce() ? Doc.styled(CoreDistiller.KEYWORD, "\\coerce ") : Doc.empty(),
      linkDef(field.ref(), CoreDistiller.FIELD_CALL),
      visitTele(field.fieldTele())
    ), field.clauses());
  }

  @Override public @NotNull Doc visitPrim(@NotNull PrimDef def, Unit unit) {
    return primDoc(def.ref());
  }

  public static @NotNull Doc primDoc(Var ref) {
    return Doc.hcat(
      Doc.styled(CoreDistiller.KEYWORD, "prim "),
      Doc.linkDef(Doc.styled(CoreDistiller.FN_CALL, ref.name()), ref.hashCode())
    );
  }
}
