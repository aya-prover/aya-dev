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
import org.aya.api.ref.PreLevelVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.ExprConsumer;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.pretty.doc.Doc;
import org.aya.util.StringEscapeUtil;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author ice1000, kiva
 * @see CoreDistiller
 */
public class ConcreteDistiller extends BaseDistiller implements
  Stmt.Visitor<Unit, Doc>,
  Expr.Visitor<BaseDistiller.Outer, Doc> {
  public ConcreteDistiller(@NotNull DistillerOptions options) {
    super(options);
  }

  @Override public Doc visitRef(Expr.@NotNull RefExpr expr, Outer outer) {
    var ref = expr.resolvedVar();
    if (ref instanceof DefVar<?, ?> defVar) return visitDefVar(defVar);
    else if (ref instanceof PreLevelVar levelVar) return linkRef(levelVar, GENERALIZED);
    else return varDoc(ref);
  }

  @Override public Doc visitUnresolved(Expr.@NotNull UnresolvedExpr expr, Outer outer) {
    return Doc.plain(expr.name().join());
  }

  @Override public Doc visitLam(Expr.@NotNull LamExpr expr, Outer outer) {
    if (!options.map.get(DistillerOptions.Key.ShowImplicitPats) && !expr.param().explicit()) {
      return expr.body().accept(this, outer);
    }
    var prelude = DynamicSeq.of(Doc.styled(KEYWORD, Doc.symbol("\\")),
      lambdaParam(expr.param()));
    if (!(expr.body() instanceof Expr.HoleExpr)) {
      prelude.append(Doc.symbol("=>"));
      prelude.append(expr.body().accept(this, Outer.Free));
    }
    return Doc.sep(prelude);
  }

  @Override public Doc visitPi(Expr.@NotNull PiExpr expr, Outer outer) {
    var data = new boolean[]{false, false};
    expr.last().accept(new ExprConsumer<>() {
      @Override public Unit visitRef(@NotNull Expr.RefExpr ref, Unit unit) {
        if (ref.resolvedVar() == expr.param().ref()) data[0] = true;
        return unit;
      }

      @Override public Unit visitUnresolved(@NotNull Expr.UnresolvedExpr expr, Unit unit) {
        data[1] = true;
        return unit;
      }
    }, Unit.unit());
    Doc doc;
    if (!data[0] && !data[1]) {
      var type = expr.param().type();
      var tyDoc = type != null ? type.toDoc(options) : Doc.symbol("?");
      doc = Doc.sep(Doc.bracedUnless(tyDoc, expr.param().explicit()),
        Doc.symbol("->"),
        expr.last().accept(this, Outer.Codomain));
    } else doc = Doc.sep(
      Doc.styled(KEYWORD, Doc.symbol("Pi")),
      expr.param().toDoc(options),
      Doc.symbol("->"),
      expr.last().accept(this, Outer.Codomain));
    // When outsider is neither a codomain nor non-expression, we need to add parentheses.
    return checkParen(outer, doc, Outer.BinOp);
  }

  @Override public Doc visitSigma(Expr.@NotNull SigmaExpr expr, Outer outer) {
    var doc = Doc.sep(
      Doc.styled(KEYWORD, Doc.symbol("Sig")),
      visitTele(expr.params().dropLast(1)),
      Doc.symbol("**"),
      Objects.requireNonNull(expr.params().last().type()).accept(this, Outer.Codomain));
    // Same as Pi
    return checkParen(outer, doc, Outer.BinOp);
  }

  @Override public Doc visitRawUniv(Expr.@NotNull RawUnivExpr expr, Outer outer) {
    return Doc.styled(KEYWORD, "Type");
  }

  @Override public Doc visitRawUnivArgs(Expr.@NotNull RawUnivArgsExpr expr, Outer outer) {
    return Doc.sep(Doc.styled(KEYWORD, "universe"),
      Doc.commaList(expr.univArgs().view().map(e -> e.accept(this, Outer.Free))));
  }

  @Override public Doc visitUnivArgs(Expr.@NotNull UnivArgsExpr expr, Outer outer) {
    return Doc.sep(Doc.styled(KEYWORD, "universe"),
      Doc.commaList(expr.univArgs().view().map(e -> e.toDoc(options))));
  }

  @Override public Doc visitUniv(Expr.@NotNull UnivExpr expr, Outer outer) {
    var fn = Doc.styled(KEYWORD, "Type");
    if (!options.map.get(DistillerOptions.Key.ShowLevels)) return fn;
    return visitCalls(false, fn, (nc, l) -> l.toDoc(options), outer, SeqView.of(expr.level()).map(t -> new Arg<>(t, true))
    );
  }

  @Override public Doc visitApp(Expr.@NotNull AppExpr expr, Outer outer) {
    // TODO[ice]: binary?
    return visitCalls(false,
      expr.function().accept(this, Outer.AppHead),
      (nest, arg) -> arg.expr().accept(this, nest), outer,
      SeqView.of(new Arg<>(expr.argument(), expr.argument().explicit()))
    );
  }

  @Override public Doc visitLsuc(Expr.@NotNull LSucExpr expr, Outer outer) {
    return visitCalls(false,
      Doc.styled(KEYWORD, "lsuc"),
      (nest, arg) -> arg.accept(this, nest), outer, SeqView.of(new Arg<>(expr.expr(), true))
    );
  }

  @Override public Doc visitLmax(Expr.@NotNull LMaxExpr expr, Outer outer) {
    return visitCalls(false,
      Doc.styled(KEYWORD, "lmax"),
      (nest, arg) -> arg.accept(this, nest), outer, expr.levels().view().map(term -> new Arg<>(term, true))
    );
  }

  @Override public Doc visitHole(Expr.@NotNull HoleExpr expr, Outer outer) {
    if (!expr.explicit()) return Doc.symbol("_");
    var filling = expr.filling();
    if (filling == null) return Doc.symbol("{??}");
    return Doc.sep(Doc.symbol("{?"), filling.accept(this, Outer.Free), Doc.symbol("?}"));
  }

  @Override public Doc visitTup(Expr.@NotNull TupExpr expr, Outer outer) {
    return Doc.parened(Doc.commaList(expr.items().view().map(e -> e.accept(this, Outer.Free))));
  }

  @Override public Doc visitProj(Expr.@NotNull ProjExpr expr, Outer outer) {
    return Doc.cat(expr.tup().accept(this, Outer.ProjHead), Doc.plain("."), Doc.plain(expr.ix().fold(
      Objects::toString, WithPos::data
    )));
  }

  @Override public Doc visitNew(Expr.@NotNull NewExpr expr, Outer outer) {
    return Doc.cblock(
      Doc.sep(Doc.styled(KEYWORD, "new"), expr.struct().accept(this, Outer.Free)),
      2, Doc.vcat(expr.fields().view().map(t ->
        Doc.sep(Doc.symbol("|"), Doc.plain(t.name()),
          Doc.emptyIf(t.bindings().isEmpty(), () ->
            Doc.sep(t.bindings().map(v -> varDoc(v.data())))),
          Doc.plain("=>"), t.body().accept(this, Outer.Free))
      )));
  }

  @Override public Doc visitLitInt(Expr.@NotNull LitIntExpr expr, Outer outer) {
    return Doc.plain(String.valueOf(expr.integer()));
  }

  @Override public Doc visitLitString(Expr.@NotNull LitStringExpr expr, Outer outer) {
    return Doc.plain("\"" + StringEscapeUtil.escapeStringCharacters(expr.string()) + "\"");
  }

  @Override public Doc visitError(Expr.@NotNull ErrorExpr error, Outer outer) {
    return Doc.angled(error.description().toDoc(options));
  }

  @Override
  public Doc visitBinOpSeq(Expr.@NotNull BinOpSeq binOpSeq, Outer outer) {
    var seq = binOpSeq.seq();
    if (seq.sizeEquals(1)) return seq.first().expr().accept(this, outer);
    return visitCalls(false,
      seq.first().expr().accept(this, Outer.AppSpine),
      (nest, arg) -> arg.accept(this, nest), outer, seq.view().drop(1).map(e -> new Arg<>(e.expr(), e.explicit()))
    );
  }

  public @NotNull Doc visitPattern(@NotNull Pattern pattern, Outer outer) {
    return switch (pattern) {
      case Pattern.Tuple tuple -> {
        var tup = Doc.licit(tuple.explicit(),
          Doc.commaList(tuple.patterns().view().map(p -> visitPattern(p, Outer.Free))));
        yield tuple.as() == null ? tup
          : Doc.sep(tup, Doc.styled(KEYWORD, "as"), linkDef(tuple.as()));
      }
      case Pattern.Absurd absurd -> Doc.bracedUnless(Doc.styled(KEYWORD, "impossible"), absurd.explicit());
      case Pattern.Bind bind -> Doc.bracedUnless(linkDef(bind.bind()), bind.explicit());
      case Pattern.CalmFace calmFace -> Doc.bracedUnless(Doc.plain(Constants.ANONYMOUS_PREFIX), calmFace.explicit());
      case Pattern.Number number -> Doc.bracedUnless(Doc.plain(String.valueOf(number.number())), number.explicit());
      case Pattern.Ctor ctor -> {
        var name = linkRef(ctor.resolved().data(), CON_CALL);
        var ctorDoc = ctor.params().isEmpty() ? name : Doc.sep(name, visitMaybeCtorPatterns(ctor.params(), Outer.AppSpine, Doc.ALT_WS));
        yield ctorDoc(outer, ctor.explicit(), ctorDoc, ctor.as(), ctor.params().isEmpty());
      }
      case Pattern.BinOpSeq seq -> Doc.plain("TODO");
    };
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Pattern> patterns, Outer outer, @NotNull Doc delim) {
    patterns = options.map.get(DistillerOptions.Key.ShowImplicitPats) ? patterns : patterns.view().filter(Pattern::explicit);
    return Doc.join(delim, patterns.view().map(p -> visitPattern(p, outer)));
  }

  public Doc matchy(Pattern.@NotNull Clause match) {
    var doc = visitMaybeCtorPatterns(match.patterns, Outer.Free, Doc.plain(", "));
    return match.expr.map(e -> Doc.sep(doc, Doc.plain("=>"), e.accept(this, Outer.Free))).getOrDefault(doc);
  }

  private Doc visitAccess(Stmt.@NotNull Accessibility accessibility, Stmt.Accessibility def) {
    if (accessibility == def) return Doc.empty();
    else return Doc.styled(KEYWORD, accessibility.keyword);
  }

  @Override public Doc visitImport(Command.@NotNull Import cmd, Unit unit) {
    var prelude = DynamicSeq.of(Doc.styled(KEYWORD, "import"), Doc.symbol(cmd.path().join()));
    if (cmd.asName() != null) {
      prelude.append(Doc.styled(KEYWORD, "as"));
      prelude.append(Doc.plain(cmd.asName()));
    }
    return Doc.sep(prelude);
  }

  @Override public Doc visitOpen(Command.@NotNull Open cmd, Unit unit) {
    return Doc.sepNonEmpty(
      visitAccess(cmd.accessibility(), Stmt.Accessibility.Private),
      Doc.styled(KEYWORD, "open"),
      Doc.plain(cmd.path().join()),
      Doc.styled(KEYWORD, switch (cmd.useHide().strategy()) {
        case Using -> "using";
        case Hiding -> "hiding";
      }),
      Doc.parened(Doc.commaList(cmd.useHide().list().view().map(Doc::plain)))
    );
  }

  @Override public Doc visitModule(Command.@NotNull Module mod, Unit unit) {
    return Doc.vcat(
      Doc.sep(visitAccess(mod.accessibility(), Stmt.Accessibility.Public),
        Doc.styled(KEYWORD, "module"),
        Doc.plain(mod.name()),
        Doc.plain("{")),
      Doc.nest(2, Doc.vcat(mod.contents().view().map(stmt -> stmt.accept(this, Unit.unit())))),
      Doc.plain("}")
    );
  }

  @Override public Doc visitRemark(@NotNull Remark remark, Unit unit) {
    var literate = remark.literate;
    return literate != null ? literate.toDoc() : Doc.plain(remark.raw);
  }

  @Override public Doc visitData(Decl.@NotNull DataDecl decl, Unit unit) {
    var prelude = DynamicSeq.of(
      visitAccess(decl.accessibility(), Stmt.Accessibility.Public),
      Doc.styled(KEYWORD, "data"),
      linkDef(decl.ref, DATA_CALL),
      visitTele(decl.telescope));
    appendResult(prelude, decl.result);
    return Doc.cat(Doc.sepNonEmpty(prelude),
      Doc.emptyIf(decl.body.isEmpty(), () -> Doc.cat(Doc.line(), Doc.nest(2, Doc.vcat(
        decl.body.view().map(ctor -> visitCtor(ctor, Unit.unit())))))),
      visitBindBlock(decl.bindBlock)
    );
  }

  @Override public Doc visitCtor(Decl.@NotNull DataCtor ctor, Unit unit) {
    var prelude = DynamicSeq.of(
      coe(ctor.coerce),
      linkDef(ctor.ref, CON_CALL),
      visitTele(ctor.telescope),
      visitClauses(ctor.clauses, true)
    );
    var doc = Doc.sepNonEmpty(prelude);
    if (ctor.patterns.isNotEmpty()) {
      var pats = Doc.commaList(ctor.patterns.view().map(pattern -> visitPattern(pattern, Outer.Free)));
      return Doc.sep(Doc.symbol("|"), pats, Doc.plain("=>"), doc);
    } else return Doc.sep(Doc.symbol("|"), doc);
  }

  private Doc visitClauses(@NotNull ImmutableSeq<Pattern.Clause> clauses, boolean wrapInBraces) {
    if (clauses.isEmpty()) return Doc.empty();
    var clausesDoc = Doc.vcat(
      clauses.view()
        .map(this::matchy)
        .map(doc -> Doc.sep(Doc.symbol("|"), doc)));
    return Doc.bracedUnless(clausesDoc, !wrapInBraces);
  }

  @Override public Doc visitStruct(@NotNull Decl.StructDecl decl, Unit unit) {
    var prelude = DynamicSeq.of(visitAccess(decl.accessibility(), Stmt.Accessibility.Public),
      Doc.styled(KEYWORD, "struct"),
      linkDef(decl.ref, STRUCT_CALL),
      visitTele(decl.telescope));
    appendResult(prelude, decl.result);
    return Doc.cat(Doc.sepNonEmpty(prelude),
      Doc.emptyIf(decl.fields.isEmpty(), () -> Doc.cat(Doc.line(), Doc.nest(2, Doc.vcat(
        decl.fields.view().map(field -> visitField(field, Unit.unit())))))),
      visitBindBlock(decl.bindBlock)
    );
  }

  private void appendResult(DynamicSeq<Doc> prelude, Expr result) {
    if (result instanceof Expr.HoleExpr) return;
    prelude.append(Doc.symbol(":"));
    prelude.append(result.accept(this, Outer.Free));
  }

  @Override public Doc visitField(Decl.@NotNull StructField field, Unit unit) {
    var doc = DynamicSeq.of(Doc.symbol("|"),
      coe(field.coerce),
      linkDef(field.ref, FIELD_CALL),
      visitTele(field.telescope));
    appendResult(doc, field.result);
    if (field.body.isDefined()) {
      doc.append(Doc.symbol("=>"));
      doc.append(field.body.get().accept(this, Outer.Free));
    }
    doc.append(visitClauses(field.clauses, true));
    return Doc.sepNonEmpty(doc);
  }

  @Override public Doc visitFn(Decl.@NotNull FnDecl decl, Unit unit) {
    var prelude = DynamicSeq.of(visitAccess(decl.accessibility(), Stmt.Accessibility.Public), Doc.styled(KEYWORD, "def"));
    prelude.appendAll(Seq.from(decl.modifiers).view().map(this::visitModifier));
    prelude.append(linkDef(decl.ref, FN_CALL));
    prelude.append(visitTele(decl.telescope));
    appendResult(prelude, decl.result);
    return Doc.cat(Doc.sepNonEmpty(prelude),
      decl.body.fold(expr -> Doc.cat(Doc.ONE_WS, Doc.symbol("=>"), Doc.ONE_WS, expr.accept(this, Outer.Free)),
        clauses -> Doc.cat(Doc.line(), Doc.nest(2, visitClauses(clauses, false)))),
      visitBindBlock(decl.bindBlock)
    );
  }

  public Doc visitBindBlock(@NotNull BindBlock bindBlock) {
    if (bindBlock == BindBlock.EMPTY) return Doc.empty();
    var loosers = bindBlock.resolvedLoosers().value;
    var tighters = bindBlock.resolvedTighters().value;
    if (loosers.isEmpty() && tighters.isEmpty()) return Doc.empty();

    if (loosers.isEmpty()) return Doc.cat(Doc.line(), Doc.hang(2, Doc.sep(
      Doc.styled(KEYWORD, "bind"), Doc.styled(KEYWORD, "tighter"),
      Doc.commaList(tighters.view().map(BaseDistiller::visitDefVar)))));
    else if (tighters.isEmpty()) return Doc.cat(Doc.line(), Doc.hang(2, Doc.sep(
      Doc.styled(KEYWORD, "bind"), Doc.styled(KEYWORD, "looser"),
      Doc.commaList(loosers.view().map(BaseDistiller::visitDefVar)))));
    return Doc.cat(Doc.line(), Doc.hang(2, Doc.cat(Doc.styled(KEYWORD, "bind"), Doc.braced(Doc.sep(
      Doc.styled(KEYWORD, "tighter"), Doc.commaList(tighters.view().map(BaseDistiller::visitDefVar)),
      Doc.styled(KEYWORD, "looser"), Doc.commaList(loosers.view().map(BaseDistiller::visitDefVar))
    )))));
  }

  @Override public Doc visitPrim(@NotNull Decl.PrimDecl decl, Unit unit) {
    return primDoc(decl.ref);
  }

  private Doc visitModifier(@NotNull Modifier modifier) {
    return Doc.styled(KEYWORD, switch (modifier) {
      case Inline -> "inline";
      case Erase -> "erase";
    });
  }

  @Override public Doc visitLevels(Generalize.@NotNull Levels levels, Unit unit) {
    var vars = levels.levels().map(t -> linkDef(t.data(), GENERALIZED));
    return Doc.sep(Doc.styled(KEYWORD, "universe"), Doc.sep(vars));
  }

  @Override public Doc visitExample(Sample.@NotNull Working example, Unit unit) {
    return Doc.sep(Doc.styled(KEYWORD, "example"),
      example.delegate().accept(this, unit));
  }

  @Override public Doc visitCounterexample(Sample.@NotNull Counter example, Unit unit) {
    return Doc.sep(Doc.styled(KEYWORD, "counterexample"),
      example.delegate().accept(this, unit));
  }
}
