package org.mzi.util;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.mzi.api.ref.Ref;
import org.mzi.core.tele.Tele;
import org.mzi.core.term.*;
import org.mzi.parser.LispBaseVisitor;
import org.mzi.parser.LispLexer;
import org.mzi.parser.LispParser;
import org.mzi.ref.LocalRef;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

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
      case "lam" -> new LamTerm(exprToBind(exprs.get(0)), exprs.get(1).accept(this));
      case "Pi" -> new PiTerm(exprToBind(exprs.get(0)));
      default -> throw new IllegalArgumentException("Unexpected lisp function: " + rule);
    };
  }

  public Tele exprToBind(LispParser.ExprContext ctx) {
    var atom = ctx.atom();
    if (atom != null) throw new IllegalArgumentException("Unexpected atom: " + atom.getText());
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

  private @NotNull Ref ref(String ident) {
    return refs.computeIfAbsent(ident, LocalRef::new);
  }
}
