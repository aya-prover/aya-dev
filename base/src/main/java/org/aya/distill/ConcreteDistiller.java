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
import org.aya.api.ref.PreLevelVar;
import org.aya.api.util.Arg;
import org.aya.api.util.WithPos;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.ExprConsumer;
import org.aya.generic.Modifier;
import org.aya.pretty.doc.Doc;
import org.aya.util.Constants;
import org.aya.util.StringEscapeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static org.aya.distill.BaseDistiller.*;

/**
 * @author ice1000, kiva
 * @see CoreDistiller
 */
public record ConcreteDistiller(@NotNull DistillerOptions options) implements
  Stmt.Visitor<Unit, Doc>,
  Pattern.Visitor<Boolean, Doc>,
  Expr.Visitor<Boolean, Doc>,
  BaseDistiller {
  @Override public Doc visitRef(Expr.@NotNull RefExpr expr, Boolean nestedCall) {
    var ref = expr.resolvedVar();
    if (ref instanceof DefVar<?, ?> defVar) return visitDefVar(defVar, defVar.concrete);
    else if (ref instanceof PreLevelVar levelVar) return linkRef(levelVar, GENERALIZED);
    else return varDoc(ref);
  }

  @NotNull private Doc visitDefVar(DefVar<?, ?> ref, Object concrete) {
    return switch (concrete) {
      case Decl.FnDecl d -> linkRef(ref, FN_CALL);
      case Decl.DataDecl d -> linkRef(ref, DATA_CALL);
      case Decl.DataCtor d -> linkRef(ref, CON_CALL);
      case Decl.StructDecl d -> linkRef(ref, STRUCT_CALL);
      case Decl.StructField d -> linkRef(ref, FIELD_CALL);
      case Decl.PrimDecl d -> linkRef(ref, FN_CALL);
      case Sample sample -> visitDefVar(ref, sample.delegate());
      case null, default -> varDoc(ref);
    };
  }

  @Override public Doc visitUnresolved(Expr.@NotNull UnresolvedExpr expr, Boolean nestedCall) {
    return Doc.plain(expr.name().join());
  }

  @Override public Doc visitLam(Expr.@NotNull LamExpr expr, Boolean nestedCall) {
    if (!options.showImplicitPats() && !expr.param().explicit()) {
      return expr.body().accept(this, nestedCall);
    }
    var prelude = Buffer.of(Doc.styled(KEYWORD, Doc.symbol("\\")),
      lambdaParam(expr.param()));
    if (!(expr.body() instanceof Expr.HoleExpr)) {
      prelude.append(Doc.symbol("=>"));
      prelude.append(expr.body().accept(this, false));
    }
    return Doc.sep(prelude);
  }

  @Override public Doc visitPi(Expr.@NotNull PiExpr expr, Boolean nestedCall) {
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
    if (!data[0] && !data[1]) {
      var type = expr.param().type();
      var tyDoc = type != null ? type.toDoc(options) : Doc.symbol("?");
      return Doc.sep(expr.param().explicit() ? tyDoc : Doc.braced(tyDoc),
        Doc.symbol("->"),
        expr.last().accept(this, false));
    }
    return Doc.sep(
      Doc.styled(KEYWORD, Doc.symbol("Pi")),
      expr.param().toDoc(options),
      Doc.symbol("->"),
      expr.last().accept(this, false));
  }

  @Override public Doc visitSigma(Expr.@NotNull SigmaExpr expr, Boolean nestedCall) {
    return Doc.sep(
      Doc.styled(KEYWORD, Doc.symbol("Sig")),
      visitTele(expr.params().dropLast(1)),
      Doc.symbol("**"),
      Objects.requireNonNull(expr.params().last().type()).accept(this, false));
  }

  @Override public Doc visitRawUniv(Expr.@NotNull RawUnivExpr expr, Boolean nestedCall) {
    return Doc.styled(KEYWORD, "Type");
  }

  @Override public Doc visitRawUnivArgs(Expr.@NotNull RawUnivArgsExpr expr, Boolean aBoolean) {
    return Doc.sep(Doc.styled(KEYWORD, "universe"),
      Doc.commaList(expr.univArgs().view().map(e -> e.accept(this, false))));
  }

  @Override public Doc visitUnivArgs(Expr.@NotNull UnivArgsExpr expr, Boolean aBoolean) {
    return Doc.sep(Doc.styled(KEYWORD, "universe"),
      Doc.commaList(expr.univArgs().view().map(e -> e.toDoc(options))));
  }

  @Override public Doc visitUniv(Expr.@NotNull UnivExpr expr, Boolean nestedCall) {
    var fn = Doc.styled(KEYWORD, "Type");
    if (!options.showLevels()) return fn;
    return visitCalls(fn, Seq.of(expr.level()).view().map(t -> new Arg<>(t, true)),
      (nc, l) -> l.toDoc(options), nestedCall);
  }

  @Override public Doc visitApp(Expr.@NotNull AppExpr expr, Boolean nestedCall) {
    return visitCalls(
      expr.function().accept(this, false),
      expr.arguments(),
      (nest, arg) -> arg.expr().accept(this, nest),
      nestedCall);
  }

  @Override public Doc visitLsuc(Expr.@NotNull LSucExpr expr, Boolean nestedCall) {
    return visitCalls(
      Doc.styled(KEYWORD, "lsuc"),
      ImmutableSeq.of(new Arg<>(expr.expr(), true)),
      (nest, arg) -> arg.accept(this, nest),
      nestedCall);
  }

  @Override public Doc visitLmax(Expr.@NotNull LMaxExpr expr, Boolean nestedCall) {
    return visitCalls(
      Doc.styled(KEYWORD, "lmax"),
      expr.levels().map(term -> new Arg<>(term, true)),
      (nest, arg) -> arg.accept(this, nest),
      nestedCall);
  }

  @Override public Doc visitHole(Expr.@NotNull HoleExpr expr, Boolean nestedCall) {
    if (!expr.explicit()) return Doc.symbol("_");
    var filling = expr.filling();
    if (filling == null) return Doc.symbol("{??}");
    return Doc.sep(Doc.symbol("{?"), filling.accept(this, false), Doc.symbol("?}"));
  }

  @Override public Doc visitTup(Expr.@NotNull TupExpr expr, Boolean nestedCall) {
    return Doc.parened(Doc.commaList(expr.items().view().map(e -> e.accept(this, false))));
  }

  @Override public Doc visitProj(Expr.@NotNull ProjExpr expr, Boolean nestedCall) {
    return Doc.cat(expr.tup().accept(this, false), Doc.plain("."), Doc.plain(expr.ix().fold(
      Objects::toString, WithPos::data
    )));
  }

  @Override public Doc visitNew(Expr.@NotNull NewExpr expr, Boolean aBoolean) {
    return Doc.sep(
      Doc.styled(KEYWORD, "new"),
      expr.struct().accept(this, false),
      Doc.symbol("{"),
      Doc.sep(expr.fields().view().map(t ->
        Doc.sep(Doc.symbol("|"), Doc.plain(t.name()),
          Doc.emptyIf(t.bindings().isEmpty(), () ->
            Doc.sep(t.bindings().map(v -> varDoc(v.data())))),
          Doc.plain("=>"), t.body().accept(this, false))
      )),
      Doc.symbol("}")
    );
  }

  @Override public Doc visitLitInt(Expr.@NotNull LitIntExpr expr, Boolean nestedCall) {
    return Doc.plain(String.valueOf(expr.integer()));
  }

  @Override public Doc visitLitString(Expr.@NotNull LitStringExpr expr, Boolean nestedCall) {
    return Doc.plain("\"" + StringEscapeUtil.escapeStringCharacters(expr.string()) + "\"");
  }

  @Override public Doc visitError(Expr.@NotNull ErrorExpr error, Boolean nestedCall) {
    return Doc.angled(error.description().toDoc(options));
  }

  @Override
  public Doc visitBinOpSeq(Expr.@NotNull BinOpSeq binOpSeq, Boolean nestedCall) {
    return visitCalls(
      binOpSeq.seq().first().expr().accept(this, false),
      binOpSeq.seq().view().drop(1).map(e -> new Arg<>(e.expr(), e.explicit())),
      (nest, arg) -> arg.accept(this, nest),
      nestedCall
    );
  }

  @Override public Doc visitTuple(Pattern.@NotNull Tuple tuple, Boolean nestedCall) {
    var tup = Doc.licit(tuple.explicit(),
      Doc.commaList(tuple.patterns().view().map(p -> p.accept(this, false))));
    return tuple.as() == null ? tup
      : Doc.sep(tup, Doc.styled(KEYWORD, "as"), linkDef(tuple.as()));
  }

  @Override public Doc visitNumber(Pattern.@NotNull Number number, Boolean nestedCall) {
    var doc = Doc.plain(String.valueOf(number.number()));
    return number.explicit() ? doc : Doc.braced(doc);
  }

  @Override public Doc visitBind(Pattern.@NotNull Bind bind, Boolean nestedCall) {
    var doc = linkDef(bind.bind());
    return bind.explicit() ? doc : Doc.braced(doc);
  }

  @Override public Doc visitAbsurd(Pattern.@NotNull Absurd absurd, Boolean aBoolean) {
    var doc = Doc.styled(KEYWORD, "impossible");
    return absurd.explicit() ? doc : Doc.braced(doc);
  }

  @Override public Doc visitCalmFace(Pattern.@NotNull CalmFace calmFace, Boolean nestedCall) {
    var doc = Doc.plain(Constants.ANONYMOUS_PREFIX);
    return calmFace.explicit() ? doc : Doc.braced(doc);
  }

  @Override public Doc visitCtor(Pattern.@NotNull Ctor ctor, Boolean nestedCall) {
    var name = Doc.styled(CON_CALL, ctor.name().data());
    var ctorDoc = ctor.params().isEmpty() ? name : Doc.sep(name, visitMaybeCtorPatterns(ctor.params(), true, Doc.ALT_WS));
    return ctorDoc(nestedCall, ctor.explicit(), ctorDoc, ctor.as(), ctor.params().isEmpty());
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Pattern> patterns, boolean nestedCall, @NotNull Doc delim) {
    patterns = options.showImplicitPats() ? patterns : patterns.view().filter(Pattern::explicit);
    return Doc.join(delim, patterns.view().map(p -> p.accept(this, nestedCall)));
  }

  public Doc matchy(Pattern.@NotNull Clause match) {
    var doc = visitMaybeCtorPatterns(match.patterns, false, Doc.plain(", "));
    return match.expr.map(e -> Doc.sep(doc, Doc.plain("=>"), e.accept(this, false))).getOrDefault(doc);
  }

  private Doc visitAccess(Stmt.@NotNull Accessibility accessibility, Stmt.Accessibility def) {
    if (accessibility == def) return Doc.empty();
    else return Doc.styled(KEYWORD, accessibility.keyword);
  }

  @Override public Doc visitImport(Command.@NotNull Import cmd, Unit unit) {
    var prelude = Buffer.of(Doc.styled(KEYWORD, "import"), Doc.symbol(cmd.path().join()));
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

  @Override public Doc visitBind(Command.@NotNull Bind bind, Unit unit) {
    return Doc.sep(
      Doc.styled(KEYWORD, "bind"),
      Doc.plain(bind.op().fold(QualifiedID::join, op -> Objects.requireNonNull(op.asOperator()).name())),
      Doc.styled(KEYWORD, bind.pred().keyword),
      Doc.plain(bind.target().fold(QualifiedID::join, op -> Objects.requireNonNull(op.asOperator()).name()))
    );
  }

  @Override public Doc visitRemark(@NotNull Remark remark, Unit unit) {
    var literate = remark.literate;
    return literate != null ? literate.toDoc() : Doc.plain(remark.raw);
  }

  @Override public Doc visitData(Decl.@NotNull DataDecl decl, Unit unit) {
    var prelude = Buffer.of(
      visitAccess(decl.accessibility(), Stmt.Accessibility.Public),
      Doc.styled(KEYWORD, "data"),
      linkDef(decl.ref, DATA_CALL),
      visitTele(decl.telescope));
    appendResult(prelude, decl.result);
    return Doc.cat(Doc.sepNonEmpty(prelude),
      Doc.emptyIf(decl.body.isEmpty(), () -> Doc.cat(Doc.line(), Doc.nest(2, Doc.vcat(
        decl.body.view().map(ctor -> visitCtor(ctor, Unit.unit()))))))
    );
  }

  @Override public Doc visitCtor(Decl.@NotNull DataCtor ctor, Unit unit) {
    var prelude = Buffer.of(
      coe(ctor.coerce),
      linkDef(ctor.ref, CON_CALL),
      visitTele(ctor.telescope),
      visitClauses(ctor.clauses, true)
    );
    var doc = Doc.sepNonEmpty(prelude);
    if (ctor.patterns.isNotEmpty()) {
      var pats = Doc.commaList(ctor.patterns.view().map(pattern -> pattern.accept(this, false)));
      return Doc.sep(Doc.symbol("|"), pats, Doc.plain("=>"), doc);
    } else return Doc.sep(Doc.symbol("|"), doc);
  }

  private Doc visitClauses(@NotNull ImmutableSeq<Pattern.Clause> clauses, boolean wrapInBraces) {
    if (clauses.isEmpty()) return Doc.empty();
    var clausesDoc = Doc.vcat(
      clauses.view()
        .map(this::matchy)
        .map(doc -> Doc.sep(Doc.symbol("|"), doc)));
    return wrapInBraces ? Doc.braced(clausesDoc) : clausesDoc;
  }

  @Override public Doc visitStruct(@NotNull Decl.StructDecl decl, Unit unit) {
    var prelude = Buffer.of(visitAccess(decl.accessibility(), Stmt.Accessibility.Public),
      Doc.styled(KEYWORD, "struct"),
      linkDef(decl.ref, STRUCT_CALL),
      visitTele(decl.telescope));
    appendResult(prelude, decl.result);
    return Doc.cat(Doc.sepNonEmpty(prelude),
      Doc.emptyIf(decl.fields.isEmpty(), () -> Doc.cat(Doc.line(), Doc.nest(2, Doc.vcat(
        decl.fields.view().map(field -> visitField(field, Unit.unit()))))))
    );
  }

  private void appendResult(Buffer<Doc> prelude, Expr result) {
    if (result instanceof Expr.HoleExpr) return;
    prelude.append(Doc.symbol(":"));
    prelude.append(result.accept(this, false));
  }

  @Override public Doc visitField(Decl.@NotNull StructField field, Unit unit) {
    var doc = Buffer.of(Doc.symbol("|"),
      coe(field.coerce),
      linkDef(field.ref, FIELD_CALL),
      visitTele(field.telescope));
    appendResult(doc, field.result);
    if (field.body.isDefined()) {
      doc.append(Doc.symbol("=>"));
      doc.append(field.body.get().accept(this, false));
    }
    doc.append(visitClauses(field.clauses, true));
    return Doc.sepNonEmpty(doc);
  }

  @Override public Doc visitFn(Decl.@NotNull FnDecl decl, Unit unit) {
    var prelude = Buffer.of(visitAccess(decl.accessibility(), Stmt.Accessibility.Public), Doc.styled(KEYWORD, "def"));
    prelude.appendAll(Seq.from(decl.modifiers).view().map(this::visitModifier));
    prelude.append(linkDef(decl.ref, FN_CALL));
    prelude.append(visitTele(decl.telescope));
    appendResult(prelude, decl.result);
    return Doc.cat(Doc.sepNonEmpty(prelude),
      decl.body.fold(expr -> Doc.cat(Doc.ONE_WS, Doc.symbol("=>"), Doc.ONE_WS, expr.accept(this, false)),
        clauses -> Doc.cat(Doc.line(), Doc.nest(2, visitClauses(clauses, false)))),
      Doc.emptyIf(decl.abuseBlock.isEmpty(), () -> Doc.cat(Doc.ONE_WS, Doc.styled(KEYWORD, "abusing"), Doc.ONE_WS, visitAbuse(decl.abuseBlock)))
    );
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

  private Doc visitAbuse(@NotNull ImmutableSeq<Stmt> block) {
    return Doc.vcat(block.view().map(stmt -> stmt.accept(this, Unit.unit())));
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
