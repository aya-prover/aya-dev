// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.parse;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicLinkedSeq;
import kala.collection.mutable.MutableHashSet;
import kala.control.Either;
import kala.control.Option;
import kala.function.BooleanFunction;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.value.Ref;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.aya.api.error.Reporter;
import org.aya.api.ref.LocalVar;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.parse.error.*;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.aya.core.def.PrimDef;
import org.aya.core.term.Term;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.generic.ref.GeneralizedVar;
import org.aya.generic.ref.PreLevelVar;
import org.aya.parser.AyaParser;
import org.aya.pretty.doc.Doc;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author ice1000, kiva
 */
public final class AyaProducer {
  public final @NotNull SourceFile sourceFile;
  public final @NotNull Reporter reporter;
  private @Nullable SourcePos overridingSourcePos;

  public AyaProducer(@NotNull SourceFile sourceFile, @NotNull Reporter reporter) {
    this.sourceFile = sourceFile;
    this.reporter = reporter;
  }

  public Either<ImmutableSeq<Stmt>, Expr> visitRepl(AyaParser.ReplContext ctx) {
    var expr = ctx.expr();
    if (expr != null) return Either.right(visitExpr(expr));
    return Either.left(ImmutableSeq.from(ctx.stmt()).flatMap(this::visitStmt));
  }

  public ImmutableSeq<Stmt> visitProgram(AyaParser.ProgramContext ctx) {
    return ImmutableSeq.from(ctx.stmt()).flatMap(this::visitStmt);
  }

  public Decl.PrimDecl visitPrimDecl(AyaParser.PrimDeclContext ctx) {
    var id = ctx.ID();
    var name = id.getText();
    var sourcePos = sourcePosOf(id);
    var primID = PrimDef.ID.find(name);
    if (primID == null) {
      reporter.report(new UnknownPrimError(sourcePos, name));
      throw new ParsingInterruptedException();
    }
    var lack = PrimDef.Factory.INSTANCE.checkDependency(primID);
    if (lack.isNotEmpty() && lack.get().isNotEmpty()) {
      reporter.report(new PrimDependencyError(name, lack.get(), sourcePos));
      throw new ParsingInterruptedException();
    } else if (PrimDef.Factory.INSTANCE.have(primID)) {
      reporter.report(new RedefinitionError(RedefinitionError.Kind.Prim, name, sourcePos));
      throw new ParsingInterruptedException();
    }
    var core = PrimDef.Factory.INSTANCE.factory(primID);
    var type = ctx.type();
    var assoc = ctx.assoc();
    return new Decl.PrimDecl(
      sourcePos,
      sourcePosOf(ctx),
      assoc == null ? null : makeInfix(assoc, name, core.telescope.count(Term.Param::explicit)),
      core.ref(),
      visitTelescope(ctx.tele()),
      type == null ? new Expr.ErrorExpr(sourcePos, Doc.plain("missing result")) : visitType(type)
    );
  }

  public @NotNull SeqLike<Stmt> visitStmt(AyaParser.StmtContext ctx) {
    var importCmd = ctx.importCmd();
    if (importCmd != null) return ImmutableSeq.of(visitImportCmd(importCmd));
    var mod = ctx.module();
    if (mod != null) return ImmutableSeq.of(visitModule(mod));
    var openCmd = ctx.openCmd();
    if (openCmd != null) return visitOpenCmd(openCmd);
    var decl = ctx.decl();
    if (decl != null) {
      var result = visitDecl(decl);
      return result._2.view().prepended(result._1);
    }
    var sample = ctx.sample();
    if (sample != null) return visitSample(sample);
    var levels = ctx.levels();
    if (levels != null) return ImmutableSeq.of(visitLevels(levels));
    var generalize = ctx.generalize();
    if (generalize != null) return ImmutableSeq.of(visitGeneralize(generalize));
    var remark = ctx.remark();
    if (remark != null) return ImmutableSeq.of(visitRemark(remark));
    return unreachable(ctx);
  }

  @NotNull private Remark visitRemark(AyaParser.RemarkContext remark) {
    assert overridingSourcePos == null : "Doc comments shall not nest";
    var pos = sourcePosOf(remark);
    overridingSourcePos = pos;
    var sb = new StringBuilder();
    for (var docComment : remark.DOC_COMMENT()) {
      sb.append(docComment.getText().substring(3)).append("\n");
    }
    var core = Remark.make(sb.toString(), pos, this);
    overridingSourcePos = null;
    return core;
  }

  public Generalize visitGeneralize(AyaParser.GeneralizeContext ctx) {
    return new Generalize.Variables(sourcePosOf(ctx), visitIds(ctx.ids())
      .map(id -> new GeneralizedVar(id.data(), id.sourcePos()))
      .collect(ImmutableSeq.factory()), visitType(ctx.type()));
  }

  public Generalize visitLevels(AyaParser.LevelsContext ctx) {
    return new Generalize.Levels(sourcePosOf(ctx), visitIds(ctx.ids())
      .map(t -> t.map(PreLevelVar::new))
      .collect(ImmutableSeq.factory()));
  }

  public @NotNull BindBlock visitBind(AyaParser.BindBlockContext ctx) {
    if (ctx.LOOSER() != null) return new BindBlock(sourcePosOf(ctx), new Ref<>(),
      visitQIdsComma(ctx.qIdsComma()).collect(ImmutableSeq.factory()), ImmutableSeq.empty(),
      new Ref<>(), new Ref<>());
    else if (ctx.TIGHTER() != null) return new BindBlock(sourcePosOf(ctx), new Ref<>(),
      ImmutableSeq.empty(), visitQIdsComma(ctx.qIdsComma()).collect(ImmutableSeq.factory()),
      new Ref<>(), new Ref<>());
    else return new BindBlock(sourcePosOf(ctx), new Ref<>(),
        visitLoosers(ctx.loosers()), visitTighters(ctx.tighters()),
        new Ref<>(), new Ref<>());
  }

  public @NotNull ImmutableSeq<QualifiedID> visitLoosers(List<AyaParser.LoosersContext> ctx) {
    return ctx.stream().flatMap(c -> visitQIdsComma(c.qIdsComma())).collect(ImmutableSeq.factory());
  }

  public @NotNull ImmutableSeq<QualifiedID> visitTighters(List<AyaParser.TightersContext> ctx) {
    return ctx.stream().flatMap(c -> visitQIdsComma(c.qIdsComma())).collect(ImmutableSeq.factory());
  }

  public @NotNull Stream<QualifiedID> visitQIdsComma(AyaParser.QIdsCommaContext ctx) {
    return ctx.qualifiedId().stream().map(this::visitQualifiedId);
  }

  public @NotNull ImmutableSeq<Stmt> visitSample(AyaParser.SampleContext ctx) {
    var decl = visitDecl(ctx.decl());
    var stmts = decl._2.view().prepended(decl._1);
    if (ctx.COUNTEREXAMPLE() != null) {
      var stmtOption = decl._2.firstOption(stmt -> !(stmt instanceof Decl));
      if (stmtOption.isDefined()) reporter.report(new BadCounterexampleWarn(stmtOption.get()));
      return stmts.filterIsInstance(Decl.class).<Stmt>map(Sample.Counter::new).toImmutableSeq();
    }
    return stmts.<Stmt>map(Sample.Working::new).toImmutableSeq();
  }

  private <T> T unreachable(ParserRuleContext ctx) {
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  public @NotNull Tuple2<Decl, ImmutableSeq<Stmt>> visitDecl(AyaParser.DeclContext ctx) {
    var accessibility = ctx.PRIVATE() == null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var fnDecl = ctx.fnDecl();
    if (fnDecl != null) return Tuple.of(visitFnDecl(fnDecl, accessibility), ImmutableSeq.empty());
    var dataDecl = ctx.dataDecl();
    if (dataDecl != null) return visitDataDecl(dataDecl, accessibility);
    var structDecl = ctx.structDecl();
    if (structDecl != null) return Tuple.of(visitStructDecl(structDecl, accessibility), ImmutableSeq.empty());
    var primDecl = ctx.primDecl();
    if (primDecl != null) return Tuple.of(visitPrimDecl(primDecl), ImmutableSeq.empty());
    return unreachable(ctx);
  }

  public Tuple2<@NotNull WithPos<String>, OpDecl.@Nullable OpInfo> visitDeclNameOrInfix(@NotNull AyaParser.DeclNameOrInfixContext ctx, int argc) {
    var assoc = ctx.assoc();
    var id = ctx.ID();
    var txt = id.getText();
    var pos = sourcePosOf(id);
    if (assoc == null) return Tuple.of(new WithPos<>(pos, txt), null);
    var infix = makeInfix(assoc, txt, argc);
    return Tuple.of(new WithPos<>(pos, infix.name()), infix);
  }

  private @NotNull OpDecl.OpInfo makeInfix(@NotNull AyaParser.AssocContext assoc, @NotNull String id, int argc) {
    if (assoc.INFIX() != null) return new OpDecl.OpInfo(id, Assoc.Infix, argc);
    if (assoc.INFIXL() != null) return new OpDecl.OpInfo(id, Assoc.InfixL, argc);
    if (assoc.INFIXR() != null) return new OpDecl.OpInfo(id, Assoc.InfixR, argc);
    if (assoc.MIXFIX() != null) return new OpDecl.OpInfo(id, Assoc.Mixfix, argc);
    throw new IllegalArgumentException("Unknown assoc: " + assoc.getText());
  }

  private int countExplicit(@NotNull ImmutableSeq<Expr.Param> tele) {
    return tele.count(Expr.Param::explicit);
  }

  public Decl.@NotNull FnDecl visitFnDecl(AyaParser.FnDeclContext ctx, Stmt.Accessibility accessibility) {
    var modifiers = Seq.from(ctx.fnModifiers()).view()
      .map(fn -> Tuple.of(fn, visitFnModifiers(fn)))
      .toImmutableSeq();
    var inline = modifiers.find(t -> t._2 == Modifier.Inline);
    var opaque = modifiers.find(t -> t._2 == Modifier.Opaque);
    if (inline.isDefined() && opaque.isDefined()) {
      var gunpowder = inline.get();
      reporter.report(new BadModifierWarn(sourcePosOf(gunpowder._1), gunpowder._2));
    }

    var tele = visitTelescope(ctx.tele());
    var bind = ctx.bindBlock();
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix(), countExplicit(tele));

    var dynamite = visitFnBody(ctx.fnBody());
    if (dynamite.isRight() && inline.isDefined()) {
      var gelatin = inline.get();
      reporter.report(new BadModifierWarn(sourcePosOf(gelatin._1), gelatin._2));
    }
    return new Decl.FnDecl(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(ctx),
      accessibility,
      modifiers.map(Tuple2::getValue).collect(Collectors.toCollection(
        () -> EnumSet.noneOf(Modifier.class))),
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      type(ctx.type(), sourcePosOf(ctx)),
      dynamite,
      bind == null ? BindBlock.EMPTY : visitBind(bind)
    );
  }

  public @NotNull ImmutableSeq<Expr.@NotNull Param> visitTelescope(List<AyaParser.TeleContext> telescope) {
    return ImmutableSeq.from(telescope).flatMap(t -> visitTele(t, false));
  }

  public @NotNull ImmutableSeq<Expr.@NotNull Param> visitLamTelescope(List<AyaParser.TeleContext> telescope) {
    return ImmutableSeq.from(telescope).flatMap(t -> visitTele(t, true));
  }

  public @NotNull ImmutableSeq<Expr.@NotNull Param> visitForallTelescope(List<AyaParser.TeleContext> telescope) {
    return ImmutableSeq.from(telescope).flatMap(t -> visitTele(t, true));
  }

  public @NotNull Either<Expr, ImmutableSeq<Pattern.Clause>> visitFnBody(AyaParser.FnBodyContext ctx) {
    var expr = ctx.expr();
    if (expr != null) return Either.left(visitExpr(expr));
    return Either.right(ImmutableSeq.from(ctx.clause()).map(this::visitClause));
  }

  public QualifiedID visitQualifiedId(AyaParser.QualifiedIdContext ctx) {
    return new QualifiedID(sourcePosOf(ctx),
      ctx.ID().stream().map(ParseTree::getText)
        .collect(ImmutableSeq.factory()));
  }

  public @NotNull Expr visitLiteral(AyaParser.LiteralContext ctx) {
    var pos = sourcePosOf(ctx);
    if (ctx.CALM_FACE() != null) return new Expr.HoleExpr(pos, false, null);
    var id = ctx.qualifiedId();
    if (id != null) return new Expr.UnresolvedExpr(pos, visitQualifiedId(id));
    if (ctx.TYPE() != null) return new Expr.RawUnivExpr(pos);
    if (ctx.LGOAL() != null) {
      var fillingExpr = ctx.expr();
      var filling = fillingExpr == null ? null : visitExpr(fillingExpr);
      return new Expr.HoleExpr(pos, true, filling);
    }
    var number = ctx.NUMBER();
    if (number != null) return new Expr.LitIntExpr(pos, Integer.parseInt(number.getText()));
    var string = ctx.STRING();
    if (string != null) return new Expr.LitStringExpr(pos, string.getText());
    return unreachable(ctx);
  }

  private @NotNull LocalVar visitParamLiteral(AyaParser.LiteralContext ctx) {
    var idCtx = ctx.qualifiedId();
    if (idCtx == null) {
      reporter.report(new ParseError(sourcePosOf(ctx),
        "`" + ctx.getText() + "` is not a parameter name"));
      throw new ParsingInterruptedException();
    }
    var id = visitQualifiedId(idCtx);
    if (id.isQualified()) {
      reporter.report(new ParseError(sourcePosOf(ctx),
        "parameter name `" + ctx.getText() + "` should not be qualified"));
      throw new ParsingInterruptedException();
    }
    return new LocalVar(id.justName(), sourcePosOf(idCtx));
  }

  public @NotNull ImmutableSeq<Expr.@NotNull Param> visitTele(AyaParser.TeleContext ctx, boolean isParamLiteral) {
    var literal = ctx.literal();
    if (literal != null) {
      var pos = sourcePosOf(ctx);
      return ImmutableSeq.of(isParamLiteral
        ? new Expr.Param(pos, visitParamLiteral(literal), type(null, pos), false, true)
        : new Expr.Param(pos, Constants.randomlyNamed(pos), visitLiteral(literal), false, true)
      );
    }
    var teleBinder = ctx.teleBinder();
    var teleMaybeTypedExpr = ctx.teleMaybeTypedExpr();
    if (teleBinder != null) {
      var type = teleBinder.expr();
      if (type != null) {
        var pos = sourcePosOf(ctx);
        return ImmutableSeq.of(new Expr.Param(pos, Constants.randomlyNamed(pos), visitExpr(type), false, true));
      }
      teleMaybeTypedExpr = teleBinder.teleMaybeTypedExpr();
    }
    if (ctx.LPAREN() != null) return visitTeleMaybeTypedExpr(teleMaybeTypedExpr).apply(true);
    if (ctx.LBRACE() != null) return visitTeleMaybeTypedExpr(teleMaybeTypedExpr).apply(false);
    return unreachable(ctx);
  }

  public @NotNull BooleanFunction<ImmutableSeq<Expr.Param>>
  visitTeleMaybeTypedExpr(AyaParser.TeleMaybeTypedExprContext ctx) {
    var ids = ctx.ids();
    var type = type(ctx.type(), sourcePosOf(ids));
    var pattern = ctx.PATTERN_KW() != null;
    return explicit -> visitIds(ids)
      .map(v -> new Expr.Param(v.sourcePos(), LocalVar.from(v), type, pattern, explicit))
      .collect(ImmutableSeq.factory());
  }

  public @NotNull Expr visitExpr(AyaParser.ExprContext ctx) {
    return switch (ctx) {
      case AyaParser.SingleContext sin -> visitAtom(sin.atom());
      case AyaParser.AppContext app -> visitApp(app);
      case AyaParser.ProjContext proj -> visitProj(proj);
      case AyaParser.PiContext pi -> visitPi(pi);
      case AyaParser.SigmaContext sig -> visitSigma(sig);
      case AyaParser.LamContext lam -> visitLam(lam);
      case AyaParser.ArrContext arr -> visitArr(arr);
      case AyaParser.NewContext n -> visitNew(n);
      case AyaParser.LsucContext lsuc -> visitLsuc(lsuc);
      case AyaParser.LmaxContext lmax -> visitLmax(lmax);
      case AyaParser.ForallContext forall -> visitForall(forall);
      // TODO: match
      default -> throw new UnsupportedOperationException("TODO: " + ctx.getClass());
    };
  }

  public @NotNull Expr visitLsuc(AyaParser.LsucContext ctx) {
    return new Expr.LSucExpr(sourcePosOf(ctx), visitAtom(ctx.atom()));
  }

  public @NotNull Expr visitLmax(AyaParser.LmaxContext ctx) {
    return new Expr.LMaxExpr(sourcePosOf(ctx), ImmutableSeq.from(ctx.atom()).map(this::visitAtom));
  }

  public @NotNull Expr visitNew(AyaParser.NewContext ctx) {
    return new Expr.NewExpr(
      sourcePosOf(ctx),
      visitExpr(ctx.expr()),
      ImmutableSeq.from(ctx.newArg())
        .map(na -> new Expr.Field(na.ID().getText(), visitIds(na.ids())
          .map(t -> new WithPos<>(t.sourcePos(), LocalVar.from(t)))
          .collect(ImmutableSeq.factory()), visitExpr(na.expr())))
    );
  }

  public @NotNull Expr visitArr(AyaParser.ArrContext ctx) {
    var expr0 = ctx.expr(0);
    var to = visitExpr(ctx.expr(1));
    var pos = sourcePosOf(expr0);
    var param = new Expr.Param(pos, Constants.randomlyNamed(pos), visitExpr(expr0), false, true);
    return new Expr.PiExpr(sourcePosOf(ctx), false, param, to);
  }

  public @NotNull Expr visitApp(AyaParser.AppContext ctx) {
    var head = new Expr.NamedArg(true, visitExpr(ctx.expr()));
    var tail = ctx.argument().stream()
      .map(this::visitArgument)
      .collect(DynamicLinkedSeq.factory());
    tail.push(head);
    return new Expr.BinOpSeq(sourcePosOf(ctx), tail.toImmutableSeq());
  }

  public @NotNull Expr visitAtom(AyaParser.AtomContext ctx) {
    var literal = ctx.literal();
    if (literal != null) return visitLiteral(literal);

    var expr = ctx.exprList().expr();
    if (expr.size() == 1) return newBinOPScope(visitExpr(expr.get(0)));
    // if (expr.size() == 1) return visitExpr(expr.get(0));
    return new Expr.TupExpr(
      sourcePosOf(ctx),
      expr.stream()
        .map(this::visitExpr)
        .collect(ImmutableSeq.factory())
    );
  }

  public @NotNull Expr.NamedArg visitArgument(AyaParser.ArgumentContext ctx) {
    var atom = ctx.atom();
    if (atom != null) {
      var fixes = ctx.projFix();
      var expr = visitAtom(atom);
      var projected = ImmutableSeq.from(fixes)
        .foldLeft(Tuple.of(sourcePosOf(ctx), expr),
          (acc, proj) -> Tuple.of(acc._2.sourcePos(), buildProj(acc._1, acc._2, proj)))
        ._2;
      return new Expr.NamedArg(true, projected);
    }
    // assert ctx.LBRACE() != null;
    var id = ctx.ID();
    if (id != null) return new Expr.NamedArg(false, id.getText(), visitExpr(ctx.expr()));
    var items = ImmutableSeq.from(ctx.exprList().expr()).map(this::visitExpr);
    if (ctx.ULEVEL() != null) {
      var univArgsExpr = new Expr.RawUnivArgsExpr(sourcePosOf(ctx), items);
      return new Expr.NamedArg(false, univArgsExpr);
    }
    if (items.sizeEquals(1)) return new Expr.NamedArg(false, newBinOPScope(items.first()));
    var tupExpr = new Expr.TupExpr(sourcePosOf(ctx), items);
    return new Expr.NamedArg(false, tupExpr);
  }

  /**
   * [kiva]: make `(expr)` into a new BinOP parser scope
   * so the `f (+)` becomes passing `+` as an argument to function `f`.
   * this should be a workaround?
   * see base/src/test/resources/success/binop.aya
   */
  public @NotNull Expr newBinOPScope(@NotNull Expr expr) {
    return new Expr.BinOpSeq(expr.sourcePos(),
      ImmutableSeq.of(new Expr.NamedArg(true, expr)));
  }

  public @NotNull Pattern newBinOPScope(@NotNull Pattern expr, @Nullable LocalVar as) {
    return new Pattern.BinOpSeq(expr.sourcePos(),
      ImmutableSeq.of(expr), as, expr.explicit());
  }

  public Expr.@NotNull LamExpr visitLam(AyaParser.LamContext ctx) {
    return (Expr.LamExpr) buildLam(
      sourcePosOf(ctx),
      visitLamTelescope(ctx.tele()).view(),
      visitLamBody(ctx)
    );
  }

  public static @NotNull Expr buildLam(
    SourcePos sourcePos,
    SeqLike<Expr.Param> params,
    Expr body
  ) {
    if (params.isEmpty()) return body;
    return new Expr.LamExpr(
      sourcePos,
      params.first(),
      buildLam(sourcePosForSubExpr(sourcePos.file(), params, body), params.view().drop(1), body)
    );
  }

  private @NotNull Expr visitLamBody(@NotNull AyaParser.LamContext ctx) {
    var bodyExpr = ctx.expr();

    if (bodyExpr == null) {
      var impliesToken = ctx.IMPLIES();
      var bodyHolePos = impliesToken == null
        ? sourcePosOf(ctx)
        : sourcePosOf(impliesToken);

      return new Expr.HoleExpr(bodyHolePos, false, null);
    }

    return visitExpr(bodyExpr);
  }

  public Expr.@NotNull SigmaExpr visitSigma(AyaParser.SigmaContext ctx) {
    return new Expr.SigmaExpr(
      sourcePosOf(ctx),
      false,
      visitTelescope(ctx.tele()).appended(new Expr.Param(
        visitExpr(ctx.expr()).sourcePos(),
        Constants.anonymous(),
        visitExpr(ctx.expr()),
        false, true))
    );
  }

  public Expr.@NotNull PiExpr visitPi(AyaParser.PiContext ctx) {
    return (Expr.PiExpr) buildPi(
      sourcePosOf(ctx),
      false,
      visitTelescope(ctx.tele()).view(),
      visitExpr(ctx.expr())
    );
  }

  public Expr.@NotNull PiExpr visitForall(AyaParser.ForallContext ctx) {
    return (Expr.PiExpr) buildPi(
      sourcePosOf(ctx),
      false,
      visitForallTelescope(ctx.tele()).view(),
      visitExpr(ctx.expr())
    );
  }

  public static @NotNull Expr buildPi(
    SourcePos sourcePos,
    boolean co,
    SeqLike<Expr.Param> params,
    Expr body
  ) {
    if (params.isEmpty()) return body;
    var first = params.first();
    return new Expr.PiExpr(
      sourcePos,
      co,
      first,
      buildPi(sourcePosForSubExpr(sourcePos.file(), params, body), co, params.view().drop(1), body)
    );
  }

  @NotNull
  private static SourcePos sourcePosForSubExpr(@NotNull SourceFile sourceFile, SeqLike<Expr.Param> params, Expr body) {
    var restParamSourcePos = params.stream().skip(1)
      .map(Expr.Param::sourcePos)
      .reduce(SourcePos.NONE, (acc, it) -> {
        if (acc == SourcePos.NONE) return it;
        return new SourcePos(sourceFile, acc.tokenStartIndex(), it.tokenEndIndex(),
          acc.startLine(), acc.startColumn(), it.endLine(), it.endColumn());
      });
    var bodySourcePos = body.sourcePos();
    return new SourcePos(
      sourceFile,
      restParamSourcePos.tokenStartIndex(),
      bodySourcePos.tokenEndIndex(),
      restParamSourcePos.startLine(),
      restParamSourcePos.startColumn(),
      bodySourcePos.endLine(),
      bodySourcePos.endColumn()
    );
  }

  public Expr.@NotNull ProjExpr visitProj(AyaParser.ProjContext proj) {
    return buildProj(sourcePosOf(proj), visitExpr(proj.expr()), proj.projFix());
  }

  private Expr.@NotNull ProjExpr buildProj(@NotNull SourcePos sourcePos,
                                           @NotNull Expr projectee,
                                           @NotNull AyaParser.ProjFixContext fix) {
    var number = fix.NUMBER();
    return new Expr.ProjExpr(
      sourcePos,
      projectee,
      number != null
        ? Either.left(Integer.parseInt(number.getText()))
        : Either.right(new WithPos<>(sourcePosOf(fix), fix.ID().getText())),
      new Ref<>(null)
    );
  }

  public @NotNull Tuple2<Decl, ImmutableSeq<Stmt>>
  visitDataDecl(AyaParser.DataDeclContext ctx, Stmt.Accessibility accessibility) {
    var bind = ctx.bindBlock();
    var openAccessibility = ctx.PUBLIC() != null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var body = ctx.dataBody().stream().map(this::visitDataBody).collect(ImmutableSeq.factory());
    checkRedefinition(RedefinitionError.Kind.Ctor,
      body.view().map(ctor -> new WithPos<>(ctor.sourcePos, ctor.ref.name())));
    var tele = visitTelescope(ctx.tele());
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix(), countExplicit(tele));
    var data = new Decl.DataDecl(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(ctx),
      accessibility,
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      type(ctx.type(), sourcePosOf(ctx)),
      body,
      bind == null ? BindBlock.EMPTY : visitBind(bind)
    );
    return Tuple2.of(data, ctx.OPEN() == null ? ImmutableSeq.empty() : ImmutableSeq.of(
      new Command.Open(
        nameOrInfix._1.sourcePos(),
        openAccessibility,
        new QualifiedID(sourcePosOf(ctx), nameOrInfix._1.data()),
        Command.Open.UseHide.EMPTY
      )
    ));
  }

  public @NotNull Expr type(@Nullable AyaParser.TypeContext typeCtx, SourcePos sourcePos) {
    return typeCtx == null
      ? new Expr.HoleExpr(sourcePos, false, null)
      : visitType(typeCtx);
  }

  private @NotNull Decl.DataCtor visitDataBody(AyaParser.DataBodyContext ctx) {
    if (ctx instanceof AyaParser.DataCtorsContext dcc) return visitDataCtor(ImmutableSeq.empty(), dcc.dataCtor());
    if (ctx instanceof AyaParser.DataClausesContext dcc) return visitDataCtorClause(dcc.dataCtorClause());
    return unreachable(ctx);
  }

  public Decl.DataCtor visitDataCtor(@NotNull ImmutableSeq<Pattern> patterns, AyaParser.DataCtorContext ctx) {
    var tele = visitTelescope(ctx.tele());
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix(), countExplicit(tele));
    var bind = ctx.bindBlock();
    return new Decl.DataCtor(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(ctx),
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      visitClauses(ctx.clauses()),
      patterns,
      ctx.COERCE() != null,
      bind == null ? BindBlock.EMPTY : visitBind(bind)
    );
  }

  public ImmutableSeq<Pattern.Clause> visitClauses(@Nullable AyaParser.ClausesContext ctx) {
    if (ctx == null) return ImmutableSeq.empty();
    return ImmutableSeq.from(ctx.clause()).map(this::visitClause);
  }

  public @NotNull Decl.DataCtor visitDataCtorClause(AyaParser.DataCtorClauseContext ctx) {
    return visitDataCtor(visitPatterns(ctx.patterns()), ctx.dataCtor());
  }

  public @NotNull Pattern visitPattern(AyaParser.PatternContext ctx) {
    return visitAtomPatterns(ctx.atomPatterns()).apply(true, null);
  }

  public BiFunction<Boolean, LocalVar, Pattern> visitAtomPatterns(@NotNull AyaParser.AtomPatternsContext ctx) {
    var atoms = ctx.atomPattern().stream()
      .map(this::visitAtomPattern)
      .collect(ImmutableSeq.factory());
    if (atoms.sizeEquals(1)) return (ex, as) -> newBinOPScope(atoms.first().apply(ex), as);

    return (ex, as) -> new Pattern.BinOpSeq(
      sourcePosOf(ctx),
      atoms.view().map(p -> p.apply(true)).toImmutableSeq(),
      as, ex
    );
  }

  public @NotNull BooleanFunction<Pattern> visitAtomPattern(AyaParser.AtomPatternContext ctx) {
    var sourcePos = sourcePosOf(ctx);
    if (ctx.LPAREN() != null || ctx.LBRACE() != null) {
      var forceEx = ctx.LPAREN() != null;
      var id = ctx.ID();
      var as = id != null ? new LocalVar(id.getText(), sourcePosOf(id)) : null;
      var tupElem = ctx.patterns().pattern().stream()
        .map(t -> visitAtomPatterns(t.atomPatterns()))
        .collect(ImmutableSeq.factory());
      return tupElem.sizeEquals(1)
        ? (exIgnored -> newBinOPScope(tupElem.first().apply(forceEx, as), as))
        : (exIgnored -> new Pattern.Tuple(
        sourcePos,
        forceEx,
        tupElem.map(p -> p.apply(true, null)),
        as));
    }
    if (ctx.CALM_FACE() != null) return ex -> new Pattern.CalmFace(sourcePos, ex);
    var number = ctx.NUMBER();
    if (number != null) return ex -> new Pattern.Number(sourcePos, ex, Integer.parseInt(number.getText()));
    var id = ctx.ID();
    if (id != null) return ex -> new Pattern.Bind(sourcePos, ex, new LocalVar(id.getText(), sourcePosOf(id)));
    if (ctx.ABSURD() != null) return ex -> new Pattern.Absurd(sourcePos, ex);

    return unreachable(ctx);
  }

  public @NotNull ImmutableSeq<Pattern> visitPatterns(AyaParser.PatternsContext ctx) {
    return ctx.pattern().stream()
      .map(this::visitPattern)
      .collect(ImmutableSeq.factory());
  }

  public @NotNull Pattern.Clause visitClause(AyaParser.ClauseContext ctx) {
    return new Pattern.Clause(sourcePosOf(ctx), visitPatterns(ctx.patterns()),
      Option.of(ctx.expr()).map(this::visitExpr));
  }

  private void checkRedefinition(@NotNull RedefinitionError.Kind kind,
                                 @NotNull SeqLike<WithPos<String>> names) {
    var set = MutableHashSet.<String>of();
    var redefs = names.view().filterNot(n -> set.add(n.data())).toImmutableSeq();
    if (redefs.isNotEmpty()) {
      var last = redefs.last();
      reporter.report(new RedefinitionError(kind, last.data(), last.sourcePos()));
      throw new ParsingInterruptedException();
    }
  }

  public @NotNull Decl.StructDecl visitStructDecl(AyaParser.StructDeclContext ctx, Stmt.Accessibility accessibility) {
    var bind = ctx.bindBlock();
    var fields = visitFields(ctx.field());
    checkRedefinition(RedefinitionError.Kind.Field,
      fields.view().map(field -> new WithPos<>(field.sourcePos, field.ref.name())));
    var tele = visitTelescope(ctx.tele());
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix(), countExplicit(tele));
    return new Decl.StructDecl(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(ctx),
      accessibility,
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      type(ctx.type(), sourcePosOf(ctx)),
      // ctx.ids(),
      fields,
      bind == null ? BindBlock.EMPTY : visitBind(bind)
    );
  }

  private ImmutableSeq<Decl.StructField> visitFields(List<AyaParser.FieldContext> field) {
    return ImmutableSeq.from(field).map(fieldCtx -> {
      if (fieldCtx instanceof AyaParser.FieldDeclContext fieldDecl) return visitFieldDecl(fieldDecl);
      else if (fieldCtx instanceof AyaParser.FieldImplContext fieldImpl) return visitFieldImpl(fieldImpl);
      else throw new IllegalArgumentException(fieldCtx.getClass() + " is neither FieldDecl nor FieldImpl!");
    });
  }

  public Decl.StructField visitFieldImpl(AyaParser.FieldImplContext ctx) {
    var tele = visitTelescope(ctx.tele());
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix(), countExplicit(tele));
    var bind = ctx.bindBlock();
    return new Decl.StructField(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(ctx),
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      type(ctx.type(), sourcePosOf(ctx)),
      Option.of(ctx.expr()).map(this::visitExpr),
      ImmutableSeq.empty(),
      false,
      bind == null ? BindBlock.EMPTY : visitBind(bind)
    );
  }

  public Decl.StructField visitFieldDecl(AyaParser.FieldDeclContext ctx) {
    var tele = visitTelescope(ctx.tele());
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix(), countExplicit(tele));
    var bind = ctx.bindBlock();
    return new Decl.StructField(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(ctx),
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      type(ctx.type(), sourcePosOf(ctx)),
      Option.none(),
      visitClauses(ctx.clauses()),
      ctx.COERCE() != null,
      bind == null ? BindBlock.EMPTY : visitBind(bind)
    );
  }

  public @NotNull Expr visitType(@NotNull AyaParser.TypeContext ctx) {
    return visitExpr(ctx.expr());
  }

  public @NotNull Stmt visitImportCmd(AyaParser.ImportCmdContext ctx) {
    final var id = ctx.ID();
    return new Command.Import(
      sourcePosOf(ctx.qualifiedId()),
      visitQualifiedId(ctx.qualifiedId()),
      id == null ? null : id.getText()
    );
  }

  public @NotNull ImmutableSeq<Stmt> visitOpenCmd(AyaParser.OpenCmdContext ctx) {
    var accessibility = ctx.PUBLIC() == null
      ? Stmt.Accessibility.Private
      : Stmt.Accessibility.Public;
    var useHide = ctx.useHide();
    var modNameCtx = ctx.qualifiedId();
    var namePos = sourcePosOf(modNameCtx);
    var modName = visitQualifiedId(modNameCtx);
    var open = new Command.Open(
      namePos,
      accessibility,
      modName,
      useHide != null ? visitUseHide(useHide) : Command.Open.UseHide.EMPTY
    );
    if (ctx.IMPORT() != null) return ImmutableSeq.of(
      new Command.Import(namePos, modName, null),
      open
    );
    else return ImmutableSeq.of(open);
  }

  public Command.Open.UseHide useHide(List<AyaParser.UseHideListContext> ctxs, Command.Open.UseHide.Strategy strategy) {
    return new Command.Open.UseHide(
      ctxs.stream()
        .map(AyaParser.UseHideListContext::idsComma)
        .flatMap(this::visitIdsComma)
        .collect(ImmutableSeq.factory()),
      strategy);
  }

  public @NotNull Command.Open.UseHide visitUseHide(@NotNull AyaParser.UseHideContext ctx) {
    return useHide(ctx.useHideList(), ctx.USING() != null
      ? Command.Open.UseHide.Strategy.Using : Command.Open.UseHide.Strategy.Hiding);
  }

  public @NotNull Command.Module visitModule(AyaParser.ModuleContext ctx) {
    var id = ctx.ID();
    return new Command.Module(
      sourcePosOf(id), id.getText(),
      ImmutableSeq.from(ctx.stmt()).flatMap(this::visitStmt)
    );
  }

  public @NotNull Stream<WithPos<String>> visitIds(AyaParser.IdsContext ctx) {
    return ctx.ID().stream().map(id -> new WithPos<>(sourcePosOf(id), id.getText()));
  }

  public @NotNull Stream<String> visitIdsComma(AyaParser.IdsCommaContext ctx) {
    return ctx.ID().stream().map(ParseTree::getText);
  }

  public @NotNull Modifier visitFnModifiers(AyaParser.FnModifiersContext ctx) {
    if (ctx.OPAQUE() != null) return Modifier.Opaque;
    if (ctx.INLINE() != null) return Modifier.Inline;
    if (ctx.OVERLAP() != null) return Modifier.Overlap;
    /*if (ctx.PATTERN_KW() != null)*/
    return Modifier.Pattern;
  }

  private @NotNull SourcePos sourcePosOf(ParserRuleContext ctx) {
    if (overridingSourcePos != null) return overridingSourcePos;
    var start = ctx.getStart();
    var end = ctx.getStop();
    return new SourcePos(
      sourceFile,
      start.getStartIndex(),
      end.getStopIndex(),
      start.getLine(),
      start.getCharPositionInLine(),
      end.getLine(),
      end.getCharPositionInLine() + end.getText().length() - 1
    );
  }

  private @NotNull SourcePos sourcePosOf(TerminalNode node) {
    if (overridingSourcePos != null) return overridingSourcePos;
    var token = node.getSymbol();
    var line = token.getLine();
    return new SourcePos(
      sourceFile,
      token.getStartIndex(),
      token.getStopIndex(),
      line,
      token.getCharPositionInLine(),
      line,
      token.getCharPositionInLine() + token.getText().length() - 1
    );
  }
}
