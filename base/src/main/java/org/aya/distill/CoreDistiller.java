// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.distill;

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
import org.aya.pretty.doc.Style;
import org.aya.util.binop.OpDecl;
import org.jetbrains.annotations.NotNull;

/**
 * It's called distiller, and it serves as the pretty printer.
 * Credit after <a
 * href="https://github.com/jonsterling/dreamtt/blob/master/frontend/Distiller.ml">Jon Sterling</a>
 *
 * @author ice1000, kiva
 * @see ConcreteDistiller
 */
public class CoreDistiller extends BaseDistiller implements
  Def.Visitor<Unit, @NotNull Doc>,
  Term.Visitor<BaseDistiller.Outer, Doc> {
  public CoreDistiller(@NotNull DistillerOptions options) {
    super(options);
  }

  @Override public Doc visitRef(@NotNull RefTerm term, Outer outer) {
    return varDoc(term.var());
  }

  @Override public Doc visitLam(@NotNull IntroTerm.Lambda term, Outer outer) {
    var params = DynamicSeq.of(term.param());
    var body = IntroTerm.Lambda.unwrap(term.body(), params);
    Doc bodyDoc;
    // Syntactic eta-contraction
    if (body instanceof CallTerm call && call.ref() instanceof DefVar<?, ?> defVar) {
      var args = visibleArgsOf(call).view();
      while (params.isNotEmpty() && args.isNotEmpty()) {
        var param = params.last();
        if (checkUneta(args, params.last())) {
          args = args.dropLast(1);
          params.removeLast();
        } else break;
      }
      if (call instanceof CallTerm.Access access) bodyDoc = visitAccessHead(access);
      else {
        var style = chooseStyle(defVar);
        bodyDoc = style != null
          ? visitCalls(defVar, style, args, Outer.Free)
          : visitCalls(false, varDoc(defVar), args, Outer.Free);
      }
    } else {
      bodyDoc = body.accept(this, Outer.Free);
    }

    if (!options.map.get(DistillerOptions.Key.ShowImplicitPats))
      params.retainAll(Term.Param::explicit);
    if (params.isEmpty()) return bodyDoc;

    var list = DynamicSeq.of(Doc.styled(KEYWORD, Doc.symbol("\\")));
    params.forEach(param -> list.append(lambdaParam(param)));
    list.append(Doc.symbol("=>"));
    list.append(bodyDoc);
    var doc = Doc.sep(list);
    // Add paren when it's in a spine
    return checkParen(outer, doc, Outer.AppSpine);
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

  @Override public Doc visitPi(@NotNull FormTerm.Pi term, Outer outer) {
    if (!options.map.get(DistillerOptions.Key.ShowImplicitPats) && !term.param().explicit()) {
      return term.body().accept(this, outer);
    }
    var params = DynamicSeq.of(term.param());
    var body = FormTerm.unpi(term.body(), params);
    var doc = Doc.sep(
      Doc.styled(KEYWORD, Doc.symbol("Pi")),
      visitTele(params),
      Doc.symbol("->"),
      body.accept(this, Outer.Codomain)
    );
    // Add paren when it's not free or a codomain
    return checkParen(outer, doc, Outer.BinOp);
  }

  @Override public Doc visitSigma(@NotNull FormTerm.Sigma term, Outer outer) {
    var doc = Doc.sep(
      Doc.styled(KEYWORD, Doc.symbol("Sig")),
      visitTele(term.params().view().dropLast(1)),
      Doc.symbol("**"),
      term.params().last().toDoc(options)
    );
    // Same as Pi
    return checkParen(outer, doc, Outer.BinOp);
  }

  @Override public Doc visitUniv(@NotNull FormTerm.Univ term, Outer outer) {
    var fn = Doc.styled(KEYWORD, "Type");
    if (!options.map.get(DistillerOptions.Key.ShowLevels)) return fn;
    return visitCalls(false, fn, (nest, t) -> t.toDoc(options), outer,
      SeqView.of(new Arg<>(o -> term.sort().toDoc(), true))
    );
  }

  @Override public Doc visitApp(@NotNull ElimTerm.App term, Outer outer) {
    var args = DynamicSeq.of(term.arg());
    var head = ElimTerm.unapp(term.of(), args);
    if (head instanceof RefTerm.Field fieldRef) return visitCalls(fieldRef.ref(), FIELD_CALL, args, outer);
    return visitCalls(false, head.accept(this, Outer.AppHead), args.view(), outer);
  }

  @Override public Doc visitFnCall(@NotNull CallTerm.Fn fnCall, Outer outer) {
    return visitCalls(fnCall.ref(), FN_CALL, fnCall.args(), outer);
  }

  @Override public Doc visitPrimCall(CallTerm.@NotNull Prim prim, Outer outer) {
    return visitCalls(prim.ref(), FN_CALL, prim.args(), outer);
  }

  @Override public Doc visitDataCall(@NotNull CallTerm.Data dataCall, Outer outer) {
    return visitCalls(dataCall.ref(), DATA_CALL, dataCall.args(), outer);
  }

  @Override public Doc visitStructCall(@NotNull CallTerm.Struct structCall, Outer outer) {
    return visitCalls(structCall.ref(), STRUCT_CALL, structCall.args(), outer);
  }

  @Override public Doc visitConCall(@NotNull CallTerm.Con conCall, Outer outer) {
    return visitCalls(conCall.ref(), CON_CALL, conCall.conArgs(), outer);
  }

  @Override public Doc visitTup(@NotNull IntroTerm.Tuple term, Outer outer) {
    return Doc.parened(Doc.commaList(term.items().view()
      .map(t -> t.accept(this, Outer.Free))));
  }

  @Override public Doc visitNew(@NotNull IntroTerm.New newTerm, Outer outer) {
    return Doc.cblock(Doc.styled(KEYWORD, "new"), 2,
      Doc.vcat(newTerm.params().view()
        .map((k, v) -> Doc.sep(Doc.symbol("|"),
          linkRef(k, FIELD_CALL),
          Doc.symbol("=>"), v.accept(this, Outer.Free)))
        .toImmutableSeq()));
  }

  @Override public Doc visitProj(@NotNull ElimTerm.Proj term, Outer outer) {
    return Doc.cat(term.of().accept(this, Outer.ProjHead), Doc.symbol("."), Doc.plain(String.valueOf(term.ix())));
  }

  @Override public Doc visitAccess(CallTerm.@NotNull Access term, Outer outer) {
    return visitCalls(false, visitAccessHead(term), term.fieldArgs().view(), outer);
  }

  @NotNull private Doc visitAccessHead(CallTerm.@NotNull Access term) {
    return Doc.cat(term.of().accept(this, Outer.ProjHead), Doc.symbol("."),
      linkRef(term.ref(), FIELD_CALL));
  }

  @Override public Doc visitHole(CallTerm.@NotNull Hole term, Outer outer) {
    var name = term.ref();
    var inner = varDoc(name);
    if (options.map.get(DistillerOptions.Key.InlineMetas))
      return visitCalls(false, inner, term.args().view(), outer);
    return Doc.wrap("{?", "?}",
      visitCalls(false, inner, term.args().view(), Outer.Free));
  }

  @Override
  public Doc visitFieldRef(@NotNull RefTerm.Field term, Outer outer) {
    return linkRef(term.ref(), FIELD_CALL);
  }

  @Override public Doc visitError(@NotNull ErrorTerm term, Outer outer) {
    var doc = term.description().toDoc(options);
    return !term.isReallyError() ? doc : Doc.angled(doc);
  }

  @Override public Doc visitMetaPat(RefTerm.@NotNull MetaPat metaPat, Outer outer) {
    return Doc.plain("<metaPat>");
  }

  private Doc visitCalls(
    @NotNull DefVar<?, ?> var, @NotNull Style style,
    @NotNull SeqLike<@NotNull Arg<@NotNull Term>> args, Outer outer
  ) {
    return visitCalls(var.concrete instanceof OpDecl decl && decl.opInfo() != null,
      linkRef(var, style), args.view(), outer);
  }

  private Doc visitCalls(
    boolean infix, @NotNull Doc fn,
    @NotNull SeqView<@NotNull Arg<@NotNull Term>> args, Outer outer
  ) {
    return visitCalls(infix, fn, (nest, term) -> term.accept(this, nest), outer, args);
  }

  public Doc visitPat(@NotNull Pat pat, Outer outer) {
    return switch (pat) {
      case Pat.Meta meta-> {
        var sol = meta.solution().value;
        yield sol != null ? visitPat(sol, outer) : Doc.plain("<meta>");
      }
      case Pat.Bind bind -> Doc.bracedUnless(linkDef(bind.as()), bind.explicit());
      case Pat.Prim prim -> Doc.bracedUnless(linkRef(prim.ref(), CON_CALL), prim.explicit());
      case Pat.Ctor ctor -> {
        var pats = options.map.get(DistillerOptions.Key.ShowImplicitPats) ? ctor.params().view() : ctor.params().view().filter(Pat::explicit);
        var ctorDoc = visitCalls(ctor.ref(), CON_CALL, pats.map(Pat::toArg), outer);
        yield ctorDoc(outer, ctor.explicit(), ctorDoc, ctor.as(), ctor.params().isEmpty());
      }
      case Pat.Absurd absurd -> Doc.bracedUnless(Doc.styled(KEYWORD, "impossible"), absurd.explicit());
      case Pat.Tuple tuple -> {
        var tup = Doc.licit(tuple.explicit(),
          Doc.commaList(tuple.pats().view().map(sub -> visitPat(sub, Outer.Free))));
        yield tuple.as() == null ? tup
          : Doc.sep(tup, Doc.styled(KEYWORD, "as"), linkDef(tuple.as()));
      }
    };
  }

  public Doc visitMaybeCtorPatterns(SeqLike<Pat> patterns, Outer outer, @NotNull Doc delim) {
    var pats = options.map.get(DistillerOptions.Key.ShowImplicitPats) ? patterns : patterns.view().filter(Pat::explicit);
    return Doc.emptyIf(pats.isEmpty(), () -> Doc.cat(Doc.ONE_WS, Doc.join(delim,
      pats.view().map(p -> visitPat(p, outer)))));
  }

  @Override public Doc visitFn(@NotNull FnDef def, Unit unit) {
    var line1 = DynamicSeq.of(Doc.styled(KEYWORD, "def"),
      linkDef(def.ref(), FN_CALL),
      visitTele(def.telescope()),
      Doc.symbol(":"),
      def.result().accept(this, Outer.Free));
    def.modifiers.forEach(m -> line1.insert(0, Doc.styled(KEYWORD, m.keyword)));
    return def.body.fold(
      term -> Doc.sep(Doc.sepNonEmpty(line1), Doc.symbol("=>"), term.accept(this, Outer.Free)),
      clauses -> Doc.vcat(Doc.sepNonEmpty(line1), Doc.nest(2, visitClauses(clauses))));
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
      def.result().accept(this, Outer.Free));
    return Doc.vcat(Doc.sepNonEmpty(line1), Doc.nest(2, Doc.vcat(
      def.body.view().map(ctor -> ctor.accept(this, Unit.unit())))));
  }

  @Override public Doc visitCtor(@NotNull CtorDef ctor, Unit unit) {
    var doc = Doc.sepNonEmpty(coe(ctor.coerce),
      linkDef(ctor.ref(), CON_CALL),
      visitTele(ctor.selfTele));
    Doc line1;
    if (ctor.pats.isNotEmpty()) {
      var pats = Doc.commaList(ctor.pats.view().map(pat -> visitPat(pat, Outer.Free)));
      line1 = Doc.sep(Doc.symbol("|"), pats, Doc.symbol("=>"), doc);
    } else line1 = Doc.sep(Doc.symbol("|"), doc);
    return Doc.cblock(line1, 2, visitClauses(ctor.clauses));
  }

  @Override public Doc visitStruct(@NotNull StructDef def, Unit unit) {
    return Doc.vcat(Doc.sepNonEmpty(Doc.styled(KEYWORD, "struct"),
      linkDef(def.ref(), STRUCT_CALL),
      visitTele(def.telescope()),
      Doc.symbol(":"),
      def.result().accept(this, Outer.Free)
    ), Doc.nest(2, Doc.vcat(
      def.fields.view().map(field -> field.accept(this, Unit.unit())))));
  }

  @Override public Doc visitField(@NotNull FieldDef field, Unit unit) {
    return Doc.cblock(Doc.sepNonEmpty(Doc.symbol("|"),
      coe(field.coerce),
      linkDef(field.ref(), FIELD_CALL),
      visitTele(field.selfTele),
      Doc.symbol(":"),
      field.result.accept(this, Outer.Free)), 2, visitClauses(field.clauses));
  }

  @Override public @NotNull Doc visitPrim(@NotNull PrimDef def, Unit unit) {
    return primDoc(def.ref());
  }
}
