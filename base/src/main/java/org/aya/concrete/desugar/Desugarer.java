// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.mutable.MutableList;
import kala.tuple.Unit;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.error.DoNotationError;
import org.aya.concrete.error.LevelProblem;
import org.aya.concrete.visitor.ExprOps;
import org.aya.concrete.visitor.ExprView;
import org.aya.concrete.visitor.StmtOps;
import org.aya.generic.SortKind;
import org.aya.ref.LocalVar;
import org.aya.resolve.ResolveInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva
 */
public record Desugarer(@NotNull ResolveInfo resolveInfo) implements StmtOps<Unit> {
  public record ForExpr(@Override @NotNull ExprView view, @NotNull ResolveInfo info) implements ExprOps {
    private int levelVar(@NotNull Expr expr) throws DesugarInterruption {
      return switch (expr) {
        case Expr.BinOpSeq binOpSeq -> levelVar(pre(binOpSeq));
        case Expr.LitIntExpr(var pos, var i) -> i;
        default -> {
          info.opSet().reporter.report(new LevelProblem.BadLevelExpr(expr));
          throw new DesugarInterruption();
        }
      };
    }

    @Override public @NotNull Expr pre(@NotNull Expr expr) {
      return switch (expr) {
        case Expr.AppExpr(var pos, Expr.RawSortExpr(var uPos, var kind), var arg) when kind == SortKind.Type -> {
          try {
            yield new Expr.TypeExpr(uPos, levelVar(arg.expr()));
          } catch (DesugarInterruption e) {
            yield new Expr.ErrorExpr(pos, expr);
          }
        }
        case Expr.AppExpr(var pos, Expr.RawSortExpr(var uPos, var kind), var arg) when kind == SortKind.Set -> {
          try {
            yield new Expr.SetExpr(uPos, levelVar(arg.expr()));
          } catch (DesugarInterruption e) {
            yield new Expr.ErrorExpr(pos, expr);
          }
        }
        case Expr.RawSortExpr univ -> switch (univ.kind()) {
          case Type -> new Expr.TypeExpr(univ.sourcePos(), 0);
          case Set -> new Expr.SetExpr(univ.sourcePos(), 0);
          case Prop -> new Expr.PropExpr(univ.sourcePos());
          case ISet -> new Expr.ISetExpr(univ.sourcePos());
        };
        case Expr.BinOpSeq(var pos, var seq) -> {
          assert seq.isNotEmpty() : pos.toString();
          yield pre(new BinExprParser(info, seq.view()).build(pos));
        }
        case Expr.Do doNotation -> {
          var last = doNotation.binds().last();
          if (last.var() != LocalVar.IGNORED) {
            info.opSet().reporter.report(new DoNotationError(last.sourcePos(), expr));
          }
          var rest = doNotation.binds().view().dropLast(1);
          yield rest.foldRight(last.expr(),
            // Upper: x <- a from last line
            // Lower: current line
            // Goal: >>=(a, \x -> rest)
            (upper, lower) -> new Expr.AppExpr(upper.sourcePos(),
              new Expr.AppExpr(
                upper.sourcePos(), doNotation.bindName(),
                new Expr.NamedArg(true, upper.expr())),
              new Expr.NamedArg(true, new Expr.LamExpr(lower.sourcePos(),
                new Expr.Param(lower.sourcePos(), upper.var(), true),
                lower))));
        }
        case Expr.Idiom idiom -> idiom.barredApps().view().map(app -> {
          var list = MutableList.<Expr.NamedArg>create();
          Expr.unapp(app, list);
          var pure = idiom.names().applicativePure();
          var head = new Expr.AppExpr(idiom.sourcePos(), pure, new Expr.NamedArg(true, app));
          return list.foldLeft(head, (e, arg) -> new Expr.AppExpr(e.sourcePos(),
            new Expr.AppExpr(e.sourcePos(), idiom.names().applicativeAp(),
              new Expr.NamedArg(true, e)), arg));
        }).reduceLeft((e, arg) ->
          new Expr.AppExpr(e.sourcePos(), new Expr.AppExpr(e.sourcePos(),
            idiom.names().alternativeOr(), new Expr.NamedArg(true, e)),
            new Expr.NamedArg(true, arg)));
        case Expr.Array arrayExpr -> arrayExpr.arrayBlock().fold(
          left -> {
            // desugar `[ expr | x <- xs, y <- ys ]` to `do; x <- xs; y <- ys; return expr`

            // just concat `bindings` and `return expr`
            var returnApp = new Expr.AppExpr(left.pureName().sourcePos(), left.pureName(), new Expr.NamedArg(true, left.generator()));
            var lastBind = new Expr.DoBind(left.generator().sourcePos(), LocalVar.IGNORED, returnApp);
            var doNotation = new Expr.Do(arrayExpr.sourcePos(), left.bindName(), left.binds().appended(lastBind));

            // desugar do-notation
            return pre(doNotation);
          },
          right -> {
            // desugar `[1, 2, 3]` to `consCtor 1 (consCtor 2 (consCtor 3 nilCtor))`
            return right.exprList().foldRight(right.nilCtor(),
              (e, last) -> {
                // construct `(consCtor e) last`
                // Note: the sourcePos of this call is the same as the element's (currently)
                // Recommend: use sourcePos [currentElement..lastElement]
                return new Expr.AppExpr(e.sourcePos(),
                  // construct `consCtor e`
                  new Expr.AppExpr(e.sourcePos(),
                    right.consCtor(),
                    new Expr.NamedArg(true, e)),
                  new Expr.NamedArg(true, last));
              });
          }
        );
        case Expr misc -> misc;
      };
    }
  }

  @Override public @NotNull Expr visitExpr(@NotNull Expr expr, Unit pp) {
    return new ForExpr(expr.view(), resolveInfo).commit();
  }

  public static class DesugarInterruption extends Exception {
  }

  @Override public @NotNull Pattern visitBinOpPattern(Pattern.@NotNull BinOpSeq binOpSeq, Unit unit) {
    var seq = binOpSeq.seq();
    assert seq.isNotEmpty() : binOpSeq.sourcePos().toString();
    var pat = new BinPatternParser(binOpSeq.explicit(), resolveInfo, seq.view()).build(binOpSeq.sourcePos());
    return StmtOps.super.visitPattern(pat, unit);
  }
}
