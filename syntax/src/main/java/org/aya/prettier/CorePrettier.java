// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.prettier;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.generic.AyaDocile;
import org.aya.generic.Renamer;
import org.aya.generic.term.DTKind;
import org.aya.generic.term.ParamLike;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.compile.JitMatchy;
import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.core.RichParam;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.*;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListTerm;
import org.aya.syntax.core.term.repr.MetaLitTerm;
import org.aya.syntax.core.term.repr.StringTerm;
import org.aya.syntax.core.term.xtt.*;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.GenerateKind.Basic;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.aya.prettier.Tokens.*;

/**
 * It's the pretty printer.
 * Credit after <a href="https://github.com/jonsterling/dreamtt/blob/main/frontend/Distiller.ml">Jon Sterling</a>
 *
 * @author ice1000, kiva
 * @see ConcretePrettier
 */
public class CorePrettier extends BasePrettier<Term> {
  private static final @NotNull LocalVar SELF = new LocalVar("self", SourcePos.NONE, Basic.Pretty);
  private final Renamer nameGen = new Renamer();

  public CorePrettier(@NotNull PrettierOptions options) { super(options); }
  @Override public @NotNull Doc term(@NotNull Outer outer, @NotNull Term preterm) {
    return switch (preterm) {
      case FreeTerm(var var) -> varDoc(var);
      case LocalTerm(var idx) -> Doc.plain("^" + idx);
      case MetaCall term -> {
        var name = term.ref();
        var inner = Doc.cat(Doc.plain("?"), varDoc(name));
        Function<Outer, Doc> factory = o -> visitCoreApp(null, inner, term.args().view(), o, optionImplicit());
        if (options.map.get(AyaPrettierOptions.Key.InlineMetas)) yield factory.apply(outer);
        yield Doc.wrap(HOLE_LEFT, HOLE_RIGHT, factory.apply(Outer.Free));
      }
      case MetaLitTerm lit -> switch (lit.repr()) {
        case AyaDocile docile -> docile.toDoc(options);
        case ImmutableSeq<?> seq -> Doc.wrap("[", "]",
          Doc.commaList(seq.view().map(p -> ((AyaDocile) p).toDoc(options))));
        case Object unknown -> Doc.plain(unknown.toString());
      };
      case TupTerm(var lhs, var rhs) -> Doc.commaList(ImmutableSeq.of(lhs, rhs).map(t -> term(Outer.Free, t)));
      case IntegerTerm shaped -> shaped.repr() == 0
        ? linkLit(0, shaped.zero(), CON)
        : linkLit(shaped.repr(), shaped.suc(), CON);
      case ListTerm shaped -> {
        var subterms = shaped.repr().map(x -> term(Outer.Free, x));
        var nil = shaped.nil();
        var cons = shaped.cons();
        yield Doc.sep(
          linkListLit(Doc.symbol("["), nil, CON),
          Doc.join(linkListLit(Doc.COMMA, cons, CON), subterms),
          linkListLit(Doc.symbol("]"), nil, CON)
        );
      }
      case ConCallLike conCall -> visitCoreCalls(conCall.ref(), conCall.conArgs(), outer, optionImplicit());
      case FnCall fnCall -> visitCoreCalls(fnCall.ref(), fnCall.args(), outer, optionImplicit());
      case LamTerm lam -> {
        var pair = LamTerm.unlam(lam, nameGen);
        var params = pair.params();
        var paramRef = params.view().<Term>map(FreeTerm::new);
        var body = pair.body().instTele(paramRef);
        Doc bodyDoc;
        // Syntactic eta-contraction
        if (body instanceof Callable.Tele call) {
          var args = visibleArgsOf(call).view();
          while (params.isNotEmpty() && args.isNotEmpty()) {
            if (checkUneta(args, params.getLast())) {
              args = args.dropLast(1);
              params = params.dropLast(1);
            } else break;
          }
          if (call instanceof MemberCall access) bodyDoc = visitAccessHead(access);
          else {
            bodyDoc = visitCoreCalls(call.ref(), args,
              params.isEmpty() ? outer : Outer.Free,
              optionImplicit());
          }
        } else bodyDoc = term(Outer.Free, body);

        if (params.isEmpty()) yield bodyDoc;

        var list = MutableList.of(LAMBDA);
        params.forEach(param -> {
          nameGen.unbindName(param);
          list.append(Doc.bracedUnless(linkDef(param), true));
        });
        list.append(FN_DEFINED_AS);
        list.append(bodyDoc);
        var doc = Doc.sep(list);
        yield checkParen(outer, doc, Outer.BinOp);
      }
      case SortTerm(var kind, var lift) -> {
        var fn = Doc.styled(KEYWORD, kind.name());
        if (!kind.hasLevel()) yield fn;
        yield visitCalls(null, fn, (_, t) -> t.toDoc(options), outer,
          SeqView.of(new Arg<>(_ -> Doc.plain(String.valueOf(lift)), true)),
          optionImplicit()
        );
      }
      case DimTyTerm _ -> KW_INTERVAL;
      case MemberCall term -> visitCoreApp(null, visitAccessHead(term),
        term.args().view(), outer,
        optionImplicit());
      case MetaPatTerm(var ref) -> {
        if (ref.solution().get() == null) yield varDoc(generateName(null));
        yield Doc.wrap(META_LEFT, META_RIGHT, pat(ref, true, outer));
      }
      case ErrorTerm(var desc, var isReallyError) -> {
        var doc = desc.toDoc(options);
        yield isReallyError ? Doc.angled(doc) : doc;
      }
      case AppTerm app -> {
        var pair = AppTerm.unapp(app);
        var args = pair.args();
        var head = pair.fun();
        // if (head instanceof RefTerm.Field fieldRef) yield visitArgsCalls(fieldRef.ref(), MEMBER, args, outer);
        var implicits = optionImplicit();
        // Infix def-calls
        if (head instanceof Callable.Tele call) {
          yield visitCoreCalls(call.ref(), call.args().view().appendedAll(args), outer, implicits);
        }
        yield visitCoreApp(null, term(Outer.AppHead, head), args.view(), outer, implicits);
      }
      case PrimCall prim -> visitCoreCalls(prim.ref(), prim.args(), outer, optionImplicit());
      case ProjTerm projTerm ->
        Doc.cat(term(Outer.ProjHead, projTerm.of()), PROJ, Doc.plain(String.valueOf(projTerm.index())));
      case DepTypeTerm depType -> {
        // Try to omit the Pi keyword
        if (depType.kind() == DTKind.Pi) {
          var var = nameGen.bindName(depType.param());
          var codomain = depType.body().apply(var);
          if (FindUsage.free(codomain, var) == 0) {
            var param = justType(Arg.ofExplicitly(depType.param()), Outer.BinOp);
            var cod = term(Outer.Codomain, codomain);
            nameGen.unbindName(var);
            yield checkParen(outer, Doc.sep(param, ARROW, cod), Outer.BinOp);
          }
        }
        var pair = DepTypeTerm.unpi(depType, UnaryOperator.identity(), nameGen);
        yield switch (depType.kind()) {
          case Pi -> visitDT(outer, pair, KW_PI, ARROW);
          case Sigma -> visitDT(outer, pair, KW_SIGMA, SIGMA_RESULT);
        };
      }
      case ClassCall classCall ->
        visitCoreCalls(classCall.ref(), classCall.args().map(x -> x.apply(SELF)), outer, true);
      case NewTerm newTerm -> Doc.sep(KW_NEW, term(Outer.Free, newTerm.inner()));
      case DataCall dataCall -> visitCoreCalls(dataCall.ref(), dataCall.args(), outer, optionImplicit());
      case StringTerm(var str) -> Doc.plain("\"" + StringUtil.escapeStringCharacters(str) + "\"");
      case PAppTerm app -> visitCalls(null, term(Outer.AppHead, app.fun()),
        SeqView.of(new Arg<>(app.arg(), true)), outer, optionImplicit());
      case CoeTerm(var ty, var r, var s) -> visitCalls(null,
        KW_COE,
        ImmutableSeq.of(r, s, new LamTerm(ty)).view().map(t -> new Arg<>(t, true)),
        outer, true);
      // case HCompTerm hComp -> throw new InternalException("TODO");
      case DimTerm dim -> Doc.styled(KEYWORD, switch (dim) {
        case I0 -> "0";
        case I1 -> "1";
      });
      // TODO: in case we want to show implicits, display the type
      case EqTerm(var _, var a, var b) -> {
        var doc = Doc.sep(term(Outer.BinOp, a), EQ, term(Outer.BinOp, b));
        yield checkParen(outer, doc, Outer.BinOp);
      }
      case RuleReducer.Fn fn -> term(outer, fn.toFnCall());
      case ClassCastTerm classCastTerm -> term(outer, classCastTerm.subterm());
      case MatchCall(Matchy clauses, var discriminant, var captures) -> {
        var deltaDoc = discriminant.map(x -> term(Outer.Free, x));
        var prefix = Doc.sep(KW_MATCH, Doc.commaList(deltaDoc));
        var clauseDoc = visitClauses(clauses.clauses().view().map(clause ->
            clause.update(clause.body().instTeleFrom(clause.bindCount(), captures.view()))),
          ImmutableSeq.fill(discriminant.size(), true).view());

        yield Doc.cblock(prefix, 2, clauseDoc);
      }
      case MatchCall(JitMatchy _, var discriminant, var captures) -> {
        var deltaDoc = discriminant.map(x -> term(Outer.Free, x));
        var prefix = Doc.sep(KW_MATCH, Doc.commaList(deltaDoc));
        yield Doc.sep(prefix, Doc.braced(Doc.spaced(Doc.styled(COMMENT, "compiled code"))));
      }
    };
  }

  private @NotNull Doc visitDT(@NotNull Outer outer, DepTypeTerm.Unpi pair, Doc kw, Doc operator) {
    var params = pair.names().zip(pair.params(), RichParam::ofExplicit);
    var body = pair.body().instTeleVar(params.view().map(ParamLike::ref));
    var teleDoc = visitTele(params, body, FindUsage::free);
    var cod = term(Outer.Codomain, body);
    var doc = Doc.sep(kw, teleDoc, operator, cod);
    pair.names().forEach(nameGen::unbindName);
    return checkParen(outer, doc, Outer.BinOp);
  }

  /** @return if we can eta-contract the last argument */
  private boolean checkUneta(@NotNull SeqView<Term> args, @NotNull LocalVar param) {
    var arg = args.getLast();
    if (!(arg instanceof FreeTerm(var var))) return false;
    if (var != param) return false;
    var counter = new FindUsage(new Usage.Ref.Free(param));
    return args.dropLast(1).allMatch(a -> counter.apply(0, a) == 0);
  }

  private ImmutableSeq<Term> visibleArgsOf(Callable call) {
    return call instanceof ConCallLike con ? con.conArgs() : call.args();
  }

  private @NotNull Doc visitAccessHead(@NotNull MemberCall term) {
    return Doc.cat(term(Outer.ProjHead, term.of()), Doc.symbol("."),
      refVar(term.ref()));
  }

  public @NotNull Doc pat(@NotNull Pat pat, boolean licit, Outer outer) {
    return switch (pat) {
      case Pat.Meta meta -> {
        var sol = meta.solution().get();
        yield sol != null ? pat(sol, licit, outer)
          : Doc.bracedUnless(linkDef(generateName(meta.type())), licit);
      }
      case Pat.Bind bind -> Doc.bracedUnless(linkDef(bind.bind()), licit);
      case Pat.Con con -> {
        var conDoc = visitCoreCalls(con.ref(), con.args().view().map(PatToTerm::visit), outer,
          optionImplicit());
        yield conDoc(outer, licit, conDoc, con.args().isEmpty());
      }
      case Pat.Misc misc -> switch (misc) {
        case Absurd -> Doc.bracedUnless(PAT_ABSURD, licit);
        case UntypedBind -> Doc.bracedUnless(linkDef(LocalVar.IGNORED), licit);
      };
      case Pat.Tuple(var l, var r) -> Doc.licit(licit,
        Doc.commaList(pat(l, true, Outer.Free), pat(r, true, Outer.Free)));
      case Pat.ShapedInt lit -> Doc.bracedUnless(lit.repr() == 0
          ? linkLit(0, lit.zero(), CON)
          : linkLit(lit.repr(), lit.suc(), CON),
        licit);
    };
  }

  public @NotNull Doc def(@NotNull TyckDef predef) {
    return switch (predef) {
      case PrimDef def -> primDoc(def.ref());
      case FnDef def -> {
        var line1 = MutableList.of(KW_DEF);
        def.modifiers().forEach(m -> line1.append(Doc.styled(KEYWORD, m.keyword)));
        var tele = enrich(def.telescope());
        var subst = tele.view().<Term>map(p -> new FreeTerm(p.ref()));
        line1.appendAll(new Doc[]{
          defVar(def.ref()),
          visitTele(tele),
          HAS_TYPE,
          term(Outer.Free, def.result().instTeleVar(tele.view().map(ParamLike::ref)))
        });
        var line1sep = Doc.sepNonEmpty(line1);
        yield def.body().fold(
          term -> Doc.sep(line1sep, FN_DEFINED_AS, term(Outer.Free, term.instTele(subst))),
          clauses -> Doc.vcat(line1sep,
            Doc.nest(2, visitClauses(clauses.view().map(WithPos::data), tele.view().map(ParamLike::explicit)))));
      }
      case MemberDef field -> Doc.sepNonEmpty(Doc.symbol("|"),
        defVar(field.ref()),
        visitTele(enrich(field.telescope())),
        Doc.symbol(":"),
        term(Outer.Free, field.result()));
      case ConDef con -> {
        var doc = Doc.sepNonEmpty(coe(con.coerce),
          defVar(con.ref()),
          visitTele(enrich(con.selfTele)));
        if (con.pats.isNotEmpty()) {
          var pats = Doc.commaList(con.pats.view().map(pat -> pat(pat, true, Outer.Free)));
          yield Doc.sep(Doc.symbol("|"), pats, Doc.symbol("=>"), doc);
        } else {
          yield Doc.sep(BAR, doc);
        }
      }
      case ClassDef def -> Doc.vcat(Doc.sepNonEmpty(KW_CLASS,
        defVar(def.ref()),
        Doc.nest(2, Doc.vcat(def.members().view().map(this::def)))));
      case DataDef def -> {
        var richDataTele = enrich(def.telescope());
        var dataArgs = richDataTele.view().<Term>map(t -> new FreeTerm(t.ref()));

        var line1 = MutableList.of(KW_DATA,
          defVar(def.ref()),
          visitTele(richDataTele),
          HAS_TYPE,
          term(Outer.Free, def.result()));
        var cons = def.body.view().map(con ->
          // we need to instantiate the tele of con, but we can't modify the CtorDef
          visitCon(con.ref, enrich(con.selfTele.mapIndexed((i, p) -> {
            // i: nth param
            // p: the param
            // instantiate reference to data tele
            return p.descent(t -> t.instTeleFrom(i, dataArgs));
          })), con.coerce));

        yield Doc.vcat(Doc.sepNonEmpty(line1),
          Doc.nest(2, Doc.vcat(cons)));
      }
    };
  }

  private @NotNull Doc visitCon(
    @NotNull DefVar<ConDef, DataCon> ref,
    @NotNull ImmutableSeq<ParamLike<Term>> richSelfTele,
    boolean coerce
  ) {
    var doc = Doc.sepNonEmpty(coe(coerce), defVar(ref), visitTele(richSelfTele));
    var con = ref.core;
    if (con.pats.isNotEmpty()) {
      var pats = Doc.commaList(con.pats.view().map(pat -> pat(pat, true, Outer.Free)));
      return Doc.sep(BAR, pats, FN_DEFINED_AS, doc);
    } else {
      return Doc.sep(BAR, doc);
    }
  }

  public @NotNull Doc visitClauseLhs(@NotNull SeqView<Boolean> licits, @NotNull Term.Matching clause) {
    var enrichPats = clause.patterns().zip(licits,
      (pat, licit) -> pat(pat, licit, Outer.Free));
    return Doc.commaList(enrichPats);
  }

  private @NotNull Doc visitClause(@NotNull Term.Matching clause, @NotNull SeqView<Boolean> licits) {
    var patSubst = Pat.collectRichBindings(clause.patterns().view());
    var lhsWithoutBar = visitClauseLhs(licits, clause);
    var rhs = term(Outer.Free, clause.body().instTele(patSubst.view().map(RichParam::toTerm)));

    return Doc.sep(BAR, lhsWithoutBar, FN_DEFINED_AS, rhs);
  }

  private @NotNull Doc visitClauses(@NotNull SeqView<Term.Matching> clauses, @NotNull SeqView<Boolean> licits) {
    return Doc.vcat(clauses.map(matching -> visitClause(matching, licits)));
  }

  /// region Name Generating
  private @NotNull ImmutableSeq<ParamLike<Term>> enrich(@NotNull SeqLike<Param> tele) {
    var richTele = MutableList.<ParamLike<Term>>create();

    for (var param : tele) {
      var freeTy = param.type().instTeleVar(richTele.view().map(ParamLike::ref));
      richTele.append(new RichParam(new LocalVar(
        param.name(), SourcePos.NONE, Basic.Pretty), freeTy, param.explicit()));
    }

    return richTele.toImmutableSeq();
  }

  private @NotNull LocalVar generateName(@Nullable Term whty) {
    return nameGen.bindName(whty);
  }
  /// endregion Name Generating
}
