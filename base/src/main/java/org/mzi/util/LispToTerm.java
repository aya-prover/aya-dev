package org.mzi.util;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.mzi.api.ref.Ref;
import org.mzi.core.term.*;
import org.mzi.parser.LispBaseVisitor;
import org.mzi.parser.LispLexer;
import org.mzi.parser.LispParser;
import org.mzi.ref.LocalRef;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author ice1000
 */
@TestOnly
public class LispToTerm extends LispBaseVisitor<Term> {
  private final @NotNull Map<String, @NotNull Ref> refs = new TreeMap<>();

  private static @NotNull LispParser parser(@NotNull String text) {
    return new LispParser(new CommonTokenStream(lexer(text)));
  }

  private static @NotNull LispLexer lexer(@NotNull String text) {
    return new LispLexer(CharStreams.fromString(text));
  }

  static @Nullable Term parse(@NotNull String text) {
    var parser = parser(text);
    return parser.getContext().accept(new LispToTerm());
  }

  @Override
  public Term visitExpr(LispParser.ExprContext ctx) {
    var atom = ctx.atom();
    if (atom != null) return atom.accept(this);
    var rule = ctx.IDENT().getText();
    var exprs = ctx.expr();
    return switch (rule) {
      case "U" -> new UnivTerm();
      case "app" -> new AppTerm.Apply(exprs.get(0).accept(this), new Arg(exprs.get(1).accept(this), true));
      case "iapp" -> new AppTerm.Apply(exprs.get(0).accept(this), new Arg(exprs.get(1).accept(this), false));
      default -> throw new IllegalArgumentException("Unexpected value: " + rule);
    };
  }

  @Override
  public Term visitAtom(LispParser.AtomContext ctx) {
    var number = ctx.NUMBER();
    var ident = ctx.IDENT();
    if (ident != null) {
      return new RefTerm(refs.computeIfAbsent(ident.getText(), LocalRef::new));
    } else if (number != null) {
      throw new UnsupportedOperationException("No numbers yet!");
    }
    throw new IllegalArgumentException(ctx.getText());
  }
}
