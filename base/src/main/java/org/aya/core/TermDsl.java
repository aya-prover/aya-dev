// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.concrete.Decl;
import org.aya.core.def.FnDef;
import org.aya.core.term.*;
import org.aya.parser.LispBaseVisitor;
import org.aya.parser.LispParser;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.immutable.ImmutableVector;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.function.BooleanSupplier;

import static java.lang.Integer.parseInt;
import static org.aya.concrete.parse.LispParsing.parser;

/**
 * @author ice1000
 */
@TestOnly
@ApiStatus.Internal
public class TermDsl extends LispBaseVisitor<Term> {
  private final @NotNull MutableMap<String, @NotNull Var> refs;

  public TermDsl(@NotNull MutableMap<String, @NotNull Var> refs) {
    this.refs = refs;
  }

  public static @Nullable Term parse(@NotNull String text, @NotNull MutableMap<String, @NotNull Var> refs) {
    return parser(text).expr().accept(new TermDsl(refs));
  }

  public static @NotNull ImmutableSeq<Term.@NotNull Param> parseTele(@NotNull String text, @NotNull MutableMap<String, @NotNull Var> refs) {
    return new TermDsl(refs).exprToParams(parser(text).expr());
  }

  @Override
  @SuppressWarnings("unchecked")
  public Term visitExpr(LispParser.ExprContext ctx) {
    var atom = ctx.atom();
    if (atom != null) return atom.accept(this);
    var rule = ctx.IDENT().getText();
    var exprs = ctx.expr();
    return switch (rule) {
      case "U" -> UnivTerm.OMEGA;
      case "app" -> new AppTerm(exprs.get(0).accept(this), Arg.explicit(exprs.get(1).accept(this)));
      case "fncall" -> new CallTerm.Fn(
        (DefVar<FnDef, Decl.FnDecl>) ref(exprs.get(0).getText()),
        ImmutableSeq.of(),
        exprs.subList(1, exprs.size())
          .stream()
          .map(c -> Arg.explicit(c.accept(this)))
          .collect(ImmutableSeq.factory()));
      case "iapp" -> new AppTerm(exprs.get(0).accept(this), Arg.implicit(exprs.get(1).accept(this)));
      case "lam" -> new LamTerm(exprToParam(exprs.get(0)), exprs.get(1).accept(this));
      case "Pi" -> new PiTerm(false, exprToParam(exprs.get(0)), exprs.get(1).accept(this));
      case "Copi" -> new PiTerm(true, exprToParam(exprs.get(0)), exprs.get(1).accept(this));
      case "Sigma" -> new SigmaTerm(false, exprToParams(exprs.get(0)), exprs.get(1).accept(this));
      case "Cosigma" -> new SigmaTerm(true, exprToParams(exprs.get(0)), exprs.get(1).accept(this));
      case "tup" -> new TupTerm(exprs.stream().collect(ImmutableVector.factory()).map(expr -> expr.accept(this)));
      case "proj" -> new ProjTerm(exprs.get(0).accept(this), parseInt(exprs.get(1).getText()));
      default -> new RefTerm((LocalVar) ref(rule));
    };
  }

  public Term.@NotNull Param exprToParam(LispParser.ExprContext ctx) {
    var atom = ctx.atom();
    if (atom != null) throw new IllegalArgumentException("Unexpected atom: " + atom.getText());
    var ident = ctx.IDENT().getText();
    var exprs = ctx.expr();
    assert exprs.size() == 2;
    boolean explicit = licit(exprs);
    return new Term.Param((LocalVar) ref(ident), exprs.get(0).accept(this), explicit);
  }

  public @NotNull ImmutableSeq<Term.@NotNull Param> exprToParams(LispParser.ExprContext ctx) {
    var atom = ctx.atom();
    if (atom != null) {
      if ("null".equals(atom.getText())) return ImmutableSeq.of();
      throw new IllegalArgumentException("Unexpected atom: " + atom.getText());
    }
    var ident = ctx.IDENT().getText();
    var exprs = ctx.expr();
    assert exprs.size() == 3;
    boolean explicit = licit(exprs);
    var param = new Term.Param((LocalVar) ref(ident), exprs.get(0).accept(this), explicit);
    return exprToParams(exprs.get(2)).prepended(param);
  }

  private boolean licit(List<LispParser.ExprContext> exprs) {
    var licit = exprs.get(1);
    var licitAtom = licit.atom();
    BooleanSupplier err = () -> {
      System.err.println("Expected ex or im (treated as ex), got: " + licit.getText());
      return true;
    };
    return licitAtom == null || licitAtom.NUMBER() != null ? err.getAsBoolean() : switch (licitAtom.IDENT().getText()) {
      case "ex" -> true;
      case "im" -> false;
      default -> err.getAsBoolean();
    };
  }

  @Override
  public Term visitAtom(LispParser.AtomContext ctx) {
    var number = ctx.NUMBER();
    var ident = ctx.IDENT();
    if (ident != null) return new RefTerm((LocalVar) ref(ident.getText()));
    else if (number != null) throw new UnsupportedOperationException("No numbers yet!");
    throw new IllegalArgumentException(ctx.getText());
  }

  private @NotNull Var ref(String ident) {
    return refs.getOrPut(ident, () -> new LocalVar(ident));
  }
}
