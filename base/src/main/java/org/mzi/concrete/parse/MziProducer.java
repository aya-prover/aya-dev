// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.parse;

import asia.kala.Tuple;
import asia.kala.Tuple2;
import asia.kala.collection.immutable.ImmutableList;
import asia.kala.collection.mutable.ArrayBuffer;
import asia.kala.collection.mutable.Buffer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Expr;
import org.mzi.concrete.Stmt;
import org.mzi.generic.Assoc;
import org.mzi.generic.Cmd;
import org.mzi.generic.Modifier;
import org.mzi.generic.Tele;
import org.mzi.parser.MziBaseVisitor;
import org.mzi.parser.MziParser;
import org.mzi.ref.LocalVar;

import java.util.ArrayList;
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
    if (ctx.cmd() != null) {
      return visitCmd(ctx.cmd());
    } else if (ctx.decl() != null) {
      return new Stmt.DeclStmt(visitDecl(ctx.decl()));
    } else throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public Decl visitDecl(MziParser.DeclContext ctx) {
    if (ctx.fnDecl() != null) {
      return visitFnDecl(ctx.fnDecl());
    } else if (ctx.dataDecl() != null) {
      return visitDataDecl(ctx.dataDecl());
    } else if (ctx.structDecl() != null) {
      return visitStructDecl(ctx.structDecl());
    } else throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public Decl visitFnDecl(MziParser.FnDeclContext ctx) {
    var modifiers = ctx.fnModifiers().stream()
      .map(this::visitFnModifiers)
      .distinct()
      .collect(Collectors.toCollection(() -> EnumSet.noneOf(Modifier.class)));
    var assoc = ctx.assoc() == null ? null : visitAssoc(ctx.assoc());
    var tele = parseTeles(ctx.tele());
    var type = ctx.type() == null
      ? new Expr.HoleExpr(sourcePosOf(ctx), null, null) // TODO: is that correct to use HoleExpr?
      : visitType(ctx.type());
    Buffer<Stmt> abuse = ctx.abuse() == null ? Buffer.of() : visitAbuse(ctx.abuse());

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
    var last = ids.get(ids.size() - 1);
    ids.remove(ids.size() - 1);
    Collections.reverse(ids);

    // build the last TypedTele
    var lastTyped = new Tele.TypedTele<>(new LocalVar(last), type, explicit, next);

    // others should be NamedTele
    return ids.stream()
      .map(LocalVar::new)
      .reduce(
        (Tele<Expr>) lastTyped,
        (n, var) -> new Tele.NamedTele<>(var, n),
        (a, b) -> a
      );
  }

  public Expr visitExpr(MziParser.ExprContext ctx) {
    // TODO: visit expr
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
    var using = new ArrayBuffer<String>();
    var hiding = new ArrayBuffer<String>();

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

  private List<String> visitExprLiteral(MziParser.ExprContext ctx) {
    if (ctx instanceof MziParser.AppContext app) {
      var list = new ArrayList<String>();
      list.add(visitLiteralId(app.atom().literal()));
      app.argument().forEach(a -> list.addAll(visitExprLiteral(a.expr())));
      return list;
    }
    // TODO: should report an error instead of throw
    throw new IllegalArgumentException("not an literal expr");
  }

  private String visitLiteralId(MziParser.LiteralContext ctx) {
    if (ctx.ID() == null) {
      // TODO: should report an error instead of throw
      throw new IllegalArgumentException("not an literal id");
    }
    return ctx.ID().getText();
  }

  private SourcePos sourcePosOf(ParserRuleContext ctx) {
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
