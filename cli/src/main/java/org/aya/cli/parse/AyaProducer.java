// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSinglyLinkedList;
import kala.control.Either;
import kala.control.Option;
import kala.function.BooleanFunction;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.value.Ref;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.error.BadCounterexampleWarn;
import org.aya.concrete.error.BadModifierWarn;
import org.aya.concrete.error.ParseError;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.generic.ref.GeneralizedVar;
import org.aya.generic.util.InternalException;
import org.aya.parser.AyaParser;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.aya.repl.antlr.AntlrUtil;
import org.aya.util.StringEscapeUtil;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
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
public record AyaProducer(
  @NotNull Either<SourceFile, SourcePos> source,
  @NotNull Reporter reporter
) {
  public AyaProducer(@NotNull Either<SourceFile, SourcePos> source, @NotNull Reporter reporter) {
    this.source = source;
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
    var id = ctx.weakId();
    var name = id.getText();
    var sourcePos = sourcePosOf(id);
    var type = ctx.type();
    return new Decl.PrimDecl(
      sourcePos,
      sourcePosOf(ctx),
      name,
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
      var stmts = result._2.view().prepended(result._1);
      if (result._1.personality == Decl.Personality.COUNTEREXAMPLE) {
        var stmtOption = result._2.firstOption(stmt -> !(stmt instanceof Decl));
        if (stmtOption.isDefined()) reporter.report(new BadCounterexampleWarn(stmtOption.get()));
        return stmts.<Stmt>filterIsInstance(Decl.class).toImmutableSeq();
      }
      return stmts;
    }
    var generalize = ctx.generalize();
    if (generalize != null) return ImmutableSeq.of(visitGeneralize(generalize));
    var remark = ctx.remark();
    if (remark != null) return ImmutableSeq.of(visitRemark(remark));
    return unreachable(ctx);
  }

  @NotNull private Remark visitRemark(AyaParser.RemarkContext remark) {
    var sb = new StringBuilder();
    for (var docComment : remark.DOC_COMMENT()) {
      sb.append(docComment.getText().substring(3)).append("\n");
    }
    return Remark.make(sb.toString(), sourcePosOf(remark), new AyaParserImpl(reporter));
  }

  public Generalize visitGeneralize(AyaParser.GeneralizeContext ctx) {
    return new Generalize(sourcePosOf(ctx), visitIds(ctx.ids())
      .map(id -> new GeneralizedVar(id.data(), id.sourcePos()))
      .collect(ImmutableSeq.factory()), visitType(ctx.type()));
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

  private <T> T unreachable(ParserRuleContext ctx) {
    throw new InternalException(ctx.getClass() + ": " + ctx.getText());
  }

  public @NotNull Tuple2<Decl, ImmutableSeq<Stmt>> visitDecl(AyaParser.DeclContext ctx) {
    var accessibility = ctx.PRIVATE() == null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var fnDecl = ctx.fnDecl();
    if (fnDecl != null) return Tuple.of(visitFnDecl(fnDecl, accessibility), ImmutableSeq.empty());
    var dataDecl = ctx.dataDecl();
    if (dataDecl != null) return visitDataDecl(dataDecl, accessibility);
    var structDecl = ctx.structDecl();
    if (structDecl != null) return visitStructDecl(structDecl, accessibility);
    var primDecl = ctx.primDecl();
    if (primDecl != null) return Tuple.of(visitPrimDecl(primDecl), ImmutableSeq.empty());
    return unreachable(ctx);
  }

  public Tuple2<@NotNull WithPos<String>, OpDecl.@Nullable OpInfo> visitDeclNameOrInfix(@NotNull AyaParser.DeclNameOrInfixContext ctx, int argc) {
    var assoc = ctx.assoc();
    var id = ctx.weakId();
    var txt = id.getText();
    var pos = sourcePosOf(id);
    if (assoc == null) return Tuple.of(new WithPos<>(pos, txt), null);
    var infix = new OpDecl.OpInfo(txt, visitAssoc(assoc), argc);
    return Tuple.of(new WithPos<>(pos, infix.name()), infix);
  }

  private @NotNull Assoc visitAssoc(@NotNull AyaParser.AssocContext assoc) {
    if (assoc.INFIX() != null) return Assoc.Infix;
    if (assoc.INFIXL() != null) return Assoc.InfixL;
    if (assoc.INFIXR() != null) return Assoc.InfixR;
    throw new InternalException("Unknown assoc: " + assoc.getText());
  }

  private int countExplicit(@NotNull ImmutableSeq<Expr.Param> tele) {
    return tele.count(Expr.Param::explicit);
  }

  public Decl.@NotNull FnDecl visitFnDecl(AyaParser.FnDeclContext ctx, Stmt.Accessibility accessibility) {
    var personality = visitSampleModifiers(ctx.sampleModifiers());
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
      personality == Decl.Personality.NORMAL ? accessibility : Stmt.Accessibility.Private,
      modifiers.map(Tuple2::getValue).collect(Collectors.toCollection(
        () -> EnumSet.noneOf(Modifier.class))),
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      type(ctx.type(), sourcePosOf(ctx)),
      dynamite,
      bind == null ? BindBlock.EMPTY : visitBind(bind),
      personality
    );
  }

  public @NotNull Decl.Personality visitSampleModifiers(AyaParser.SampleModifiersContext ctx) {
    if (ctx == null) return Decl.Personality.NORMAL;
    if (ctx.EXAMPLE() != null) return Decl.Personality.EXAMPLE;
    return Decl.Personality.COUNTEREXAMPLE;
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
      ctx.weakId().stream().map(ParseTree::getText)
        .collect(ImmutableSeq.factory()));
  }

  public @NotNull Expr visitLiteral(AyaParser.LiteralContext ctx) {
    var pos = sourcePosOf(ctx);
    if (ctx.CALM_FACE() != null) return new Expr.HoleExpr(pos, false, null);
    var id = ctx.qualifiedId();
    if (id != null) return new Expr.UnresolvedExpr(pos, visitQualifiedId(id));
    if (ctx.TYPE() != null) return new Expr.RawUnivExpr(pos);
    if (ctx.I() != null) return new Expr.IntervalExpr(pos);
    if (ctx.LGOAL() != null) {
      var fillingExpr = ctx.expr();
      var filling = fillingExpr == null ? null : visitExpr(fillingExpr);
      return new Expr.HoleExpr(pos, true, filling);
    }
    var number = ctx.NUMBER();
    if (number != null) try {
      return new Expr.LitIntExpr(pos, Integer.parseInt(number.getText()));
    } catch (NumberFormatException ignored) {
      reporter.report(new ParseError(sourcePosOf(number), "Unsupported integer literal `" + number.getText() + "`"));
      throw new ParsingInterruptedException();
    }
    var string = ctx.STRING();
    if (string != null) {
      var content = string.getText().substring(1, string.getText().length() - 1);
      return new Expr.LitStringExpr(pos, StringEscapeUtil.escapeStringCharacters(content));
    }
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
      case AyaParser.AppContext app -> {
        var head = new Expr.NamedArg(true, visitExpr(app.expr()));
        var tail = app.argument().stream()
          .map(this::visitArgument)
          .collect(MutableSinglyLinkedList.factory());
        tail.push(head);
        yield new Expr.BinOpSeq(sourcePosOf(app), tail.toImmutableSeq());
      }
      case AyaParser.ProjContext proj -> buildProj(sourcePosOf(proj), visitExpr(proj.expr()), proj.projFix());
      case AyaParser.PiContext pi -> buildPi(
        sourcePosOf(pi), false,
        visitTelescope(pi.tele()).view(),
        visitExpr(pi.expr()));
      case AyaParser.SigmaContext sig -> new Expr.SigmaExpr(
        sourcePosOf(sig), false,
        visitTelescope(sig.tele()).appended(new Expr.Param(
          visitExpr(sig.expr()).sourcePos(),
          Constants.anonymous(),
          visitExpr(sig.expr()), false, true)));
      case AyaParser.LamContext lam -> {
        Expr result;
        var bodyExpr = lam.expr();
        if (bodyExpr == null) {
          var impliesToken = lam.IMPLIES();
          var bodyHolePos = impliesToken == null ? sourcePosOf(lam) : sourcePosOf(impliesToken);
          result = new Expr.HoleExpr(bodyHolePos, false, null);
        } else result = visitExpr(bodyExpr);
        yield buildLam(sourcePosOf(lam), visitLamTelescope(lam.tele()).view(), result);
      }
      case AyaParser.ArrContext arr -> {
        var expr0 = arr.expr(0);
        var to = visitExpr(arr.expr(1));
        var pos = sourcePosOf(expr0);
        var param = new Expr.Param(pos, Constants.randomlyNamed(pos), visitExpr(expr0), false, true);
        yield new Expr.PiExpr(sourcePosOf(arr), false, param, to);
      }
      case AyaParser.NewContext n -> new Expr.NewExpr(
        sourcePosOf(n), visitExpr(n.expr()),
        Option.of(n.newBody()).map(b -> ImmutableSeq.from(b.newArg()).map(this::visitField))
          .getOrDefault(ImmutableSeq.empty()));
      case AyaParser.ForallContext forall -> buildPi(
        sourcePosOf(forall), false,
        visitForallTelescope(forall.tele()).view(),
        visitExpr(forall.expr()));
      case AyaParser.DoContext doCtx -> visitDo(doCtx);
      case AyaParser.IdiomContext idmCtx -> {
        if (idmCtx.idiomBlock().barredExpr() == null)
          yield new Expr.UnresolvedExpr(sourcePosOf(idmCtx), "empty");
        yield visitIdiomBlock(idmCtx.idiomBlock());
      }
      // TODO: match
      default -> throw new UnsupportedOperationException("TODO: " + ctx.getClass());
    };
  }

  /**
   * Warning: the parser cannot enforce left associativity at this stage
   */
  private @NotNull Expr visitIdiomBlock(AyaParser.IdiomBlockContext ctx) {
    var orArg = new Expr.NamedArg(true, new Expr.UnresolvedExpr(SourcePos.NONE, "<*>"));

    if (ctx.barredExpr().isEmpty()) {
      var apSeq = buildApSeq(ctx.expr());
      var pure = new Expr.UnresolvedExpr(apSeq.first().sourcePos(), "pure");
      var pureFirst = new Expr.NamedArg(true, new Expr.AppExpr(pure.sourcePos(), pure, apSeq.first()));
      return new Expr.BinOpSeq(sourcePosOf(ctx), apSeq.drop(1).prepended(pureFirst).toImmutableSeq());
    }

    var first = new Expr.NamedArg(true, visitExpr(ctx.barredExpr(0).expr(0)));
    var pure = new Expr.UnresolvedExpr(first.sourcePos(), "pure");
    var pureFirst = new Expr.NamedArg(true, new Expr.AppExpr(pure.sourcePos(), pure, first));
    var appSeq = ImmutableSeq.from(ctx.barredExpr()).view()
      .flatMap(barredExprCtx -> buildApSeq(barredExprCtx.expr()).prepended(orArg))
      .drop(1)
      .prepended(pureFirst)
      .toImmutableSeq();
    return new Expr.BinOpSeq(sourcePosOf(ctx), appSeq);
  }

  private @NotNull SeqView<Expr.NamedArg> buildApSeq(@NotNull List<AyaParser.ExprContext> exprs) {
    var ap = new Expr.NamedArg(true, new Expr.UnresolvedExpr(SourcePos.NONE, "<*>"));
    return Seq.from(exprs).view()
      .map(expr -> new Expr.NamedArg(true, visitExpr(expr)))
      .flatMap(arg -> ImmutableSeq.of(ap, arg))
      .drop(1);
  }

  private @NotNull Expr visitDo(AyaParser.DoContext ctx) {
    var doBlockExprCtxs = ctx.doBlock().doBlockExpr();
    var lastExprCtx = doBlockExprCtxs.get(doBlockExprCtxs.size() - 1);
    if (lastExprCtx.LARROW() != null) {
      reporter.report(new ParseError(sourcePosOf(lastExprCtx),
        "last expression in a do block cannot be a bind expression"));
      throw new ParsingInterruptedException();
    }

    var bindOp = new Expr.NamedArg(true, new Expr.UnresolvedExpr(SourcePos.NONE, ">>="));
    var lastExpr = visitExpr(lastExprCtx.expr());
    doBlockExprCtxs.remove(lastExprCtx);
    var doBlockExprCtxsSeq = ImmutableSeq.from(doBlockExprCtxs);
    return doBlockExprCtxsSeq.foldRight(lastExpr, (doCtx, accExpr) -> {
      var sourcePos = sourcePosOf(doCtx);
      Expr.Param param;

      if (doCtx.LARROW() != null)
        param = new Expr.Param(sourcePosOf(doCtx.weakId()), new LocalVar(doCtx.weakId().getText()), true);
      else
        param = Expr.Param.ignoredParam(SourcePos.NONE);

      var rhs = new Expr.NamedArg(true, new Expr.LamExpr(sourcePos, param, accExpr));
      var lhs = new Expr.NamedArg(true, visitExpr(doCtx.expr()));
      var seq = ImmutableSeq.of(lhs, bindOp, rhs);
      return new Expr.BinOpSeq(sourcePos, seq);
    });
  }

  private @NotNull Expr.Field visitField(AyaParser.NewArgContext na) {
    var weakId = na.weakId();
    return new Expr.Field(new WithPos<>(sourcePosOf(weakId), weakId.getText()), visitIds(na.ids())
      .map(t -> new WithPos<>(t.sourcePos(), LocalVar.from(t)))
      .collect(ImmutableSeq.factory()), visitExpr(na.expr()), new Ref<>());
  }

  public @NotNull Expr visitAtom(AyaParser.AtomContext ctx) {
    var literal = ctx.literal();
    if (literal != null) {
      var expr = visitLiteral(literal);
      var lifts = ctx.ULIFT().size();
      return lifts > 0 ? new Expr.LiftExpr(sourcePosOf(ctx), expr, lifts) : expr;
    }

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
    var id = ctx.weakId();
    if (id != null) return new Expr.NamedArg(false, id.getText(), visitExpr(ctx.expr()));
    var items = ImmutableSeq.from(ctx.exprList().expr()).map(this::visitExpr);
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

  public static @NotNull Expr buildLam(SourcePos sourcePos, SeqView<Expr.Param> params, Expr body) {
    if (params.isEmpty()) return body;
    var drop = params.drop(1);
    return new Expr.LamExpr(
      sourcePos, params.first(),
      buildLam(AntlrUtil.sourcePosForSubExpr(sourcePos.file(),
        drop.map(Expr.Param::sourcePos), body.sourcePos()), drop, body));
  }

  public static @NotNull Expr buildPi(
    SourcePos sourcePos, boolean co,
    SeqView<Expr.Param> params, Expr body
  ) {
    if (params.isEmpty()) return body;
    var drop = params.drop(1);
    return new Expr.PiExpr(
      sourcePos, co, params.first(),
      buildPi(AntlrUtil.sourcePosForSubExpr(sourcePos.file(),
        drop.map(Expr.Param::sourcePos), body.sourcePos()), co, drop, body));
  }

  private Expr.@NotNull ProjExpr buildProj(
    @NotNull SourcePos sourcePos, @NotNull Expr projectee,
    @NotNull AyaParser.ProjFixContext fix
  ) {
    var number = fix.NUMBER();
    return new Expr.ProjExpr(
      sourcePos,
      projectee,
      number != null
        ? Either.left(Integer.parseInt(number.getText()))
        : Either.right(visitQualifiedId(fix.qualifiedId()))
    );
  }

  public @NotNull Tuple2<Decl, ImmutableSeq<Stmt>>
  visitDataDecl(AyaParser.DataDeclContext ctx, Stmt.Accessibility accessibility) {
    var personality = visitSampleModifiers(ctx.sampleModifiers());
    var bind = ctx.bindBlock();
    var openAccessibility = ctx.PUBLIC() != null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var body = ctx.dataBody().stream().map(this::visitDataBody).collect(ImmutableSeq.factory());
    var tele = visitTelescope(ctx.tele());
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix(), countExplicit(tele));
    var data = new Decl.DataDecl(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(ctx),
      personality == Decl.Personality.NORMAL ? accessibility : Stmt.Accessibility.Private,
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      type(ctx.type(), sourcePosOf(ctx)),
      body,
      bind == null ? BindBlock.EMPTY : visitBind(bind),
      personality
    );
    return Tuple2.of(data, ctx.OPEN() == null ? ImmutableSeq.empty() : ImmutableSeq.of(
      new Command.Open(
        nameOrInfix._1.sourcePos(),
        openAccessibility,
        new QualifiedID(sourcePosOf(ctx), nameOrInfix._1.data()),
        Command.Open.UseHide.EMPTY,
        personality == Decl.Personality.EXAMPLE
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
      var patterns = ctx.patterns();
      if (patterns == null) return ex -> new Pattern.Absurd(sourcePos, ex);
      var id = ctx.weakId();
      var as = id != null ? new LocalVar(id.getText(), sourcePosOf(id)) : null;
      var tupElem = Seq.from(patterns.pattern()).view()
        .map(t -> visitAtomPatterns(t.atomPatterns()))
        .toImmutableSeq();
      return tupElem.sizeEquals(1)
        ? (ignored -> newBinOPScope(tupElem.first().apply(forceEx, as), as))
        : (ignored -> new Pattern.Tuple(sourcePos, forceEx, tupElem.map(p -> p.apply(true, null)), as));
    }
    if (ctx.CALM_FACE() != null) return ex -> new Pattern.CalmFace(sourcePos, ex);
    var number = ctx.NUMBER();
    if (number != null) return ex -> new Pattern.Number(sourcePos, ex, Integer.parseInt(number.getText()));
    var id = ctx.weakId();
    if (id != null) return ex -> new Pattern.Bind(sourcePos, ex, new LocalVar(id.getText(), sourcePosOf(id)));

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

  public @NotNull Tuple2<Decl, ImmutableSeq<Stmt>> visitStructDecl(AyaParser.StructDeclContext ctx, Stmt.Accessibility accessibility) {
    var personality = visitSampleModifiers(ctx.sampleModifiers());
    var bind = ctx.bindBlock();
    var openAccessibility = ctx.PUBLIC() != null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var fields = visitFields(ctx.field());
    var tele = visitTelescope(ctx.tele());
    var nameOrInfix = visitDeclNameOrInfix(ctx.declNameOrInfix(), countExplicit(tele));
    var struct = new Decl.StructDecl(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(ctx),
      personality == Decl.Personality.NORMAL ? accessibility : Stmt.Accessibility.Private,
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      type(ctx.type(), sourcePosOf(ctx)),
      fields,
      bind == null ? BindBlock.EMPTY : visitBind(bind),
      personality
    );
    return Tuple2.of(struct, ctx.OPEN() == null ? ImmutableSeq.empty() : ImmutableSeq.of(
      new Command.Open(
        nameOrInfix._1.sourcePos(),
        openAccessibility,
        new QualifiedID(sourcePosOf(ctx), nameOrInfix._1.data()),
        Command.Open.UseHide.EMPTY,
        personality == Decl.Personality.EXAMPLE
      )
    ));
  }

  private ImmutableSeq<Decl.StructField> visitFields(List<AyaParser.FieldContext> field) {
    return ImmutableSeq.from(field).map(fieldCtx -> {
      if (fieldCtx instanceof AyaParser.FieldDeclContext fieldDecl) return visitFieldDecl(fieldDecl);
      else if (fieldCtx instanceof AyaParser.FieldImplContext fieldImpl) return visitFieldImpl(fieldImpl);
      else throw new InternalException(fieldCtx.getClass() + " is neither FieldDecl nor FieldImpl!");
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
    final var id = ctx.weakId();
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
      useHide != null ? visitUseHide(useHide) : Command.Open.UseHide.EMPTY,
      false
    );
    if (ctx.IMPORT() != null) return ImmutableSeq.of(
      new Command.Import(namePos, modName, null),
      open
    );
    else return ImmutableSeq.of(open);
  }

  public Command.Open.UseHide hideList(List<AyaParser.HideListContext> ctxs, Command.Open.UseHide.Strategy strategy) {
    return new Command.Open.UseHide(
      ctxs.stream()
        .map(AyaParser.HideListContext::idsComma)
        .flatMap(this::visitIdsComma)
        .map(id -> new Command.Open.UseHideName(id, id, Assoc.Invalid, BindBlock.EMPTY))
        .collect(ImmutableSeq.factory()),
      strategy);
  }

  public Command.Open.UseHide useList(List<AyaParser.UseListContext> ctxs, Command.Open.UseHide.Strategy strategy) {
    return new Command.Open.UseHide(ctxs.stream()
      .map(AyaParser.UseListContext::useIdsComma)
      .flatMap(this::visitUseIdsComma)
      .collect(ImmutableSeq.factory()),
      strategy);
  }

  public Stream<Command.Open.UseHideName> visitUseIdsComma(@NotNull AyaParser.UseIdsCommaContext ctx) {
    return ctx.useId().stream().map(id -> {
      var name = id.weakId().getText();
      var useAs = id.useAs();
      if (useAs == null) return new Command.Open.UseHideName(name, name, Assoc.Invalid, BindBlock.EMPTY);
      var asId = useAs.weakId().getText();
      var asAssoc = useAs.assoc();
      var asBind = useAs.bindBlock();
      return new Command.Open.UseHideName(name, asId,
        asAssoc != null ? visitAssoc(asAssoc) : Assoc.Invalid,
        asBind != null ? visitBind(asBind) : BindBlock.EMPTY);
    });
  }

  public @NotNull Command.Open.UseHide visitUseHide(@NotNull AyaParser.UseHideContext ctx) {
    if (ctx.HIDING() != null) return hideList(ctx.hideList(), Command.Open.UseHide.Strategy.Hiding);
    return useList(ctx.useList(), Command.Open.UseHide.Strategy.Using);
  }

  public @NotNull Command.Module visitModule(AyaParser.ModuleContext ctx) {
    var id = ctx.weakId();
    return new Command.Module(
      sourcePosOf(id), id.getText(),
      ImmutableSeq.from(ctx.stmt()).flatMap(this::visitStmt)
    );
  }

  public @NotNull Stream<WithPos<String>> visitIds(AyaParser.IdsContext ctx) {
    return ctx.weakId().stream().map(id -> new WithPos<>(sourcePosOf(id), id.getText()));
  }

  public @NotNull Stream<String> visitIdsComma(AyaParser.IdsCommaContext ctx) {
    return ctx.weakId().stream().map(ParseTree::getText);
  }

  public @NotNull Modifier visitFnModifiers(AyaParser.FnModifiersContext ctx) {
    if (ctx.OPAQUE() != null) return Modifier.Opaque;
    if (ctx.INLINE() != null) return Modifier.Inline;
    if (ctx.OVERLAP() != null) return Modifier.Overlap;
    /*if (ctx.PATTERN_KW() != null)*/
    return Modifier.Pattern;
  }

  private @NotNull SourcePos sourcePosOf(ParserRuleContext ctx) {
    return source.fold(sourceFile -> AntlrUtil.sourcePosOf(ctx, sourceFile), pos -> pos);
  }

  private @NotNull SourcePos sourcePosOf(TerminalNode node) {
    return source.fold(sourceFile -> AntlrUtil.sourcePosOf(node, sourceFile), pos -> pos);
  }
}
