package org.aya.tactic.reflect;

import org.aya.concrete.Expr;
import org.aya.concrete.GenericAyaParser;
import org.aya.core.term.Term;
import org.aya.generic.util.InternalException;
import org.aya.tyck.ExprTycker;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

// todo: some ideas:
// create a new expr, call it "reflected expr"?


public class Reflector {

  private final @NotNull ExprTycker exprTycker;
  private final @NotNull GenericAyaParser ayaParser;

  Reflector(@NotNull ExprTycker exprTycker, @NotNull GenericAyaParser ayaParser) {
    this.exprTycker = exprTycker;
    this.ayaParser = ayaParser;
  }

  /**
   * Reflect statically into string
   *
   * @param expr {@link Expr} being elaborated
   * @return reflected  {@link String}
   */
  private @NotNull static String reflectToStr(@NotNull Expr expr) {
    var prefix = "(" + prefix(expr);
    return prefix + switch (expr) {
      case Expr.RefExpr ref -> ref.resolvedVar().name();
      case Expr.LamExpr lam -> {
        var binder = lam.param().ref().name();
        var dom = reflectToStr(lam.param().type());
        var cod = reflectToStr(lam.body());
        yield String.join(" ", binder, dom, cod);
      }
      case Expr.PiExpr pi -> {
        var binder = pi.param().ref().name();
        var dom = reflectToStr(pi.param().type());
        var cod = reflectToStr(pi.last());
        yield String.join(" ", binder, dom, cod);
      }
      case Expr.SigmaExpr sig -> {
        var first = sig.params().first();
        var binder = first.ref().name();
        var dom = reflectToStr(first.type());
        var rest = reflectToStr(new Expr.SigmaExpr(sig.sourcePos(), sig.co(), sig.params().drop(1)));
        yield String.join(" ", binder, dom, rest);
      }
      case Expr.UnivExpr univ -> univ.lift();
      case Expr.AppExpr app -> reflectToStr(app.function()) + " " + reflectToStr(app.argument().expr());
      case Expr.ProjExpr proj -> {
        var tup = reflectToStr(proj.tup());
        if (proj.ix().isLeft()) {
          yield tup + proj.ix().getLeftValue();
        } else {
          yield tup + proj.ix().getRightValue();
        }
      }
      case Expr.IntervalExpr ignored -> "";
      case Expr.TupExpr tup -> {
        var cons = " <: "; // TODO: find ways to make it unambiguous
        var nil = "nil";
        yield tup.items().map(Reflector::reflectToStr)
          .appended(nil)
          .joinToString(cons);
      }
      case Expr.NewExpr neu -> {
        var fields = neu.fields().map(field -> {
          var front = "(new Meta.Reflection.Field";
          var name = field.name().data();
          var bindings = field.bindings().map(WithPos::data).joinToString(" <: ");
          return String.join(" ", front,
            "{",
            "| name =>", "\"", name, "\"",
            "| bindings =>", bindings,
            "| body =>", reflectToStr(field.body()),
            "})");
        }).appended("nil").joinToString(" <: ");
        yield reflectToStr(neu.struct()) + " " + fields;
      }
      case Expr.LiftExpr l -> reflectToStr(l.expr()) + l.lift();
      case Expr.HoleExpr hole -> {
        if (hole.filling() != null) {
          yield "(just " + reflectToStr(hole.filling()) + ")";
        } else yield "none";
      }
      default -> throw new InternalException("Unsupported reflection");
    } + ")";
  }

  private @NotNull static String prefix(@NotNull Expr expr) {
    var pre = "Meta.Reflection.Expr.";
    return pre + switch (expr) {
      case Expr.RefExpr ignored -> "ref ";
      case Expr.LamExpr ignored -> "lam ";
      case Expr.PiExpr ignored -> "pi ";
      case Expr.SigmaExpr ignored -> "sigma ";
      case Expr.UnivExpr ignored -> "univ ";
      case Expr.AppExpr ignored -> "app ";
      case Expr.ProjExpr ignored -> "proj ";
      case Expr.IntervalExpr ignored -> "I";
      case Expr.TupExpr ignored -> "tup ";
      case Expr.NewExpr ignored -> "newEx ";
      case Expr.LiftExpr ignored -> "lift ";
      case Expr.HoleExpr ignored -> "hole ";
      default -> throw new InternalException("Unsupported reflection");
    };
  }

  public @NotNull Expr reflectToExpr(@NotNull Expr expr) {
    return ayaParser.expr(reflectToStr(expr), expr.sourcePos());
  }

  public @NotNull Term reflectToTerm(@NotNull Expr expr) {
    return exprTycker.synthesize(reflectToExpr(expr)).wellTyped();
  }

  public @NotNull Term unreflect(@NotNull Term reflected) {
    throw new InternalException("unimplemented");
  }
}
