// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.parse;

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
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFile;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.PreLevelVar;
import org.aya.api.util.Assoc;
import org.aya.api.util.WithPos;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.desugar.BinOpParser;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.resolve.error.PrimDependencyError;
import org.aya.concrete.resolve.error.RedefinitionError;
import org.aya.concrete.resolve.error.UnknownPrimError;
import org.aya.concrete.stmt.*;
import org.aya.core.def.PrimDef;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.parser.AyaParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
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
      assoc == null ? null : makeInfix(assoc, name),
      core.ref(),
      visitTelescope(ctx.tele()),
      type == null ? null : visitType(type)
    );
  }

  public @NotNull SeqLike<Stmt> visitStmt(AyaParser.StmtContext ctx) {
    var importCmd = ctx.importCmd();
    if (importCmd != null) return ImmutableSeq.of(visitImportCmd(importCmd));
    var openCmd = ctx.openCmd();
    if (openCmd != null) return visitOpenCmd(openCmd);
    var decl = ctx.decl();
    if (decl != null) {
      var result = visitDecl(decl);
      return result._2.view().prepended(result._1);
    }
    var sample = ctx.sample();
    if (sample != null) return ImmutableSeq.of(visitSample(sample));
    var mod = ctx.module();
    if (mod != null) return ImmutableSeq.of(visitModule(mod));
    var levels = ctx.levels();
    if (levels != null) return ImmutableSeq.of(visitLevels(levels));
    var bind = ctx.bind();
    if (bind != null) return ImmutableSeq.of(visitBind(bind));
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

  public Generalize visitLevels(AyaParser.LevelsContext ctx) {
    return new Generalize.Levels(sourcePosOf(ctx), visitIds(ctx.ids())
      .map(t -> t.map(PreLevelVar::new))
      .collect(ImmutableSeq.factory()));
  }

  public Command.@NotNull Bind visitBind(AyaParser.BindContext ctx) {
    var bindOp = ctx.qualifiedId();
    return new Command.Bind(
      sourcePosOf(ctx),
      visitQualifiedId(bindOp.get(0)),
      ctx.TIGHTER() != null ? Command.BindPred.Tighter : Command.BindPred.Looser,
      visitQualifiedId(bindOp.get(1)),
      new Ref<>(null),
      new Ref<>(null),
      new Ref<>(null)
    );
  }

  public @NotNull Sample visitSample(AyaParser.SampleContext ctx) {
    var decl = visitDecl(ctx.decl());
    // TODO: submodule in example modules
    return ctx.COUNTEREXAMPLE() != null ? new Sample.Counter(decl._1) : new Sample.Working(decl._1);
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

  public Tuple2<@NotNull String, OpDecl.@Nullable Operator> visitDeclNameOrInfix(@NotNull AyaParser.DeclNameOrInfixContext ctx) {
    var assoc = ctx.assoc();
    var id = ctx.ID().getText();
    if (assoc == null) return Tuple.of(id, null);
    var infix = makeInfix(assoc, id);
    return Tuple.of(infix.name(), infix);
  }

  private @NotNull OpDecl.Operator makeInfix(@NotNull AyaParser.AssocContext assoc, @NotNull String id) {
    if (assoc.INFIX() != null) return new OpDecl.Operator(id, Assoc.Infix);
    if (assoc.INFIXL() != null) return new OpDecl.Operator(id, Assoc.InfixL);
    if (assoc.INFIXR() != null) return new OpDecl.Operator(id, Assoc.InfixR);
    throw new IllegalArgumentException("Unknown assoc: " + assoc.getText());
  }

  public Decl.@NotNull FnDecl visitFnDecl(AyaParser.FnDeclContext ctx, Stmt.Accessibility accessibility) {
    var modifiers = ctx.fnModifiers().stream()
      .map(this::visitFnModifiers)
      .distinct()
      .collect(Collectors.toCollection(() -> EnumSet.noneOf(Modifier.class)));
    var abuseCtx = ctx.abuse();
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix());

    return new Decl.FnDecl(
      sourcePosOf(ctx.declNameOrInfix()),
      sourcePosOf(ctx),
      accessibility,
      modifiers,
      nameOrInfix._2,
      nameOrInfix._1,
      visitTelescope(ctx.tele()),
      type(ctx.type(), sourcePosOf(ctx)),
      visitFnBody(ctx.fnBody()),
      abuseCtx == null ? ImmutableSeq.empty() : visitAbuse(abuseCtx)
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

  public @NotNull ImmutableSeq<@NotNull Stmt> visitAbuse(AyaParser.AbuseContext ctx) {
    return ImmutableSeq.from(ctx.stmt()).flatMap(this::visitStmt);
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
        ? new Expr.Param(pos, visitParamLiteral(literal), type(null, pos), true)
        : new Expr.Param(pos, Constants.randomlyNamed(pos), visitLiteral(literal), true)
      );
    }
    var teleBinder = ctx.teleBinder();
    var teleMaybeTypedExpr = ctx.teleMaybeTypedExpr();
    if (teleBinder != null) {
      var type = teleBinder.expr();
      if (type != null) {
        var pos = sourcePosOf(ctx);
        return ImmutableSeq.of(new Expr.Param(pos, Constants.randomlyNamed(pos), visitExpr(type), true));
      }
      teleMaybeTypedExpr = teleBinder.teleMaybeTypedExpr();
    }
    if (ctx.LPAREN() != null) return visitTeleMaybeTypedExpr(teleMaybeTypedExpr).apply(true);
    if (ctx.LBRACE() != null) return visitTeleMaybeTypedExpr(teleMaybeTypedExpr).apply(false);
    return unreachable(ctx);
  }

  public @NotNull
  Function<Boolean, ImmutableSeq<Expr.Param>> visitTeleMaybeTypedExpr(AyaParser.TeleMaybeTypedExprContext ctx) {
    var type = type(ctx.type(), sourcePosOf(ctx.ids()));
    return explicit -> visitIds(ctx.ids())
      .map(v -> new Expr.Param(v.sourcePos(), WithPos.toVar(v), type, explicit))
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
          .map(t -> new WithPos<>(t.sourcePos(), WithPos.toVar(t)))
          .collect(ImmutableSeq.factory()), visitExpr(na.expr())))
    );
  }

  public @NotNull Expr visitArr(AyaParser.ArrContext ctx) {
    var from = visitExpr(ctx.expr(0));
    var to = visitExpr(ctx.expr(1));
    var pos = sourcePosOf(ctx.expr(0));
    return new Expr.PiExpr(
      sourcePosOf(ctx),
      false,
      new Expr.Param(pos, Constants.randomlyNamed(pos), from, true),
      to);
  }

  public @NotNull Expr visitApp(AyaParser.AppContext ctx) {
    var head = new BinOpParser.Elem(null, visitExpr(ctx.expr()), true);
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

  public @NotNull BinOpParser.Elem visitArgument(AyaParser.ArgumentContext ctx) {
    var atom = ctx.atom();
    if (atom != null) {
      var fixes = ctx.projFix();
      var expr = visitAtom(atom);
      var projected = ImmutableSeq.from(fixes)
        .foldLeft(Tuple.of(sourcePosOf(ctx), expr),
          (acc, proj) -> Tuple.of(acc._2.sourcePos(), buildProj(acc._1, acc._2, proj)))
        ._2;
      return new BinOpParser.Elem(projected, true);
    }
    // assert ctx.LBRACE() != null;
    var id = ctx.ID();
    if (id != null) return new BinOpParser.Elem(id.getText(), visitExpr(ctx.expr()), false);
    var items = ImmutableSeq.from(ctx.exprList().expr()).map(this::visitExpr);
    if (ctx.ULEVEL() != null) {
      var univArgsExpr = new Expr.RawUnivArgsExpr(sourcePosOf(ctx), items);
      return new BinOpParser.Elem(univArgsExpr, false);
    }
    if (items.sizeEquals(1)) return new BinOpParser.Elem(newBinOPScope(items.first()), false);
    var tupExpr = new Expr.TupExpr(sourcePosOf(ctx), items);
    return new BinOpParser.Elem(tupExpr, false);
  }

  /**
   * [kiva]: make `(expr)` into a new BinOP parser scope
   * so the `f (+)` becomes passing `+` as an argument to function `f`.
   * this should be a workaround?
   * see base/src/test/resources/success/binop.aya
   */
  public @NotNull Expr newBinOPScope(@NotNull Expr expr) {
    return new Expr.BinOpSeq(expr.sourcePos(),
      ImmutableSeq.of(new BinOpParser.Elem(expr, true)));
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
        true))
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

  public @NotNull
  Tuple2<Decl, ImmutableSeq<Stmt>> visitDataDecl(AyaParser.DataDeclContext ctx, Stmt.Accessibility accessibility) {
    var abuseCtx = ctx.abuse();
    var openAccessibility = ctx.PUBLIC() != null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var body = ctx.dataBody().stream().map(this::visitDataBody).collect(ImmutableSeq.factory());
    checkRedefinition(RedefinitionError.Kind.Ctor,
      body.view().map(ctor -> new WithPos<>(ctor.sourcePos, ctor.ref.name())));
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix());
    var data = new Decl.DataDecl(
      sourcePosOf(ctx.declNameOrInfix()),
      sourcePosOf(ctx),
      accessibility,
      nameOrInfix._2,
      nameOrInfix._1,
      visitTelescope(ctx.tele()),
      type(ctx.type(), sourcePosOf(ctx)),
      body,
      abuseCtx == null ? ImmutableSeq.empty() : visitAbuse(abuseCtx)
    );
    return Tuple2.of(data, ctx.OPEN() == null ? ImmutableSeq.empty() : ImmutableSeq.of(
      new Command.Open(
        sourcePosOf(ctx.declNameOrInfix()),
        openAccessibility,
        new QualifiedID(sourcePosOf(ctx), nameOrInfix._1),
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
    var telescope = visitTelescope(ctx.tele());
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix());

    return new Decl.DataCtor(
      sourcePosOf(ctx.declNameOrInfix()),
      sourcePosOf(ctx),
      nameOrInfix._2,
      nameOrInfix._1,
      telescope,
      visitClauses(ctx.clauses()),
      patterns,
      ctx.COERCE() != null
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
    if (atoms.sizeEquals(1)) return (ex, as) -> atoms.first().apply(ex);

    // this `apply` does nothing on explicitness because we only used its bind
    var first = atoms.first().apply(true);
    if (!(first instanceof Pattern.Bind bind)) {
      reporter.report(new ParseError(first.sourcePos(),
        "`" + first.toDoc(DistillerOptions.DEBUG).debugRender() + "` is not a constructor name"));
      throw new ParsingInterruptedException();
    }
    return (ex, as) -> new Pattern.Ctor(
      sourcePosOf(ctx),
      ex,
      new WithPos<>(bind.sourcePos(), bind.bind().name()),
      atoms.view().drop(1).map(p -> p.apply(true)).toImmutableSeq(),
      as,
      new Ref<>(null)
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
        ? (exIgnored -> tupElem.first().apply(forceEx, as))
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
    if (id != null)
      return ex -> new Pattern.Bind(sourcePos, ex, new LocalVar(id.getText(), sourcePosOf(id)), new Ref<>());
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
    var abuseCtx = ctx.abuse();
    var fields = visitFields(ctx.field());
    checkRedefinition(RedefinitionError.Kind.Field,
      fields.view().map(field -> new WithPos<>(field.sourcePos, field.ref.name())));
    var nameOrIndex = visitDeclNameOrInfix(ctx.declNameOrInfix());
    return new Decl.StructDecl(
      sourcePosOf(ctx.declNameOrInfix()),
      sourcePosOf(ctx),
      accessibility,
      nameOrIndex._2,
      nameOrIndex._1,
      visitTelescope(ctx.tele()),
      type(ctx.type(), sourcePosOf(ctx)),
      // ctx.ids(),
      fields,
      abuseCtx == null ? ImmutableSeq.empty() : visitAbuse(abuseCtx)
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
    var telescope = visitTelescope(ctx.tele());
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix());
    return new Decl.StructField(
      sourcePosOf(ctx.declNameOrInfix()),
      sourcePosOf(ctx),
      nameOrInfix._2,
      nameOrInfix._1,
      telescope,
      type(ctx.type(), sourcePosOf(ctx)),
      Option.of(ctx.expr()).map(this::visitExpr),
      ImmutableSeq.empty(),
      false
    );
  }

  public Decl.StructField visitFieldDecl(AyaParser.FieldDeclContext ctx) {
    var telescope = visitTelescope(ctx.tele());
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix());
    return new Decl.StructField(
      sourcePosOf(ctx.declNameOrInfix()),
      sourcePosOf(ctx),
      nameOrInfix._2,
      nameOrInfix._1,
      telescope,
      type(ctx.type(), sourcePosOf(ctx)),
      Option.none(),
      visitClauses(ctx.clauses()),
      ctx.COERCE() != null
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

  public Command.Open.UseHide visitUse(List<AyaParser.UseContext> ctxs) {
    return new Command.Open.UseHide(
      ctxs.stream()
        .map(AyaParser.UseContext::useHideList)
        .map(AyaParser.UseHideListContext::idsComma)
        .flatMap(this::visitIdsComma)
        .collect(ImmutableSeq.factory()),
      Command.Open.UseHide.Strategy.Using);
  }

  public Command.Open.UseHide visitHide(List<AyaParser.HideContext> ctxs) {
    return new Command.Open.UseHide(
      ctxs.stream()
        .map(AyaParser.HideContext::useHideList)
        .map(AyaParser.UseHideListContext::idsComma)
        .flatMap(this::visitIdsComma)
        .collect(ImmutableSeq.factory()),
      Command.Open.UseHide.Strategy.Hiding);
  }

  public @NotNull Command.Open.UseHide visitUseHide(@NotNull AyaParser.UseHideContext ctx) {
    var use = ctx.use();
    if (use != null) return visitUse(use);
    return visitHide(ctx.hide());
  }

  public @NotNull Command.Module visitModule(AyaParser.ModuleContext ctx) {
    return new Command.Module(
      sourcePosOf(ctx.ID()),
      ctx.ID().getText(),
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
    if (ctx.ERASE() != null) return Modifier.Erase;
    if (ctx.INLINE() != null) return Modifier.Inline;
    return unreachable(ctx);
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
