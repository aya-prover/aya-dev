// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.parse;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.glavo.kala.Tuple;
import org.glavo.kala.Tuple2;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.immutable.ImmutableVector;
import org.glavo.kala.collection.mutable.Buffer;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.SourcePos;
import org.mzi.api.util.Assoc;
import org.mzi.concrete.*;
import org.mzi.concrete.Stmt.CmdStmt.Cmd;
import org.mzi.generic.Arg;
import org.mzi.generic.Modifier;
import org.mzi.parser.MziBaseVisitor;
import org.mzi.parser.MziParser;
import org.mzi.ref.LocalVar;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author ice1000, kiva
 */
public final class MziProducer extends MziBaseVisitor<Object> {
  public static final @NotNull MziProducer INSTANCE = new MziProducer();

  private MziProducer() {
  }

  public static @NotNull Expr parseExpr(@NotNull @NonNls @Language("TEXT") String code) {
    return INSTANCE.visitExpr(MziParsing.parser(code).expr());
  }

  public static @NotNull Stmt parseStmt(@NotNull @NonNls @Language("TEXT") String code) {
    return INSTANCE.visitStmt(MziParsing.parser(code).stmt());
  }

  public static @NotNull Decl parseDecl(@NotNull @NonNls @Language("TEXT") String code) {
    return INSTANCE.visitDecl(MziParsing.parser(code).decl());
  }

  @Override
  public @NotNull Stmt visitStmt(MziParser.StmtContext ctx) {
    var cmd = ctx.cmd();
    if (cmd != null) return visitCmd(cmd);
    var decl = ctx.decl();
    if (decl != null) return visitDecl(decl);
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public @NotNull Decl visitDecl(MziParser.DeclContext ctx) {
    var accessibility = ctx.PRIVATE() == null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var fnDecl = ctx.fnDecl();
    if (fnDecl != null) return visitFnDecl(fnDecl, accessibility);
    var dataDecl = ctx.dataDecl();
    if (dataDecl != null) return visitDataDecl(dataDecl, accessibility);
    var structDecl = ctx.structDecl();
    if (structDecl != null) return visitStructDecl(structDecl, accessibility);
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  public Decl.@NotNull FnDecl visitFnDecl(MziParser.FnDeclContext ctx, Stmt.Accessibility accessibility) {
    var modifiers = ctx.fnModifiers().stream()
      .map(this::visitFnModifiers)
      .distinct()
      .collect(Collectors.toCollection(() -> EnumSet.noneOf(Modifier.class)));
    var assocCtx = ctx.assoc();
    var abuseCtx = ctx.abuse();

    return new Decl.FnDecl(
      sourcePosOf(ctx),
      accessibility,
      modifiers,
      assocCtx == null ? null : visitAssoc(assocCtx),
      ctx.ID().getText(),
      visitTelescope(ctx.tele().stream()),
      type(ctx.type(), sourcePosOf(ctx)),
      visitFnBody(ctx.fnBody()),
      abuseCtx == null ? Buffer.of() : visitAbuse(abuseCtx)
    );
  }

  public @NotNull Buffer<@NotNull Param> visitTelescope(Stream<MziParser.TeleContext> stream) {
    return stream
      .collect(ImmutableSeq.factory())
      .flatMap(this::visitTele)
      .collect(Buffer.factory());
  }

  @Override
  public @NotNull Buffer<@NotNull Stmt> visitAbuse(MziParser.AbuseContext ctx) {
    return ctx.stmt().stream()
      .map(this::visitStmt)
      .collect(Buffer.factory());
  }

  @Override
  public @NotNull Expr visitFnBody(MziParser.FnBodyContext ctx) {
    return visitExpr(ctx.expr());
  }

  @Override
  public @NotNull Expr visitLiteral(MziParser.LiteralContext ctx) {
    if (ctx.CALM_FACE() != null) return new Expr.HoleExpr(sourcePosOf(ctx), "_", null);
    var id = ctx.ID();
    if (id != null) return new Expr.UnresolvedExpr(sourcePosOf(ctx), id.getText());
    var universe = ctx.UNIVERSE();
    if (universe != null) {
      String universeText = universe.getText();
      var univTrunc = universeText.substring(1, universeText.indexOf("T"));
      var hLevel = switch (univTrunc) {
        default -> Integer.parseInt(univTrunc.substring(0, univTrunc.length() - 1));
        case "h-", "h" -> -3;
        case "oo-" -> Integer.MAX_VALUE;
      };
      var uLevel = visitOptNumber(universeText.substring(universeText.indexOf("e") + 1), -3);
      return new Expr.UnivExpr(sourcePosOf(ctx), uLevel, hLevel);
    }
    var set = ctx.SET_UNIV();
    if (set != null) {
      var text = set.getText().substring("\\Set".length());
      return new Expr.UnivExpr(sourcePosOf(ctx), visitOptNumber(text, -3), -3);
    }
    var prop = ctx.PROP();
    if (prop != null) return new Expr.UnivExpr(sourcePosOf(ctx), 0, -1);
    if (ctx.LGOAL() != null) {
      var fillingExpr = ctx.expr();
      var filling = fillingExpr == null ? null : visitExpr(fillingExpr);
      return new Expr.HoleExpr(sourcePosOf(ctx), null, filling);
    }
    var number = ctx.NUMBER();
    if (number != null) return new Expr.LitIntExpr(sourcePosOf(ctx), Integer.parseInt(number.getText()));
    var string = ctx.STRING();
    if (string != null) return new Expr.LitStringExpr(sourcePosOf(ctx), string.getText());
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  public int visitOptNumber(@NotNull String number, int defaultVal) {
    return Optional.of(number)
      .filter(Predicate.not(String::isEmpty))
      .map(Integer::parseInt)
      .orElse(defaultVal);
  }

  @Override
  public @NotNull Buffer<@NotNull Param> visitTele(MziParser.TeleContext ctx) {
    var literal = ctx.literal();
    if (literal != null) return Buffer.of(new Param(sourcePosOf(ctx), new LocalVar("_"), visitLiteral(literal), true));
    var teleTypedExpr = ctx.teleTypedExpr();
    if (ctx.LPAREN() != null) return visitTeleTypedExpr(teleTypedExpr).apply(true);
    assert ctx.LBRACE() != null;
    return visitTeleTypedExpr(teleTypedExpr).apply(false);
  }

  @Override
  public @NotNull Function<Boolean, Buffer<Param>> visitTeleTypedExpr(MziParser.TeleTypedExprContext ctx) {
    var type = visitType(ctx.type());
    return explicit -> visitIds(ctx.ids())
      .map(var -> new Param(sourcePosOf(ctx), new LocalVar(var), type, explicit))
      .collect(Buffer.factory());
  }

  public @NotNull Expr visitExpr(MziParser.ExprContext ctx) {
    if (ctx instanceof MziParser.AppContext app) return visitApp(app);
    if (ctx instanceof MziParser.ProjContext proj) return visitProj(proj);
    if (ctx instanceof MziParser.PiContext pi) return visitPi(pi);
    if (ctx instanceof MziParser.SigmaContext sig) return visitSigma(sig);
    if (ctx instanceof MziParser.LamContext lam) return visitLam(lam);
    // TODO: match and arr
    return new Expr.HoleExpr(sourcePosOf(ctx), null, null);
  }

  @Override
  public @NotNull Expr visitApp(MziParser.AppContext ctx) {
    var argument = ctx.argument();
    if (argument.isEmpty()) return visitAtom(ctx.atom());
    return new Expr.AppExpr(
      sourcePosOf(ctx),
      visitAtom(ctx.atom()),
      argument.stream()
        .flatMap(a -> this.visitArgument(a).stream())
        .collect(ImmutableSeq.factory())
    );
  }

  @Override
  public @NotNull Expr visitAtom(MziParser.AtomContext ctx) {
    var literal = ctx.literal();
    if (literal != null) return visitLiteral(literal);

    return new Expr.TupExpr(
      sourcePosOf(ctx),
      ctx.typed().stream()
        .<Expr>map(this::visitTyped)
        .collect(ImmutableVector.factory())
    );
  }

  public @NotNull Buffer<Arg<Expr>> visitArgumentAtom(MziParser.AtomContext ctx) {
    var literal = ctx.literal();
    if (literal != null) return Buffer.of(Arg.explicit(visitLiteral(literal)));
    return ctx.typed().stream()
      .<Expr>map(this::visitTyped)
      .map(Arg::explicit)
      .collect(Buffer.factory());
  }

  @Override
  public Expr.@NotNull TypedExpr visitTyped(MziParser.TypedContext ctx) {
    return new Expr.TypedExpr(
      sourcePosOf(ctx),
      visitExpr(ctx.expr()),
      type(ctx.type(), sourcePosOf(ctx))
    );
  }

  @Override
  public @NotNull Buffer<Arg<Expr>> visitArgument(MziParser.ArgumentContext ctx) {
    var atom = ctx.atom();
    if (atom != null) return visitArgumentAtom(atom);
    if (ctx.LBRACE() != null) {
      return ctx.typed().stream()
        .<Expr>map(this::visitTyped)
        .map(Arg::implicit)
        .collect(Buffer.factory());
    }
    // TODO: . idFix
    throw new UnsupportedOperationException();
  }

  @Override
  public Expr.@NotNull TelescopicLamExpr visitLam(MziParser.LamContext ctx) {
    return new Expr.TelescopicLamExpr(
      sourcePosOf(ctx),
      visitTelescope(ctx.tele().stream()),
      visitLamBody(ctx)
    );
  }

  private @NotNull Expr visitLamBody(@NotNull MziParser.LamContext ctx) {
    var bodyExpr = ctx.expr();

    if (bodyExpr == null) {
      var impliesToken = ctx.IMPLIES();
      var bodyHolePos = impliesToken == null
        ? sourcePosOf(ctx)
        : sourcePosOf(impliesToken);

      return new Expr.HoleExpr(bodyHolePos, null, null);
    }

    return visitExpr(bodyExpr);
  }

  @Override
  public Expr.@NotNull TelescopicSigmaExpr visitSigma(MziParser.SigmaContext ctx) {
    return new Expr.TelescopicSigmaExpr(
      sourcePosOf(ctx),
      false,
      visitTelescope(ctx.tele().stream()),
      visitExpr(ctx.expr())
    );
  }

  @Override
  public Expr.@NotNull TelescopicPiExpr visitPi(MziParser.PiContext ctx) {
    return new Expr.TelescopicPiExpr(
      sourcePosOf(ctx),
      false,
      visitTelescope(ctx.tele().stream()),
      visitExpr(ctx.expr())
    );
  }

  @Override
  public Expr.@NotNull ProjExpr visitProj(MziParser.ProjContext proj) {
    return new Expr.ProjExpr(
      sourcePosOf(proj),
      visitExpr(proj.expr()),
      Integer.parseInt(proj.NUMBER().getText())
    );
  }

  public Decl.@NotNull DataDecl visitDataDecl(MziParser.DataDeclContext ctx, Stmt.Accessibility accessibility) {
    var abuseCtx = ctx.abuse();

    return new Decl.DataDecl(
      sourcePosOf(ctx),
      accessibility,
      ctx.ID().getText(),
      visitTelescope(ctx.tele().stream()),
      type(ctx.type(), sourcePosOf(ctx)),
      visitDataBody(ctx.dataBody()),
      abuseCtx == null ? Buffer.of() : visitAbuse(abuseCtx)
    );
  }

  public @NotNull Expr type(@Nullable MziParser.TypeContext typeCtx, SourcePos sourcePos) {
    return typeCtx == null
      ? new Expr.HoleExpr(sourcePos, null, null)
      : visitType(typeCtx);
  }

  private @NotNull Decl.DataBody visitDataBody(MziParser.DataBodyContext ctx) {
    if (ctx instanceof MziParser.DataCtorsContext dcc) return visitDataCtors(dcc);
    if (ctx instanceof MziParser.DataClausesContext dcc) return visitDataClauses(dcc);

    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public Decl.DataBody visitDataCtors(MziParser.DataCtorsContext ctx) {
    return new Decl.DataBody.Ctors(
      ctx.dataCtor().stream()
        .map(this::visitDataCtor)
        .collect(Buffer.factory())
    );
  }

  @Override
  public Decl.DataBody visitDataClauses(MziParser.DataClausesContext ctx) {
    var elim = visitElim(ctx.elim());
    // TODO[imkiva]: use var will compile, but IDEA shows error
    Buffer<Tuple2<Pattern, Decl.DataCtor>> clauses = ctx.dataCtorClause().stream()
      .map(this::visitDataCtorClause)
      .collect(Buffer.factory());
    return new Decl.DataBody.Clauses(elim, clauses);
  }

  @Override
  public Decl.@NotNull DataCtor visitDataCtor(MziParser.DataCtorContext ctx) {
    var elimCtx = ctx.elim();
    var elim = elimCtx == null
      ? Buffer.<String>of()
      : visitElim(elimCtx);

    return new Decl.DataCtor(
      ctx.ID().getText(),
      visitTelescope(ctx.tele().stream()),
      elim,
      ctx.clause().stream()
        .map(this::visitClause)
        .collect(Buffer.factory()),
      ctx.COERCE() != null
    );
  }

  @Override
  public @NotNull Tuple2<@NotNull Pattern, Decl.@NotNull DataCtor> visitDataCtorClause(MziParser.DataCtorClauseContext ctx) {
    return Tuple.of(
      visitPattern(ctx.pattern()),
      visitDataCtor(ctx.dataCtor())
    );
  }

  private @NotNull Pattern visitPattern(MziParser.PatternContext ctx) {
    if (ctx instanceof MziParser.PatAtomContext pa) return visitPatAtom(pa);
    if (ctx instanceof MziParser.PatCtorContext pc) return visitPatCtor(pc);

    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public Pattern.@NotNull PatAtom visitPatAtom(MziParser.PatAtomContext ctx) {
    if (ctx.AS() == null) {
      return new Pattern.PatAtom(visitAtomPattern(ctx.atomPattern()), null);
    }

    var asIdCtx = ctx.ID();

    return new Pattern.PatAtom(
      visitAtomPattern(ctx.atomPattern()),
      Tuple.of(asIdCtx.getText(), type(ctx.type(), sourcePosOf(asIdCtx)))
    );
  }

  @Override
  public Pattern.@NotNull PatCtor visitPatCtor(MziParser.PatCtorContext ctx) {
    return new Pattern.PatCtor(
      ctx.ID(0).getText(),
      ctx.patternCtorParam().stream()
        .map(this::visitPatternCtorParam)
        .collect(Buffer.factory()),
      ctx.AS() == null ? null : ctx.ID(1).getText(),
      type(ctx.type(), sourcePosOf(ctx))
    );
  }

  @Override
  public Pattern.@NotNull Atom visitPatternCtorParam(MziParser.PatternCtorParamContext ctx) {
    var id = ctx.ID();
    if (id != null) return new Pattern.Ident(id.getText());
    var atomPattern = ctx.atomPattern();
    if (atomPattern != null) return visitAtomPattern(atomPattern);

    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public Pattern.@NotNull Atom visitAtomPattern(MziParser.AtomPatternContext ctx) {
    if (ctx.LPAREN() != null) return new Pattern.Tuple(visitPatterns(ctx.patterns()));
    if (ctx.LBRACE() != null) return new Pattern.Braced(visitPatterns(ctx.patterns()));
    if (ctx.CALM_FACE() != null) return new Pattern.CalmFace();
    var number = ctx.NUMBER();
    if (number != null) return new Pattern.Number(Integer.parseInt(number.getText()));

    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public @NotNull Buffer<@NotNull Pattern> visitPatterns(MziParser.PatternsContext ctx) {
    return ctx.pattern().stream()
      .map(this::visitPattern)
      .collect(Buffer.factory());
  }

  @Override
  public @NotNull Clause visitClause(MziParser.ClauseContext ctx) {
    if (ctx.ABSURD() != null) return Clause.Impossible.INSTANCE;
    return new Clause.Possible(
      visitPatterns(ctx.patterns()),
      visitExpr(ctx.expr())
    );
  }

  @Override
  public Buffer<String> visitElim(MziParser.ElimContext ctx) {
    return ctx.ID().stream()
      .map(ParseTree::getText)
      .collect(Buffer.factory());
  }

  public @NotNull Decl visitStructDecl(MziParser.StructDeclContext ctx, Stmt.Accessibility accessibility) {
    // TODO: visit struct decl
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull Expr visitType(@NotNull MziParser.TypeContext ctx) {
    return visitExpr(ctx.expr());
  }

  @Override
  public @NotNull Stmt visitCmd(MziParser.CmdContext ctx) {
    var modifier = ctx.cmdModifier();
    var accessibility = (modifier == null || modifier.PUBLIC() == null)
      ? Stmt.Accessibility.Private
      : Stmt.Accessibility.Public;
    var useHide = ctx.useHide();
    return new Stmt.CmdStmt(
      sourcePosOf(ctx),
      accessibility,
      (modifier != null && modifier.OPEN() != null) ? Cmd.Open : Cmd.Import,
      visitModuleName(ctx.moduleName()),
      useHide != null ? visitUseHide(useHide) : Stmt.CmdStmt.UseHide.EMPTY
    );
  }

  public Stmt.CmdStmt.UseHide visitUse(List<MziParser.UseContext> ctxs) {
    return new Stmt.CmdStmt.UseHide(
      ctxs.stream()
        .flatMap(ctx -> visitIds(ctx.useHideList().ids()))
        .collect(ImmutableSeq.factory()),
      Stmt.CmdStmt.UseHide.Strategy.Using);
  }

  public Stmt.CmdStmt.UseHide visitHide(List<MziParser.HideContext> ctxs) {
    return new Stmt.CmdStmt.UseHide(
      ctxs.stream()
        .flatMap(ctx -> visitIds(ctx.useHideList().ids()))
        .collect(ImmutableSeq.factory()),
      Stmt.CmdStmt.UseHide.Strategy.Hiding);
  }

  @Override
  public @NotNull Stmt.CmdStmt.UseHide visitUseHide(@NotNull MziParser.UseHideContext ctx) {
    var use = ctx.use();
    if (use != null) return visitUse(use);
    return visitHide(ctx.hide());
  }

  @Override
  public @NotNull Stmt.ModuleStmt visitModule(MziParser.ModuleContext ctx) {
    return new Stmt.ModuleStmt(
      sourcePosOf(ctx),
      ctx.ID().getText(),
      ImmutableSeq.from(ctx.program().stmt()).map(this::visitStmt)
    );
  }

  @Override
  public @NotNull Stream<String> visitIds(MziParser.IdsContext ctx) {
    return ctx.ID().stream().map(ParseTree::getText);
  }

  @Override
  public @NotNull ImmutableSeq<@NotNull String> visitModuleName(MziParser.ModuleNameContext ctx) {
    return ctx.ID().stream()
      .map(ParseTree::getText)
      .collect(ImmutableSeq.factory());
  }

  @Override
  public @NotNull Assoc visitAssoc(MziParser.AssocContext ctx) {
    if (ctx.FIX() != null) return Assoc.Fix;
    if (ctx.FIXL() != null) return Assoc.FixL;
    if (ctx.FIXR() != null) return Assoc.FixR;
    if (ctx.INFIX() != null) return Assoc.Infix;
    if (ctx.INFIXL() != null) return Assoc.InfixL;
    if (ctx.INFIXR() != null) return Assoc.InfixR;
    if (ctx.TWIN() != null) return Assoc.Twin;
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public @NotNull Modifier visitFnModifiers(MziParser.FnModifiersContext ctx) {
    if (ctx.ERASE() != null) return Modifier.Erase;
    if (ctx.INLINE() != null) return Modifier.Inline;
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  private @NotNull SourcePos sourcePosOf(ParserRuleContext ctx) {
    var interval = ctx.getSourceInterval();
    var start = ctx.getStart();
    var end = ctx.getStop();
    return new SourcePos(
      interval.a,
      interval.b,
      start.getLine(),
      start.getCharPositionInLine(),
      end.getLine(),
      end.getCharPositionInLine()
    );
  }

  private @NotNull SourcePos sourcePosOf(TerminalNode node) {
    var interval = node.getSourceInterval();
    var token = node.getSymbol();
    var line = token.getLine();
    return new SourcePos(
      interval.a,
      interval.b,
      line,
      token.getCharPositionInLine(),
      line,
      token.getCharPositionInLine() + token.getText().length()
    );
  }
}
