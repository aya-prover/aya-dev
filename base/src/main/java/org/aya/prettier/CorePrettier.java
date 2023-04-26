// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.prettier;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.repr.CodeShape;
import org.aya.core.term.*;
import org.aya.core.visitor.TermFolder;
import org.aya.generic.AyaDocile;
import org.aya.util.error.InternalException;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * It's the pretty printer.
 * Credit after <a href="https://github.com/jonsterling/dreamtt/blob/main/frontend/Distiller.ml">Jon Sterling</a>
 *
 * @author ice1000, kiva
 * @see ConcretePrettier
 */
public class CorePrettier extends BasePrettier<Term> {
  public CorePrettier(@NotNull PrettierOptions options) {
    super(options);
  }

  private @Nullable Doc binCube(Restr.Side<Term> a, Restr.Side<Term> b, LocalVar var, @NotNull Outer outer) {
    if (!(a.cof().ands().sizeEquals(1) && b.cof().ands().sizeEquals(1)))
      return null;
    var aa = a.cof().ands().get(0);
    var bb = b.cof().ands().get(0);
    if (aa.inst() instanceof RefTerm(var ref) && ref == var && aa.isOne() == !bb.isOne()
      && bb.inst() instanceof RefTerm(var ref2) && ref2 == var
    ) {
      var aaa = term(Outer.BinOp, a.u());
      var bbb = term(Outer.BinOp, b.u());
      var eq = Doc.symbol("=");
      var doc = aa.isOne() ? Doc.sep(bbb, eq, aaa) : Doc.sep(aaa, eq, bbb);
      return checkParen(outer, doc, Outer.BinOp);
    }
    return null;
  }

  @Override public @NotNull Doc term(@NotNull Outer outer, @NotNull Term preterm) {
    return switch (preterm) {
      case RefTerm(var var) -> varDoc(var);
      case MetaTerm term -> {
        var name = term.ref();
        var inner = varDoc(name);
        var showImplicits = options.map.get(AyaPrettierOptions.Key.ShowImplicitArgs);
        if (options.map.get(AyaPrettierOptions.Key.InlineMetas))
          yield visitCalls(null, inner, term.args().view(), outer, showImplicits);
        yield Doc.wrap("{?", "?}",
          visitCalls(null, inner, term.args().view(), Outer.Free, showImplicits));
      }
      case MetaLitTerm lit ->
        lit.repr() instanceof AyaDocile docile ? docile.toDoc(options) : Doc.plain(lit.repr().toString());
      case TupTerm(var items) -> Doc.parened(argsDoc(options, items));
      case ConCall conCall -> visitArgsCalls(conCall.ref(), CON, conCall.conArgs(), outer);
      case FnCall fnCall -> visitArgsCalls(fnCall.ref(), FN, fnCall.args(), outer);
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
              : visitCalls(defVar.assoc(), varDoc(defVar), args, params.isEmpty() ? outer : Outer.Free,
                options.map.get(AyaPrettierOptions.Key.ShowImplicitArgs));
          }
        } else bodyDoc = term(Outer.Free, body);

        if (!options.map.get(AyaPrettierOptions.Key.ShowImplicitPats))
          params.retainIf(LamTerm.Param::explicit);
        if (params.isEmpty()) yield bodyDoc;

        var list = MutableList.of(Doc.styled(KEYWORD, Doc.symbol("\\")));
        params.forEach(param -> list.append(Doc.bracedUnless(linkDef(param.ref()), param.explicit())));
        list.append(Doc.symbol("=>"));
        list.append(bodyDoc);
        var doc = Doc.sep(list);
        yield checkParen(outer, doc, Outer.BinOp);
      }
      case SortTerm(var kind, var lift) -> {
        var fn = Doc.styled(KEYWORD, kind.name());
        if (!kind.hasLevel()) yield fn;
        yield visitCalls(null, fn, (nest, t) -> t.toDoc(options), outer,
          SeqView.of(new Arg<>(o -> Doc.plain(String.valueOf(lift)), true)),
          options.map.get(AyaPrettierOptions.Key.ShowImplicitArgs)
        );
      }
      case IntervalTerm $ -> Doc.styled(PRIM, "I");
      case NewTerm(var inner) -> visitCalls(null, Doc.styled(KEYWORD, "new"), (nest, t) -> t.toDoc(options), outer,
        SeqView.of(new Arg<>(o -> term(Outer.AppSpine, inner), true)),
        options.map.get(AyaPrettierOptions.Key.ShowImplicitArgs)
      );
      case FieldTerm term -> visitCalls(null, visitAccessHead(term), term.args().view(), outer,
        options.map.get(AyaPrettierOptions.Key.ShowImplicitArgs));
      case MetaPatTerm(var ref) -> {
        if (ref.solution().get() == null) yield varDoc(ref.fakeBind());
        yield Doc.wrap("<", ">", pat(ref, true, outer));
      }
      case ErrorTerm(var desc, var really) -> {
        var doc = desc.toDoc(options);
        yield really ? Doc.angled(doc) : doc;
      }
      case AppTerm(var of, var arg) -> {
        var args = MutableList.of(arg);
        var head = AppTerm.unapp(of, args);
        if (head instanceof RefTerm.Field fieldRef) yield visitArgsCalls(fieldRef.ref(), MEMBER, args, outer);
        var implicits = options.map.get(AyaPrettierOptions.Key.ShowImplicitArgs);
        // Infix def-calls
        if (head instanceof Callable call && call.ref() instanceof DefVar<?, ?> var) {
          yield visitCalls(var.assoc(), defVar(var),
            call.args().view().appendedAll(args), outer, implicits);
        }
        yield visitCalls(null, term(Outer.AppHead, head), args.view(), outer, implicits);
      }
      case PrimCall prim -> visitArgsCalls(prim.ref(), PRIM, prim.args(), outer);
      case RefTerm.Field term -> linkRef(term.ref(), MEMBER);
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
        if (!options.map.get(AyaPrettierOptions.Key.ShowImplicitPats) && !params0.explicit()) {
          yield term(outer, body0);
        }
        // Try to omit the Pi keyword
        if (body0.findUsages(params0.ref()) == 0) yield checkParen(outer, Doc.sep(
          justType(params0, Outer.BinOp),
          Doc.symbol("->"),
          term(Outer.Codomain, body0)
        ), Outer.BinOp);
        var params = MutableList.of(params0);
        var body = PiTerm.unpi(body0, UnaryOperator.identity(), params);
        var doc = Doc.sep(
          Doc.styled(KEYWORD, Doc.symbol("Fn")),
          visitTele(params, body, Term::findUsages),
          Doc.symbol("->"),
          term(Outer.Codomain, body)
        );
        // Add paren when it's not free or a codomain
        yield checkParen(outer, doc, Outer.BinOp);
      }
      case ClassCall classCall -> visitArgsCalls(classCall.ref(), CLAZZ, classCall.orderedArgs(), outer);
      case DataCall dataCall -> visitArgsCalls(dataCall.ref(), DATA, dataCall.args(), outer);
      case IntegerTerm shaped -> shaped.repr() == 0
        ? linkLit(0, shaped.ctorRef(CodeShape.MomentId.ZERO), CON)
        : linkLit(shaped.repr(), shaped.ctorRef(CodeShape.MomentId.SUC), CON);
      case ListTerm shaped -> {
        var subterms = shaped.repr().map(x -> term(Outer.Free, x));
        var nil = shaped.ctorRef(CodeShape.MomentId.NIL);
        var cons = shaped.ctorRef(CodeShape.MomentId.CONS);
        yield Doc.sep(
          linkListLit(Doc.symbol("["), nil, CON),
          Doc.join(linkListLit(Doc.COMMA, cons, CON), subterms),
          linkListLit(Doc.symbol("]"), nil, CON)
        );
      }
      case StringTerm(var str) -> Doc.plain("\"" + StringUtil.escapeStringCharacters(str) + "\"");
      case PartialTyTerm(var ty, var restr) -> checkParen(outer, Doc.sep(Doc.styled(KEYWORD, "Partial"),
        term(Outer.AppSpine, ty), Doc.parened(restr(options, restr))), Outer.AppSpine);
      case PartialTerm el -> partial(options, el.partial(), true, Doc.symbol("{|"), Doc.symbol("|}"));
      case FormulaTerm(var mula) -> formula(outer, mula);
      case PathTerm cube -> {
        if (cube.params().sizeEquals(1)
          && cube.partial() instanceof Partial.Split<Term> split
          && split.clauses().sizeEquals(2)
        ) {
          var var = cube.params().get(0);
          var clause1 = split.clauses().get(0);
          var clause2 = split.clauses().get(1);
          var beauty = binCube(clause1, clause2, var, outer);
          if (beauty != null) yield beauty;
        }
        yield Doc.sepNonEmpty(
          Doc.symbol("[|"),
          Doc.commaList(cube.params().map(BasePrettier::linkDef)),
          Doc.symbol("|]"),
          cube.type().toDoc(options),
          partial(options, cube.partial(), false, Doc.symbol("{"), Doc.symbol("}"))
        );
      }
      case PLamTerm(var params, var body) -> checkParen(outer,
        Doc.sep(Doc.styled(KEYWORD, "\\"),
          Doc.sep(params.map(BasePrettier::varDoc)),
          Doc.symbol("=>"),
          body.toDoc(options)),
        Outer.BinOp);
      case PAppTerm app -> visitCalls(null, term(Outer.AppHead, app.of()),
        app.args().view(), outer, options.map.get(AyaPrettierOptions.Key.ShowImplicitArgs));
      case CoeTerm(var ty, var r, var s) -> visitCalls(null,
        Doc.styled(KEYWORD, "coe"),
        Seq.of(r, s, ty).view().map(t -> new Arg<>(t, true)),
        outer, true);
      case HCompTerm hComp -> throw new InternalException("TODO");
      case InTerm(var phi, var u) -> insideOut(outer, phi, u, "inS");
      case OutTerm(var phi, var par, var u) -> insideOut(outer, phi, u, "outS");
    };
  }

  private @NotNull Doc insideOut(@NotNull Outer outer, @NotNull Term phi, @NotNull Term u, String fnName) {
    return checkParen(outer, Doc.sep(Doc.styled(KEYWORD, fnName),
      term(Outer.AppSpine, phi), term(Outer.AppSpine, u)), Outer.AppSpine);
  }

  /** @return if we can eta-contract the last argument */
  private boolean checkUneta(SeqView<Arg<Term>> args, LamTerm.Param param) {
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
      ? access.args() : call.args();
  }

  private @NotNull Doc visitAccessHead(@NotNull FieldTerm term) {
    return Doc.cat(term(Outer.ProjHead, term.of()), Doc.symbol("."),
      linkRef(term.ref(), MEMBER));
  }

  public @NotNull Doc pat(@NotNull Arg<Pat> pat, @NotNull Outer outer) {
    return pat(pat.term(), pat.explicit(), outer);
  }

  public @NotNull Doc pat(@NotNull Pat pat, boolean licit, Outer outer) {
    return switch (pat) {
      case Pat.Meta meta -> {
        var sol = meta.solution().get();
        yield sol != null ? pat(sol, licit, outer) : Doc.bracedUnless(linkDef(meta.fakeBind()), licit);
      }
      case Pat.Bind bind -> Doc.bracedUnless(linkDef(bind.bind()), licit);
      case Pat.Ctor ctor -> {
        var ctorDoc = visitCalls(ctor.ref(), CON, Arg.mapSeq(ctor.params().view(), Pat::toTerm), outer,
          options.map.get(AyaPrettierOptions.Key.ShowImplicitPats));
        yield ctorDoc(outer, licit, ctorDoc, ctor.params().isEmpty());
      }
      case Pat.Absurd absurd -> Doc.bracedUnless(Doc.styled(KEYWORD, "()"), licit);
      case Pat.Tuple tuple -> Doc.licit(licit,
        Doc.commaList(tuple.pats().view().map(sub -> pat(sub.term(), sub.explicit(), Outer.Free))));
      case Pat.ShapedInt lit -> Doc.bracedUnless(lit.repr() == 0
          ? linkLit(0, lit.ctorRef(CodeShape.MomentId.ZERO), CON)
          : linkLit(lit.repr(), lit.ctorRef(CodeShape.MomentId.SUC), CON),
        licit);
    };
  }

  public @NotNull Doc def(@NotNull GenericDef predef) {
    return switch (predef) {
      case FnDef def -> {
        var line1 = MutableList.of(Doc.styled(KEYWORD, "def"));
        def.modifiers.forEach(m -> line1.append(Doc.styled(KEYWORD, m.keyword)));
        line1.appendAll(new Doc[]{
          linkDef(def.ref(), FN),
          visitTele(def.telescope()),
          Doc.symbol(":"),
          term(Outer.Free, def.result)
        });
        var line1sep = Doc.sepNonEmpty(line1);
        yield def.body.fold(
          term -> Doc.sep(line1sep, Doc.symbol("=>"), term(Outer.Free, term)),
          clauses -> Doc.vcat(line1sep, Doc.nest(2, visitClauses(clauses))));
      }
      case MemberDef field -> Doc.sepNonEmpty(Doc.symbol("|"),
        coe(field.coerce),
        linkDef(field.ref(), MEMBER),
        visitTele(field.telescope),
        Doc.symbol(":"),
        term(Outer.Free, field.result));
      case PrimDef def -> primDoc(def.ref());
      case CtorDef ctor -> {
        var doc = Doc.sepNonEmpty(coe(ctor.coerce),
          linkDef(ctor.ref(), CON),
          visitTele(ctor.selfTele));
        Doc line1;
        if (ctor.pats.isNotEmpty()) {
          var pats = Doc.commaList(ctor.pats.view().map(pat -> pat(pat, Outer.Free)));
          line1 = Doc.sep(Doc.symbol("|"), pats, Doc.symbol("=>"), doc);
        } else line1 = Doc.sep(Doc.symbol("|"), doc);
        yield Doc.cblock(line1, 2, partial(options, ctor.clauses, false, Doc.empty(), Doc.empty()));
      }
      case ClassDef def -> Doc.vcat(Doc.sepNonEmpty(Doc.styled(KEYWORD, "class"),
        linkDef(def.ref(), CLAZZ),
        Doc.nest(2, Doc.vcat(def.members.view().map(this::def)))));
      case DataDef def -> {
        var line1 = MutableList.of(Doc.styled(KEYWORD, "data"),
          linkDef(def.ref(), DATA),
          visitTele(def.telescope()),
          Doc.symbol(":"),
          term(Outer.Free, def.result));
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
