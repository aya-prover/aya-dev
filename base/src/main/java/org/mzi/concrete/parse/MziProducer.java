// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.parse;

import asia.kala.Tuple;
import asia.kala.Tuple2;
import asia.kala.collection.immutable.ImmutableList;
import asia.kala.collection.mutable.Buffer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Expr;
import org.mzi.concrete.Stmt;
import org.mzi.concrete.Stmt.CmdStmt.Cmd;
import org.mzi.generic.Assoc;
import org.mzi.generic.Modifier;
import org.mzi.generic.Tele;
import org.mzi.parser.MziBaseVisitor;
import org.mzi.parser.MziParser;
import org.mzi.ref.LocalVar;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ice1000
 */
public class MziProducer extends MziBaseVisitor<Object> {
  public enum UseHide {
    Use, Hide,
  }

  @Override
  public Stmt visitStmt(MziParser.StmtContext ctx) {
    var cmd = ctx.cmd();
    if (cmd != null) return visitCmd(cmd);
    var decl = ctx.decl();
    if (decl != null) return visitDecl(decl);
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public Decl visitDecl(MziParser.DeclContext ctx) {
    var fnDecl = ctx.fnDecl();
    if (fnDecl != null) return visitFnDecl(fnDecl);
    var dataDecl = ctx.dataDecl();
    if (dataDecl != null) return visitDataDecl(dataDecl);
    var structDecl = ctx.structDecl();
    if (structDecl != null) return visitStructDecl(structDecl);
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public Decl visitFnDecl(MziParser.FnDeclContext ctx) {
    var modifiers = ctx.fnModifiers().stream()
      .map(this::visitFnModifiers)
      .distinct()
      .collect(Collectors.toCollection(() -> EnumSet.noneOf(Modifier.class)));
    var assocCtx = ctx.assoc();
    var assoc = assocCtx == null ? null : visitAssoc(assocCtx);
    var tele = parseTeles(ctx.tele());
    var typeCtx = ctx.type();
    var type = typeCtx == null
      ? new Expr.HoleExpr(sourcePosOf(ctx), null, null) // TODO: is that correct to use HoleExpr?
      : visitType(typeCtx);
    var abuseCtx = ctx.abuse();
    Buffer<Stmt> abuse = abuseCtx == null ? Buffer.of() : visitAbuse(abuseCtx);

    return new Decl.FnDecl(
      sourcePosOf(ctx),
      modifiers,
      assoc,
      ctx.ID().getText(),
      tele,
      type,
      visitFnBody(ctx.fnBody()),
      abuse
    );
  }

  @Override
  public Buffer<Stmt> visitAbuse(MziParser.AbuseContext ctx) {
    return ctx.stmt().stream()
      .map(this::visitStmt)
      .collect(Buffer.factory());
  }

  @Override
  public Expr visitFnBody(MziParser.FnBodyContext ctx) {
    return visitExpr(ctx.expr());
  }

  public Tele<Expr> parseTeles(List<MziParser.TeleContext> teles) {
    Tele<Expr> next = null;
    Collections.reverse(teles);
    for (var ctx : teles) {
      next = parseTele(next, ctx);
    }
    return next;
  }

  public Tele<Expr> parseTele(Tele<Expr> next, MziParser.TeleContext ctx) {
    if (ctx instanceof MziParser.TeleLiteralContext teleLit) {
      var id = visitLiteralId(teleLit.literal());
      var var = new LocalVar(id);
      return new Tele.NamedTele<>(var, next);

    } else if (ctx instanceof MziParser.ExplicitContext ex) {
      return newTele(next, true, ex.teleTypedExpr());

    } else if (ctx instanceof MziParser.ImplicitContext im) {
      return newTele(next, false, im.teleTypedExpr());

    } else throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  public Tele<Expr> newTele(Tele<Expr> next, boolean explicit, MziParser.TeleTypedExprContext ctx) {
    if (ctx.type() == null) {
      // TODO: should report an error instead of throw
      throw new IllegalArgumentException("explicit/implicit tele should have type");
    }

    var type = visitType(ctx.type());

    // when parsing (a b : T), only `b` should be TypedTele
    // and `a` should be NamedTele,
    var ids = visitExprLiteral(ctx.expr());
    var last = ids.last();

    // build the last TypedTele
    var lastTyped = new Tele.TypedTele<>(new LocalVar(last), type, explicit, next);

    // others should be NamedTele
    return ids.view().reversed()
      .drop(1)
      .stream()
      .map(LocalVar::new)
      .<Tele<Expr>>reduce(
        lastTyped,
        (n, var) -> new Tele.NamedTele<>(var, n),
        (a, b) -> a
      );
  }

  public Expr visitExpr(MziParser.ExprContext ctx) {
    if (ctx instanceof MziParser.ProjContext proj) return new Expr.ProjExpr(
      sourcePosOf(proj),
      visitExpr(proj.expr()),
      Integer.parseInt(proj.NUMBER().getText()));
    return new Expr.HoleExpr(sourcePosOf(ctx), null, null);
  }

  @Override
  public Decl visitDataDecl(MziParser.DataDeclContext ctx) {
    // TODO: visit data decl
    throw new IllegalStateException("unimplemented");
  }

  @Override
  public Decl visitStructDecl(MziParser.StructDeclContext ctx) {
    // TODO: visit struct decl
    throw new IllegalStateException("unimplemented");
  }

  @Override
  public Expr visitType(MziParser.TypeContext ctx) {
    return visitExpr(ctx.expr());
  }

  @Override
  public Stmt visitCmd(MziParser.CmdContext ctx) {
    var cmd = ctx.OPEN() != null ? Cmd.Open : Cmd.Import;
    var using = Buffer.<String>of();
    var hiding = Buffer.<String>of();

    for (var useHind : ctx.useHide()) {
      var useOrHide = visitUseHide(useHind);
      var ref = switch (useOrHide.component1()) {
        case Use -> using;
        case Hide -> hiding;
      };
      ref.appendAll(useOrHide.component2());
    }

    return new Stmt.CmdStmt(
      cmd,
      visitModuleName(ctx.moduleName()),
      using.toImmutableList(),
      hiding.toImmutableList()
    );
  }

  @Override
  public Tuple2<UseHide, ImmutableList<String>> visitUseHide(MziParser.UseHideContext ctx) {
    var type = ctx.USING() != null ? UseHide.Use : UseHide.Hide;
    return Tuple.of(type, visitIds(ctx.ids()));
  }

  @Override
  public ImmutableList<String> visitIds(MziParser.IdsContext ctx) {
    return ctx.ID().stream()
      .map(t -> t.getSymbol().getText())
      .collect(ImmutableList.factory());
  }

  @Override
  public String visitModuleName(MziParser.ModuleNameContext ctx) {
    return ctx.ID().stream()
      .map(t -> t.getSymbol().getText())
      .collect(Collectors.joining("."));
  }

  @Override public @NotNull Assoc visitAssoc(MziParser.AssocContext ctx) {
    if (ctx.FIX() != null) return Assoc.Fix;
    else if (ctx.FIXL() != null) return Assoc.FixL;
    else if (ctx.FIXR() != null) return Assoc.FixR;
    else if (ctx.INFIX() != null) return Assoc.Infix;
    else if (ctx.INFIXL() != null) return Assoc.InfixL;
    else if (ctx.INFIXR() != null) return Assoc.InfixR;
    else if (ctx.TWIN() != null) return Assoc.Twin;
    else throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public Modifier visitFnModifiers(MziParser.FnModifiersContext ctx) {
    if (ctx.ERASE() != null) return Modifier.Erase;
    else if (ctx.INLINE() != null) return Modifier.Inline;
    else throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  private Buffer<String> visitExprLiteral(MziParser.ExprContext ctx) {
    if (ctx instanceof MziParser.AppContext app) {
      var list = Buffer.<String>of();
      list.append(visitLiteralId(app.atom().literal()));
      app.argument().forEach(a -> list.appendAll(visitExprLiteral(a.expr())));
      return list;
    }
    // TODO: should report an error instead of throw
    throw new IllegalArgumentException("not an literal expr");
  }

  private String visitLiteralId(MziParser.LiteralContext ctx) {
    var id = ctx.ID();
    if (id == null) {
      // TODO: should report an error instead of throw
      throw new IllegalArgumentException("not an literal id");
    }
    return id.getText();
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
}
