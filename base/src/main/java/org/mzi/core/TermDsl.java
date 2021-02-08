// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core;

import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.immutable.ImmutableVector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.mzi.api.ref.DefVar;
import org.mzi.api.ref.Var;
import org.mzi.core.def.FnDef;
import org.mzi.core.term.*;
import org.mzi.generic.Arg;
import org.mzi.parser.LispBaseVisitor;
import org.mzi.parser.LispParser;
import org.mzi.ref.LocalVar;
import org.mzi.tyck.sort.Sort;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static java.lang.Integer.parseInt;
import static org.mzi.concrete.parse.LispParsing.parser;

/**
 * @author ice1000
 */
@TestOnly
@ApiStatus.Internal
public class TermDsl extends LispBaseVisitor<Term> {
  private final @NotNull Map<String, @NotNull Var> refs;

  public TermDsl(@NotNull Map<String, @NotNull Var> refs) {
    this.refs = refs;
  }

  public static @Nullable Term parse(@NotNull String text, @NotNull Map<String, @NotNull Var> refs) {
    return parser(text).expr().accept(new TermDsl(refs));
  }

  public static @NotNull ImmutableSeq<@NotNull Param> parseTele(@NotNull String text, @NotNull Map<String, @NotNull Var> refs) {
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
      case "U" -> new UnivTerm(Sort.OMEGA);
      case "app" -> new AppTerm.Apply(exprs.get(0).accept(this), Arg.explicit(exprs.get(1).accept(this)));
      case "fncall" -> new AppTerm.FnCall(
        (DefVar<FnDef>) ((RefTerm) exprs.get(0).accept(this)).var(),
        exprs.subList(1, exprs.size())
          .stream()
          .map(c -> Arg.explicit(c.accept(this)))
          .collect(ImmutableSeq.factory()));
      case "iapp" -> new AppTerm.Apply(exprs.get(0).accept(this), Arg.implicit(exprs.get(1).accept(this)));
      case "lam" -> new LamTerm(exprToParam(exprs.get(0)), exprs.get(1).accept(this));
      case "Pi" -> new PiTerm(false, exprToParam(exprs.get(0)), exprs.get(1).accept(this));
      case "Copi" -> new PiTerm(true, exprToParam(exprs.get(0)), exprs.get(1).accept(this));
      case "Sigma" -> new SigmaTerm(false, exprToParams(exprs.get(0)), exprs.get(1).accept(this));
      case "Cosigma" -> new SigmaTerm(true, exprToParams(exprs.get(0)), exprs.get(1).accept(this));
      case "tup" -> new TupTerm(exprs.stream().collect(ImmutableVector.factory()).map(expr -> expr.accept(this)));
      case "proj" -> new ProjTerm(exprs.get(0).accept(this), parseInt(exprs.get(1).getText()));
      default -> new RefTerm(ref(rule));
    };
  }

  public @NotNull Param exprToParam(LispParser.ExprContext ctx) {
    var atom = ctx.atom();
    if (atom != null) {
      throw new IllegalArgumentException("Unexpected atom: " + atom.getText());
    }
    var ident = ctx.IDENT().getText();
    var exprs = ctx.expr();
    assert exprs.size() == 2;
    boolean explicit = licit(exprs);
    return new Param(ref(ident), exprs.get(0).accept(this), explicit);
  }

  public @NotNull ImmutableSeq<@NotNull Param> exprToParams(LispParser.ExprContext ctx) {
    var atom = ctx.atom();
    if (atom != null) {
      if ("null".equals(atom.getText())) return ImmutableSeq.of();
      throw new IllegalArgumentException("Unexpected atom: " + atom.getText());
    }
    var ident = ctx.IDENT().getText();
    var exprs = ctx.expr();
    assert exprs.size() == 3;
    boolean explicit = licit(exprs);
    return exprToParams(exprs.get(2)).prepended(new Param(ref(ident), exprs.get(0).accept(this), explicit));
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
    if (ident != null) return new RefTerm(ref(ident.getText()));
    else if (number != null) throw new UnsupportedOperationException("No numbers yet!");
    throw new IllegalArgumentException(ctx.getText());
  }

  private @NotNull Var ref(String ident) {
    return refs.computeIfAbsent(ident, LocalVar::new);
  }
}
