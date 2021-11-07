// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.distill;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.DefVar;
import org.aya.api.util.Arg;
import org.aya.core.Matching;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.core.visitor.VarConsumer;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

import static org.aya.distill.BaseDistiller.*;

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
  Term.Visitor<Boolean, Doc>,
  BaseDistiller {
  @Override public Doc visitRef(@NotNull RefTerm term, Boolean nestedCall) {
    return varDoc(term.var());
  }

  @Override public Doc visitLam(@NotNull IntroTerm.Lambda term, Boolean nestedCall) {
    var params = DynamicSeq.<Term.Param>create();
    var body = IntroTerm.Lambda.unwrap(term, params);
    Doc bodyDoc;
    // Syntactic eta-contraction
    if (body instanceof CallTerm call && call.ref() instanceof DefVar<?, ?> defVar) {
      var args = visibleArgsOf(call).view();
      while (params.isNotEmpty() && args.isNotEmpty()) {
        var param = params.last();
        if (checkUneta(args, params.last())) {
          args = args.dropLast(1);
          params.removeLast();
        }
      }
      bodyDoc = call instanceof CallTerm.Access access
        ? visitAccessHead(access)
        : visitCalls(visitDefVar(defVar), args, false);
    } else {
      bodyDoc = body.accept(this, false);
    }

    if (!options.showImplicitPats())
      params.removeAll(param -> !param.explicit());
    if (params.isEmpty()) return bodyDoc;

    var list = DynamicSeq.of(Doc.styled(KEYWORD, Doc.symbol("\\")));
    params.forEach(param -> list.append(lambdaParam(param)));
    list.append(Doc.symbol("=>"));
    list.append(bodyDoc);
    var doc = Doc.sep(list);
    return nestedCall ? Doc.parened(doc) : doc;
  }

  /** @return if we can eta-contract the last argument */
  private boolean checkUneta(SeqView<Arg<Term>> args, Term.Param param) {
    var arg = args.last();
    if (arg.explicit() != param.explicit()) return false;
    if (!(arg.term() instanceof RefTerm argRef)) return false;
    if (argRef.var() != param.ref()) return false;
    var counter = new VarConsumer.UsageCounter(param.ref());
    args.dropLast(1).forEach(t -> t.term().accept(counter, Unit.unit()));
    return counter.usageCount() == 0;
  }

  private ImmutableSeq<Arg<Term>> visibleArgsOf(CallTerm call) {
    return call instanceof CallTerm.Con con
      ? con.conArgs() : call instanceof CallTerm.Access access
      ? access.fieldArgs() : call.args();
  }

  @Override public Doc visitPi(@NotNull FormTerm.Pi term, Boolean nestedCall) {
    if (!options.showImplicitPats() && !term.param().explicit()) {
      return term.body().accept(this, nestedCall);
    }
    var doc = Doc.sep(
      Doc.styled(KEYWORD, Doc.symbol("Pi")),
      term.param().toDoc(options),
      Doc.symbol("->"),
      term.body().accept(this, false)
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
    var fn = Doc.styled(KEYWORD, "Type");
    if (!options.showLevels()) return fn;
    return visitCalls(fn, Seq.of(term.sort()).view().map(t -> new Arg<>(t, true)),
      (nest, t) -> t.toDoc(options), nestedCall);
  }

  @Override public Doc visitApp(@NotNull ElimTerm.App term, Boolean nestedCall) {
    return visitCalls(term.of().accept(this, false), Seq.of(term.arg()), nestedCall);
  }

  @Override public Doc visitFnCall(@NotNull CallTerm.Fn fnCall, Boolean nestedCall) {
    return visitCalls(linkRef(fnCall.ref(), FN_CALL), fnCall.args(), nestedCall);
  }

  @Override public Doc visitPrimCall(CallTerm.@NotNull Prim prim, Boolean nestedCall) {
    return visitCalls(linkRef(prim.ref(), FN_CALL), prim.args(), nestedCall);
  }

  @Override public Doc visitDataCall(@NotNull CallTerm.Data dataCall, Boolean nestedCall) {
    return visitCalls(linkRef(dataCall.ref(), DATA_CALL), dataCall.args(), nestedCall);
  }

  @Override public Doc visitStructCall(@NotNull CallTerm.Struct structCall, Boolean nestedCall) {
    return visitCalls(linkRef(structCall.ref(), STRUCT_CALL), structCall.args(), nestedCall);
  }

  @Override public Doc visitConCall(@NotNull CallTerm.Con conCall, Boolean nestedCall) {
    return visitCalls(linkRef(conCall.ref(), CON_CALL), conCall.conArgs(), nestedCall);
  }

  @Override public Doc visitTup(@NotNull IntroTerm.Tuple term, Boolean nestedCall) {
    return Doc.parened(Doc.commaList(term.items().view()
      .map(t -> t.accept(this, false))));
  }

  @Override public Doc visitNew(@NotNull IntroTerm.New newTerm, Boolean aBoolean) {
    return Doc.sep(
      Doc.styled(KEYWORD, "new"),
      Doc.symbol("{"),
      Doc.sep(newTerm.params().view()
        .map((k, v) -> Doc.sep(Doc.symbol("|"),
          linkRef(k, FIELD_CALL),
          Doc.symbol("=>"), v.accept(this, false)))
        .toImmutableSeq()),
      Doc.symbol("}")
    );
  }

  @Override public Doc visitProj(@NotNull ElimTerm.Proj term, Boolean nestedCall) {
    return Doc.cat(term.of().accept(this, false), Doc.symbol("."), Doc.plain(String.valueOf(term.ix())));
  }

  @Override public Doc visitAccess(CallTerm.@NotNull Access term, Boolean nestedCall) {
    return visitCalls(visitAccessHead(term), term.fieldArgs(), nestedCall);
  }

  @NotNull private Doc visitAccessHead(CallTerm.@NotNull Access term) {
    return Doc.cat(term.of().accept(this, true), Doc.symbol("."),
      linkRef(term.ref(), FIELD_CALL));
  }

  @Override public Doc visitHole(CallTerm.@NotNull Hole term, Boolean nestedCall) {
    var name = term.ref();
    var inner = varDoc(name);
    if (options.inlineMetas())
      return visitCalls(inner, term.args(), nestedCall);
    return Doc.wrap("{?", "?}",
      visitCalls(inner, term.args(), false));
  }

  @Override
  public Doc visitFieldRef(@NotNull RefTerm.Field term, Boolean aBoolean) {
    return linkRef(term.ref(), FIELD_CALL);
  }

  @Override public Doc visitError(@NotNull ErrorTerm term, Boolean aBoolean) {
    var doc = term.description().toDoc(options);
    return !term.isReallyError() ? doc : Doc.angled(doc);
  }

  private Doc visitCalls(
    @NotNull Doc fn, @NotNull SeqLike<@NotNull Arg<@NotNull Term>> args,
    boolean nestedCall
  ) {
    return visitCalls(fn, args, (nest, term) -> term.accept(this, nest), nestedCall);
  }

  @Override public Doc visitTuple(Pat.@NotNull Tuple tuple, Boolean nested) {
    var tup = Doc.licit(tuple.explicit(),
      Doc.commaList(tuple.pats().view().map(pat -> pat.accept(this, false))));
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

  public Doc visitMaybeCtorPatterns(SeqLike<Pat> patterns, boolean nestedCall, @NotNull Doc delim) {
    var pats = options.showImplicitPats() ? patterns : patterns.view().filter(Pat::explicit);
    return Doc.emptyIf(pats.isEmpty(), () -> Doc.cat(Doc.ONE_WS, Doc.join(delim,
      pats.view().map(p -> p.accept(this, nestedCall)))));
  }

  @Override public Doc visitFn(@NotNull FnDef def, Unit unit) {
    var line1 = DynamicSeq.of(Doc.styled(KEYWORD, "def"),
      linkDef(def.ref(), FN_CALL),
      visitTele(def.telescope()),
      Doc.symbol(":"),
      def.result().accept(this, false));
    return def.body.fold(
      term -> Doc.sep(Doc.sepNonEmpty(line1), Doc.symbol("=>"), term.accept(this, false)),
      clauses -> Doc.vcat(Doc.sepNonEmpty(line1), Doc.nest(2, visitClauses(clauses))));
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
    var line1 = DynamicSeq.of(Doc.styled(KEYWORD, "data"),
      linkDef(def.ref(), DATA_CALL),
      visitTele(def.telescope()),
      Doc.symbol(":"),
      def.result().accept(this, false));
    return Doc.vcat(Doc.sepNonEmpty(line1), Doc.nest(2, Doc.vcat(
      def.body.view().map(ctor -> ctor.accept(this, Unit.unit())))));
  }

  @Override public Doc visitCtor(@NotNull CtorDef ctor, Unit unit) {
    var doc = Doc.sepNonEmpty(coe(ctor.coerce),
      linkDef(ctor.ref(), CON_CALL),
      visitTele(ctor.selfTele));
    Doc line1;
    if (ctor.pats.isNotEmpty()) {
      var pats = Doc.commaList(ctor.pats.view().map(pat -> pat.accept(this, false)));
      line1 = Doc.sep(Doc.symbol("|"), pats, Doc.symbol("=>"), doc);
    } else line1 = Doc.sep(Doc.symbol("|"), doc);
    return visitConditions(line1, ctor.clauses);
  }

  @Override public Doc visitStruct(@NotNull StructDef def, Unit unit) {
    return Doc.vcat(Doc.sepNonEmpty(Doc.styled(KEYWORD, "struct"),
      linkDef(def.ref(), STRUCT_CALL),
      visitTele(def.telescope()),
      Doc.symbol(":"),
      def.result().accept(this, false)
    ), Doc.nest(2, Doc.vcat(
      def.fields.view().map(field -> field.accept(this, Unit.unit())))));
  }

  @Override public Doc visitField(@NotNull FieldDef field, Unit unit) {
    return visitConditions(Doc.sepNonEmpty(Doc.symbol("|"),
      coe(field.coerce),
      linkDef(field.ref(), FIELD_CALL),
      visitTele(field.selfTele),
      Doc.symbol(":"),
      field.result.accept(this, false)), field.clauses);
  }

  @Override public @NotNull Doc visitPrim(@NotNull PrimDef def, Unit unit) {
    return primDoc(def.ref());
  }
}
