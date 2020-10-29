// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.mzi.api.ref.Var;
import org.mzi.ref.LocalVar;
import org.mzi.tyck.sort.Sort;
import org.mzi.core.term.*;
import org.mzi.generic.Arg;
import org.mzi.generic.DTKind;
import org.mzi.parser.LispBaseVisitor;
import org.mzi.parser.LispParser;
import org.mzi.ref.DefVar;

import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.mzi.concrete.parse.LispParsing.parser;

/**
 * @author ice1000
 */
@TestOnly
@ApiStatus.Internal
public class TermProducer extends LispBaseVisitor<Term> {
  private final @NotNull Map<String, @NotNull Var> refs;

  public TermProducer(@NotNull Map<String, @NotNull Var> refs) {
    this.refs = refs;
  }

  public static @Nullable Term parse(@NotNull String text, @NotNull Map<String, @NotNull Var> refs) {
    return parser(text).expr().accept(new TermProducer(refs));
  }

  public static @Nullable Tele parseTele(@NotNull String text, @NotNull Map<String, @NotNull Var> refs) {
    return new TermProducer(refs).exprToBind(parser(text).expr());
  }

  @Override
  public Term visitExpr(LispParser.ExprContext ctx) {
    var atom = ctx.atom();
    if (atom != null) return atom.accept(this);
    var rule = ctx.IDENT().getText();
    var exprs = ctx.expr();
    return switch (rule) {
      case "U" -> new UnivTerm(Sort.SET0);
      case "app" -> new AppTerm.Apply(exprs.get(0).accept(this), Arg.explicit(exprs.get(1).accept(this)));
      case "fncall" -> new AppTerm.FnCall(
        (DefVar) ((RefTerm) exprs.get(0).accept(this)).var(),
        exprs.subList(1, exprs.size())
          .stream()
          .map(c -> Arg.explicit(c.accept(this)))
          .collect(ImmutableSeq.factory()));
      case "iapp" -> new AppTerm.Apply(exprs.get(0).accept(this), Arg.implicit(exprs.get(1).accept(this)));
      case "lam" -> new LamTerm(exprToBind(exprs.get(0)), exprs.get(1).accept(this));
      case "Pi" -> new PiTerm(exprToBind(exprs.get(0)), exprs.get(1).accept(this), DTKind.Pi);
      case "Copi" -> new PiTerm(exprToBind(exprs.get(0)), exprs.get(1).accept(this), DTKind.Copi);
      case "Sigma" -> new PiTerm(exprToBind(exprs.get(0)), exprs.get(1).accept(this), DTKind.Sigma);
      case "Cosigma" -> new PiTerm(exprToBind(exprs.get(0)), exprs.get(1).accept(this), DTKind.Cosigma);
      default -> throw new IllegalArgumentException("Unexpected lisp function: " + rule);
    };
  }

  public Tele exprToBind(LispParser.ExprContext ctx) {
    var atom = ctx.atom();
    if (atom != null) {
      if ("null".equals(atom.getText())) return null;
      throw new IllegalArgumentException("Unexpected atom: " + atom.getText());
    }
    var ident = ctx.IDENT().getText();
    var exprs = ctx.expr();
    return switch (exprs.size()) {
      case 1 -> new Tele.NamedTele(ref(ident), exprToBind(exprs.get(0)));
      case 3 -> {
        var licit = exprs.get(1);
        var licitAtom = licit.atom();
        boolean explicit;
        BooleanSupplier err = () -> {
          System.err.println("Expected ex or im (treated as ex), got: " + licit.getText());
          return true;
        };
        explicit = licitAtom == null || licitAtom.NUMBER() != null ? err.getAsBoolean() : switch (licitAtom.IDENT().getText()) {
          case "ex" -> true;
          case "im" -> false;
          default -> err.getAsBoolean();
        };
        yield new Tele.TypedTele(ref(ident), exprs.get(0).accept(this), explicit, exprToBind(exprs.get(2)));
      }
      default -> throw new IllegalArgumentException("Expected 1 or 3 arguments, got: " + exprs.size());
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
