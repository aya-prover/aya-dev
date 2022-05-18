// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.distill;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.tuple.Unit;
import org.aya.core.Matching;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.core.visitor.VarConsumer;
import org.aya.generic.Arg;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.util.StringEscapeUtil;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

/**
 * It's called distiller, and it serves as the pretty printer.
 * Credit after <a href="https://github.com/jonsterling/dreamtt/blob/main/frontend/Distiller.ml">Jon Sterling</a>
 *
 * @author ice1000, kiva
 * @see ConcreteDistiller
 */
public class CoreDistiller extends BaseDistiller<Term> {
  public CoreDistiller(@NotNull DistillerOptions options) {
    super(options);
  }

  @Override public @NotNull Doc term(@NotNull Outer outer, @NotNull Term preterm) {
    return switch (preterm) {
      case RefTerm term -> varDoc(term.var());
      case CallTerm.Hole term -> {
        var name = term.ref();
        var inner = varDoc(name);
        var showImplicits = options.map.get(DistillerOptions.Key.ShowImplicitArgs);
        if (options.map.get(DistillerOptions.Key.InlineMetas))
          yield visitCalls(false, inner, term.args().view(), outer, showImplicits);
        yield Doc.wrap("{?", "?}",
          visitCalls(false, inner, term.args().view(), Outer.Free, showImplicits));
      }
      case IntroTerm.Tuple term -> Doc.parened(Doc.commaList(term.items().view().map(t -> term(Outer.Free, t))));
      case CallTerm.Con conCall -> visitArgsCalls(conCall.ref(), CON_CALL, conCall.conArgs(), outer);
      case CallTerm.Fn fnCall -> visitArgsCalls(fnCall.ref(), FN_CALL, fnCall.args(), outer);
      case FormTerm.Sigma term -> {
        var last = term.params().last();
        var doc = Doc.sep(
          Doc.styled(KEYWORD, Doc.symbol("Sig")),
          visitTele(term.params().dropLast(1), last.type(), Term::findUsages),
          Doc.symbol("**"),
          justType(last, Outer.Codomain)
        );
        // Same as Pi
        yield checkParen(outer, doc, Outer.BinOp);
      }
      case IntroTerm.Lambda term -> {
        var params = MutableList.of(term.param());
        var body = IntroTerm.Lambda.unwrap(term.body(), params::append);
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
              ? visitArgsCalls(defVar, style, args, Outer.Free)
              : visitCalls(false, varDoc(defVar), args, Outer.Free,
              options.map.get(DistillerOptions.Key.ShowImplicitArgs));
          }
        } else bodyDoc = term(Outer.Free, body);

        if (!options.map.get(DistillerOptions.Key.ShowImplicitPats))
          params.retainAll(Term.Param::explicit);
        if (params.isEmpty()) yield bodyDoc;

        var list = MutableList.of(Doc.styled(KEYWORD, Doc.symbol("\\")));
        params.forEach(param -> list.append(lambdaParam(param)));
        list.append(Doc.symbol("=>"));
        list.append(bodyDoc);
        var doc = Doc.sep(list);
        // Add paren when it's in a spine
        yield checkParen(outer, doc, Outer.AppSpine);
      }
      case FormTerm.Univ term -> {
        var fn = Doc.styled(KEYWORD, "Type");
        if (!options.map.get(DistillerOptions.Key.ShowLevels)) yield fn;
        yield visitCalls(false, fn, (nest, t) -> t.toDoc(options), outer,
          SeqView.of(new Arg<>(o -> Doc.plain(String.valueOf(term.lift())), true)),
          options.map.get(DistillerOptions.Key.ShowImplicitArgs)
        );
      }
      case FormTerm.Interval term -> Doc.styled(KEYWORD, "I");
      case PrimTerm.End end -> Doc.styled(KEYWORD, end.isRight() ? "1" : "0");
      case IntroTerm.New newTerm -> Doc.cblock(Doc.styled(KEYWORD, "new"), 2,
        Doc.vcat(newTerm.params().view()
          .map((k, v) -> Doc.sep(Doc.symbol("|"),
            linkRef(k, FIELD_CALL),
            Doc.symbol("=>"), term(Outer.Free, v)))
          .toImmutableSeq()));
      case CallTerm.Access term -> visitCalls(false, visitAccessHead(term), term.fieldArgs().view(), outer,
        options.map.get(DistillerOptions.Key.ShowImplicitArgs));
      case RefTerm.MetaPat metaPat -> {
        var ref = metaPat.ref();
        if (ref.solution().value == null) yield varDoc(ref.fakeBind());
        yield pat(ref, outer);
      }
      case ErrorTerm term -> {
        var doc = term.description().toDoc(options);
        yield term.isReallyError() ? Doc.angled(doc) : doc;
      }
      case ElimTerm.App term -> {
        var args = MutableList.of(term.arg());
        var head = ElimTerm.unapp(term.of(), args);
        if (head instanceof RefTerm.Field fieldRef) yield visitArgsCalls(fieldRef.ref(), FIELD_CALL, args, outer);
        yield visitCalls(false, term(Outer.AppHead, head), args.view(), outer,
          options.map.get(DistillerOptions.Key.ShowImplicitArgs));
      }
      case CallTerm.Prim prim -> visitArgsCalls(prim.ref(), FN_CALL, prim.args(), outer);
      case RefTerm.Field term -> linkRef(term.ref(), FIELD_CALL);
      case ElimTerm.Proj term ->
        Doc.cat(term(Outer.ProjHead, term.of()), Doc.symbol("."), Doc.plain(String.valueOf(term.ix())));
      case FormTerm.Pi term -> {
        if (!options.map.get(DistillerOptions.Key.ShowImplicitPats) && !term.param().explicit()) {
          yield term(outer, term.body());
        }
        // Try to omit the Pi keyword
        if (term.body().findUsages(term.param().ref()) == 0) yield checkParen(outer, Doc.sep(
          Doc.bracedUnless(term.param().type().toDoc(options), term.param().explicit()),
          Doc.symbol("->"),
          term(Outer.Codomain, term.body())
        ), Outer.BinOp);
        var params = MutableList.of(term.param());
        var body = FormTerm.unpi(term.body(), params);
        var doc = Doc.sep(
          Doc.styled(KEYWORD, Doc.symbol("Pi")),
          visitTele(params, body, Term::findUsages),
          Doc.symbol("->"),
          term(Outer.Codomain, body)
        );
        // Add paren when it's not free or a codomain
        yield checkParen(outer, doc, Outer.BinOp);
      }
      case StructCall structCall -> throw new UnsupportedOperationException("TODO");//visitArgsCalls(structCall.ref(), STRUCT_CALL, structCall.args(), outer);
      case CallTerm.Data dataCall -> visitArgsCalls(dataCall.ref(), DATA_CALL, dataCall.args(), outer);
      case LitTerm.ShapedInt shaped -> options.map.get(DistillerOptions.Key.ShowLiterals)
        ? Doc.plain(String.valueOf(shaped.repr()))
        : shaped.with(
        (zero, suc) -> shaped.repr() == 0
          ? linkLit(0, zero.ref, CON_CALL)
          : linkLit(shaped.repr(), suc.ref, CON_CALL),
        () -> Doc.plain(String.valueOf(shaped.repr())));
      case PrimTerm.Str str -> Doc.plain("\"" + StringEscapeUtil.escapeStringCharacters(str.string()) + "\"");
    };
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

  private @NotNull Doc visitAccessHead(CallTerm.@NotNull Access term) {
    return Doc.cat(term(Outer.ProjHead, term.of()), Doc.symbol("."),
      linkRef(term.ref(), FIELD_CALL));
  }

  public @NotNull Doc pat(@NotNull Pat pat, Outer outer) {
    return switch (pat) {
      case Pat.Meta meta -> {
        var sol = meta.solution().value;
        yield sol != null ? pat(sol, outer) : Doc.bracedUnless(linkDef(meta.fakeBind()), meta.explicit());
      }
      case Pat.Bind bind -> Doc.bracedUnless(linkDef(bind.bind()), bind.explicit());
      case Pat.Ctor ctor -> {
        var ctorDoc = visitCalls(ctor.ref(), CON_CALL, ctor.params().view().map(Pat::toArg), outer,
          options.map.get(DistillerOptions.Key.ShowImplicitPats));
        yield ctorDoc(outer, ctor.explicit(), ctorDoc, null, ctor.params().isEmpty());
      }
      case Pat.Absurd absurd -> Doc.bracedUnless(Doc.styled(KEYWORD, "()"), absurd.explicit());
      case Pat.Tuple tuple -> Doc.licit(tuple.explicit(),
        Doc.commaList(tuple.pats().view().map(sub -> pat(sub, Outer.Free))));
      case Pat.End end -> Doc.bracedUnless(Doc.styled(KEYWORD, !end.isRight() ? "0" : "1"), end.explicit());
      case Pat.ShapedInt lit -> options.map.get(DistillerOptions.Key.ShowLiterals)
        ? Doc.plain(String.valueOf(lit.repr()))
        : Doc.bracedUnless(lit.with(
          (zero, suc) -> lit.repr() == 0
            ? linkLit(0, zero.ref, CON_CALL)
            : linkLit(lit.repr(), suc.ref, CON_CALL),
          () -> Doc.plain(String.valueOf(lit.repr()))),
        lit.explicit());
    };
  }

  public @NotNull Doc def(@NotNull GenericDef predef) {
    return switch (predef) {
      case FnDef def -> {
        var line1 = MutableList.of(Doc.styled(KEYWORD, "def"));
        def.modifiers.forEach(m -> line1.append(Doc.styled(KEYWORD, m.keyword)));
        line1.appendAll(new Doc[]{
          linkDef(def.ref(), FN_CALL),
          visitTele(def.telescope()),
          Doc.symbol(":"),
          term(Outer.Free, def.result())
        });
        yield def.body.fold(
          term -> Doc.sep(Doc.sepNonEmpty(line1), Doc.symbol("=>"), term(Outer.Free, term)),
          clauses -> Doc.vcat(Doc.sepNonEmpty(line1), Doc.nest(2, visitClauses(clauses))));
      }
      case FieldDef field -> Doc.cblock(Doc.sepNonEmpty(Doc.symbol("|"),
        coe(field.coerce),
        linkDef(field.ref(), FIELD_CALL),
        visitTele(field.selfTele),
        Doc.symbol(":"),
        term(Outer.Free, field.result)), 2, visitClauses(field.clauses));
      case PrimDef def -> primDoc(def.ref());
      case CtorDef ctor -> {
        var doc = Doc.sepNonEmpty(coe(ctor.coerce),
          linkDef(ctor.ref(), CON_CALL),
          visitTele(ctor.selfTele));
        Doc line1;
        if (ctor.pats.isNotEmpty()) {
          var pats = Doc.commaList(ctor.pats.view().map(pat -> pat(pat, Outer.Free)));
          line1 = Doc.sep(Doc.symbol("|"), pats, Doc.symbol("=>"), doc);
        } else line1 = Doc.sep(Doc.symbol("|"), doc);
        yield Doc.cblock(line1, 2, visitClauses(ctor.clauses));
      }
      case StructDef def -> Doc.vcat(Doc.sepNonEmpty(Doc.styled(KEYWORD, "struct"),
        linkDef(def.ref(), STRUCT_CALL),
        Doc.symbol(":"),
        term(Outer.Free, def.result())
      ), Doc.nest(2, Doc.vcat(def.fields.view().map(this::def))));
      case DataDef def -> {
        var line1 = MutableList.of(Doc.styled(KEYWORD, "data"),
          linkDef(def.ref(), DATA_CALL),
          visitTele(def.telescope()),
          Doc.symbol(":"),
          term(Outer.Free, def.result()));
        yield Doc.vcat(Doc.sepNonEmpty(line1),
          Doc.nest(2, Doc.vcat(def.body.view().map(this::def))));
      }
    };
  }

  private @NotNull Doc visitClauses(@NotNull ImmutableSeq<Matching> clauses) {
    return Doc.vcat(clauses.view().map(matching ->
      Doc.sep(Doc.symbol("|"), matching.toDoc(options))));
  }
}
