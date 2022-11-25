// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.distill;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.repr.CodeShape;
import org.aya.core.term.*;
import org.aya.core.visitor.TermFolder;
import org.aya.generic.AyaDocile;
import org.aya.generic.util.InternalException;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

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
      case RefTerm(var var) -> varDoc(var);
      case MetaTerm term -> {
        var name = term.ref();
        var inner = varDoc(name);
        var showImplicits = options.map.get(DistillerOptions.Key.ShowImplicitArgs);
        if (options.map.get(DistillerOptions.Key.InlineMetas))
          yield visitCalls(false, inner, term.args().view(), outer, showImplicits);
        yield Doc.wrap("{?", "?}",
          visitCalls(false, inner, term.args().view(), Outer.Free, showImplicits));
      }
      case MetaLitTerm lit -> lit.repr() instanceof AyaDocile docile ? docile.toDoc(options) : Doc.plain(lit.repr().toString());
      case TupTerm(var items) -> Doc.parened(Doc.commaList(items.view().map(t -> term(Outer.Free, t))));
      case ConCall conCall -> visitArgsCalls(conCall.ref(), CON_CALL, conCall.conArgs(), outer);
      case FnCall fnCall -> visitArgsCalls(fnCall.ref(), FN_CALL, fnCall.args(), outer);
      case SigmaTerm(var params) -> {
        var last = params.last();
        var doc = Doc.sep(
          Doc.styled(KEYWORD, Doc.symbol("Sig")),
          visitTele(params.dropLast(1), last.type(), Term::findUsages),
          Doc.symbol("**"),
          justType(last, Outer.Codomain)
        );
        // Same as Pi
        yield checkParen(outer, doc, Outer.BinOp);
      }
      case LamTerm(var param0, var body0) -> {
        var params = MutableList.of(param0);
        var body = LamTerm.unwrap(body0, params::append);
        Doc bodyDoc;
        // Syntactic eta-contraction
        if (body instanceof Callable call && call.ref() instanceof DefVar<?, ?> defVar) {
          var args = visibleArgsOf(call).view();
          while (params.isNotEmpty() && args.isNotEmpty()) {
            if (checkUneta(args, params.last())) {
              args = args.dropLast(1);
              params.removeLast();
            } else break;
          }
          if (call instanceof FieldTerm access) bodyDoc = visitAccessHead(access);
          else {
            var style = chooseStyle(defVar);
            bodyDoc = style != null
              ? visitArgsCalls(defVar, style, args, outer)
              : visitCalls(defVar.isInfix(), varDoc(defVar), args, params.isEmpty() ? outer : Outer.Free,
                options.map.get(DistillerOptions.Key.ShowImplicitArgs));
          }
        } else bodyDoc = term(Outer.Free, body);

        if (!options.map.get(DistillerOptions.Key.ShowImplicitPats))
          params.retainIf(Term.Param::explicit);
        if (params.isEmpty()) yield bodyDoc;

        var list = MutableList.of(Doc.styled(KEYWORD, Doc.symbol("\\")));
        params.forEach(param -> list.append(lambdaParam(param)));
        list.append(Doc.symbol("=>"));
        list.append(bodyDoc);
        var doc = Doc.sep(list);
        yield checkParen(outer, doc, Outer.BinOp);
      }
      case SortTerm(var kind, var lift) -> {
        var fn = Doc.styled(KEYWORD, kind.name());
        if (!kind.hasLevel()) yield fn;
        yield visitCalls(false, fn, (nest, t) -> t.toDoc(options), outer,
          SeqView.of(new Arg<>(o -> Doc.plain(String.valueOf(lift)), true)),
          options.map.get(DistillerOptions.Key.ShowImplicitArgs)
        );
      }
      case IntervalTerm term -> Doc.styled(KEYWORD, "I");
      case NewTerm newTerm -> Doc.cblock(Doc.styled(KEYWORD, "new"), 2,
        Doc.vcat(newTerm.params().view()
          .map((k, v) -> Doc.sep(Doc.symbol("|"),
            linkRef(k, FIELD_CALL),
            Doc.symbol("=>"), term(Outer.Free, v)))
          .toImmutableSeq()));
      case FieldTerm term -> visitCalls(false, visitAccessHead(term), term.fieldArgs().view(), outer,
        options.map.get(DistillerOptions.Key.ShowImplicitArgs));
      case MetaPatTerm(var ref) -> {
        if (ref.solution().get() == null) yield varDoc(ref.fakeBind());
        yield Doc.wrap("<", ">", pat(ref, outer));
      }
      case ErrorTerm(var desc, var really) -> {
        var doc = desc.toDoc(options);
        yield really ? Doc.angled(doc) : doc;
      }
      case AppTerm(var of, var arg) -> {
        var args = MutableList.of(arg);
        var head = AppTerm.unapp(of, args);
        if (head instanceof RefTerm.Field fieldRef) yield visitArgsCalls(fieldRef.ref(), FIELD_CALL, args, outer);
        var implicits = options.map.get(DistillerOptions.Key.ShowImplicitArgs);
        // Infix def-calls
        if (head instanceof Callable call && call.ref() instanceof DefVar<?, ?> var && var.isInfix()) {
          yield visitCalls(true, defVar(var),
            call.args().view().appendedAll(args), outer, implicits);
        }
        yield visitCalls(false, term(Outer.AppHead, head), args.view(), outer, implicits);
      }
      case PrimCall prim -> visitArgsCalls(prim.ref(), PRIM_CALL, prim.args(), outer);
      case RefTerm.Field term -> linkRef(term.ref(), FIELD_CALL);
      case ProjTerm(var of, var ix) ->
        Doc.cat(term(Outer.ProjHead, of), Doc.symbol("."), Doc.plain(String.valueOf(ix)));
      case MatchTerm match -> Doc.cblock(Doc.sep(Doc.styled(KEYWORD, "match"),
          Doc.commaList(match.discriminant().map(t -> term(Outer.Free, t)))), 2,
        Doc.vcat(match.clauses().view()
          .map(clause -> Doc.sep(Doc.symbol("|"),
            Doc.commaList(clause.patterns().map(p -> pat(p, Outer.Free))),
            Doc.symbol("=>"), term(Outer.Free, clause.body())))
          .toImmutableSeq()));
      case PiTerm(var params0, var body0) -> {
        if (!options.map.get(DistillerOptions.Key.ShowImplicitPats) && !params0.explicit()) {
          yield term(outer, body0);
        }
        // Try to omit the Pi keyword
        if (body0.findUsages(params0.ref()) == 0) yield checkParen(outer, Doc.sep(
          Doc.bracedUnless(params0.type().toDoc(options), params0.explicit()),
          Doc.symbol("->"),
          term(Outer.Codomain, body0)
        ), Outer.BinOp);
        var params = MutableList.of(params0);
        var body = PiTerm.unpi(body0, UnaryOperator.identity(), params);
        var doc = Doc.sep(
          Doc.styled(KEYWORD, Doc.symbol("Pi")),
          visitTele(params, body, Term::findUsages),
          Doc.symbol("->"),
          term(Outer.Codomain, body)
        );
        // Add paren when it's not free or a codomain
        yield checkParen(outer, doc, Outer.BinOp);
      }
      case StructCall structCall -> visitArgsCalls(structCall.ref(), STRUCT_CALL, structCall.args(), outer);
      case DataCall dataCall -> visitArgsCalls(dataCall.ref(), DATA_CALL, dataCall.args(), outer);
      case IntegerTerm shaped -> shaped.repr() == 0
        ? linkLit(0, shaped.ctorRef(CodeShape.MomentId.ZERO), CON_CALL)
        : linkLit(shaped.repr(), shaped.ctorRef(CodeShape.MomentId.SUC), CON_CALL);
      case ListTerm shaped -> {
        var subterms = shaped.repr().map(x -> term(Outer.Free, x));
        var nil = shaped.ctorRef(CodeShape.MomentId.NIL);
        var cons = shaped.ctorRef(CodeShape.MomentId.CONS);
        yield Doc.sep(
          linkListLit(Doc.symbol("["), nil, CON_CALL),
          Doc.join(linkListLit(Doc.COMMA, cons, CON_CALL), subterms),
          linkListLit(Doc.symbol("]"), nil, CON_CALL)
        );
      }
      case StringTerm(var str) -> Doc.plain("\"" + StringUtil.escapeStringCharacters(str) + "\"");
      case PartialTyTerm(var ty, var restr) -> checkParen(outer, Doc.sep(Doc.styled(KEYWORD, "Partial"),
        term(Outer.AppSpine, ty), Doc.parened(restr(options, restr))), Outer.AppSpine);
      case PartialTerm el -> partial(options, el.partial(), true);
      case FormulaTerm(var mula) -> formula(outer, mula);
      case PathTerm(var cube) -> cube(options, cube);
      case PLamTerm(var params, var body) -> checkParen(outer,
        Doc.sep(Doc.styled(KEYWORD, "\\"),
          Doc.sep(params.map(BaseDistiller::varDoc)),
          Doc.symbol("=>"),
          body.toDoc(options)),
        Outer.BinOp);
      case PAppTerm app -> visitCalls(false, term(Outer.AppHead, app.of()),
        app.args().view(), outer, options.map.get(DistillerOptions.Key.ShowImplicitArgs));
      case CoeTerm coe -> checkParen(outer, Doc.sep(Doc.styled(KEYWORD, "coe"),
        term(Outer.AppSpine, coe.type()), Doc.parened(restr(options, coe.restr()))), Outer.AppSpine);
      case HCompTerm hComp -> throw new InternalException("TODO");
      case SubTerm(var ty, var restr, var partial) -> checkParen(outer, Doc.sep(Doc.styled(KEYWORD, "Sub"),
        term(Outer.AppSpine, ty), Doc.parened(restr(options, restr)), Doc.parened(partial(options, partial, true))), Outer.AppSpine);
      case InSTerm(var phi, var u) -> checkParen(outer, Doc.sep(Doc.styled(KEYWORD, "inS"),
        term(Outer.AppSpine, phi), term(Outer.AppSpine, u)), Outer.AppSpine);
      case OutSTerm(var phi, var u) -> checkParen(outer, Doc.sep(Doc.styled(KEYWORD, "outS"),
        term(Outer.AppSpine, phi), term(Outer.AppSpine, u)), Outer.AppSpine);
    };
  }

  /** @return if we can eta-contract the last argument */
  private boolean checkUneta(SeqView<Arg<Term>> args, Term.Param param) {
    var arg = args.last();
    if (arg.explicit() != param.explicit()) return false;
    if (!(arg.term() instanceof RefTerm(var argVar))) return false;
    if (argVar != param.ref()) return false;
    var counter = new TermFolder.Usages(param.ref());
    return args.dropLast(1).allMatch(a -> counter.apply(a.term()) == 0);
  }

  private ImmutableSeq<Arg<Term>> visibleArgsOf(Callable call) {
    return call instanceof ConCall con
      ? con.conArgs() : call instanceof FieldTerm access
      ? access.fieldArgs() : call.args();
  }

  private @NotNull Doc visitAccessHead(@NotNull FieldTerm term) {
    return Doc.cat(term(Outer.ProjHead, term.of()), Doc.symbol("."),
      linkRef(term.ref(), FIELD_CALL));
  }

  public @NotNull Doc pat(@NotNull Pat pat, Outer outer) {
    return switch (pat) {
      case Pat.Meta meta -> {
        var sol = meta.solution().get();
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
      case Pat.End end -> Doc.bracedUnless(Doc.styled(KEYWORD, end.isOne() ? "1" : "0"), end.explicit());
      case Pat.ShapedInt lit -> Doc.bracedUnless(lit.repr() == 0
          ? linkLit(0, lit.ctorRef(CodeShape.MomentId.ZERO), CON_CALL)
          : linkLit(lit.repr(), lit.ctorRef(CodeShape.MomentId.SUC), CON_CALL),
        lit.explicit());
    };
  }

  public @NotNull Doc def(@NotNull GenericDef predef) {
    return switch (predef) {
      case ClassDef classDef -> throw new UnsupportedOperationException("not implemented yet");
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
      case FieldDef field -> Doc.sepNonEmpty(Doc.symbol("|"),
        coe(field.coerce),
        linkDef(field.ref(), FIELD_CALL),
        visitTele(field.selfTele),
        Doc.symbol(":"),
        term(Outer.Free, field.result));
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
        yield Doc.cblock(line1, 2, partial(options, ctor.clauses, false));
      }
      case StructDef def -> Doc.vcat(Doc.sepNonEmpty(Doc.styled(KEYWORD, "struct"),
        linkDef(def.ref(), STRUCT_CALL),
        visitTele(def.telescope()),
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

  private @NotNull Doc visitClauses(@NotNull ImmutableSeq<Term.Matching> clauses) {
    return Doc.vcat(clauses.view().map(matching ->
      Doc.sep(Doc.symbol("|"), matching.toDoc(options))));
  }
}
