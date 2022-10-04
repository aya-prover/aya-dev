// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.tuple.Tuple;
import org.aya.concrete.Expr;
import org.jetbrains.annotations.NotNull;

/**
 * A generic view for traversing Expr
 *
 * @author luna
 */

public interface ExprView {
  @NotNull Expr initial();

  default @NotNull Expr pre(@NotNull Expr expr) {return expr;}

  default @NotNull Expr post(@NotNull Expr expr) {return expr;}

  private @NotNull Expr commit(@NotNull Expr expr) {return post(traverse(pre(expr)));}

  private Expr.@NotNull Param commit(Expr.@NotNull Param param) {
    var type = commit(param.type());
    if (type == param.type()) return param;
    return new Expr.Param(param, type);
  }

  private Expr.@NotNull NamedArg commit(Expr.@NotNull NamedArg arg) {
    var expr = commit(arg.expr());
    if (expr == arg.expr()) return arg;
    return new Expr.NamedArg(arg.explicit(), expr);
  }

  private Expr.@NotNull PartEl commit(Expr.@NotNull PartEl partial) {
    var clauses = partial.clauses().map(cls -> Tuple.of(commit(cls._1), commit(cls._2)));
    if (clauses == partial.clauses()) return partial;
    return new Expr.PartEl(partial.sourcePos(), clauses);
  }

  private @NotNull Expr traverse(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.RefExpr ref -> ref; // I don't know
      case Expr.UnresolvedExpr unresolved -> unresolved;
      case Expr.LamExpr lam -> {
        var param = commit(lam.param());
        var body = commit(lam.body());
        if (param == lam.param() && body == lam.body()) yield lam;
        yield new Expr.LamExpr(lam.sourcePos(), param, body);
      }
      case Expr.PiExpr pi -> {
        var param = commit(pi.param());
        var last = commit(pi.last());
        if (param == pi.param() && last == pi.last()) yield pi;
        yield new Expr.PiExpr(pi.sourcePos(), pi.co(), param, last);
      }
      case Expr.SigmaExpr sigma -> {
        var params = sigma.params().map(this::commit);
        if (params.sameElements(sigma.params(), true)) yield sigma;
        yield new Expr.SigmaExpr(sigma.sourcePos(), sigma.co(), params);
      }
      case Expr.RawSortExpr rawType -> rawType;
      case Expr.LiftExpr lift -> {
        var inner = commit(lift.expr());
        if (inner == lift.expr()) yield lift;
        yield new Expr.LiftExpr(lift.sourcePos(), inner, lift.lift());
      }
      case Expr.SortExpr univ -> univ;
      case Expr.AppExpr app -> {
        var func = commit(app.function());
        var arg = commit(app.argument());
        if (func == app.function() && arg == app.argument()) yield app;
        yield new Expr.AppExpr(app.sourcePos(), func, arg);
      }
      case Expr.HoleExpr hole -> {
        var filling = hole.filling();
        var committed = filling != null ? commit(filling) : null;
        if (committed == filling) yield hole;
        yield new Expr.HoleExpr(hole.sourcePos(), hole.explicit(), committed, hole.accessibleLocal());
      }
      case Expr.TupExpr tup -> {
        var items = tup.items().map(this::commit);
        if (items.sameElements(tup.items(), true)) yield tup;
        yield new Expr.TupExpr(tup.sourcePos(), items);
      }
      case Expr.ProjExpr proj -> {
        var tup = commit(proj.tup());
        if (tup == proj.tup()) yield proj;
        yield new Expr.ProjExpr(proj.sourcePos(), tup, proj.ix(), proj.resolvedIx(), proj.theCore());
      }
      case Expr.NewExpr neu -> {
        var struct = commit(neu.struct());
        var fields = neu.fields().map(field ->
          new Expr.Field(field.name(), field.bindings(), commit(field.body()), field.resolvedField()));
        if (struct == neu.struct() && fields.sameElements(neu.fields(), true)) yield neu;
        yield new Expr.NewExpr(neu.sourcePos(), struct, fields);
      }
      case Expr.PartEl el -> commit(el);
      case Expr.Path path -> {
        var partial = commit(path.partial());
        var type = commit(path.type());
        if (partial == path.partial() && type == path.type()) yield path;
        yield new Expr.Path(path.sourcePos(), path.params(), type, partial);
      }
      case Expr.LitIntExpr litInt -> litInt;
      case Expr.LitStringExpr litStr -> litStr;
      case Expr.BinOpSeq binOpSeq -> {
        var seq = binOpSeq.seq().map(this::commit);
        if (seq.sameElements(binOpSeq.seq(), true)) yield binOpSeq;
        yield new Expr.BinOpSeq(binOpSeq.sourcePos(), seq);
      }
      case Expr.ErrorExpr error -> error;
      case Expr.MetaPat meta -> meta;
      case Expr.Idiom idiom -> {
        var newInner = idiom.barredApps().map(this::commit);
        var newNames = idiom.names().fmap(this::commit);
        if (newInner.sameElements(idiom.barredApps()) && newNames.identical(idiom.names()))
          yield expr;
        yield new Expr.Idiom(idiom.sourcePos(), newNames, newInner);
      }
      case Expr.Do doNotation -> {
        var lamExprs = doNotation.binds().map(x ->
          new Expr.DoBind(x.sourcePos(), x.var(), commit(x.expr())));
        var bindName = commit(doNotation.bindName());
        if (lamExprs.sameElements(doNotation.binds()) && bindName == doNotation.bindName())
          yield doNotation;
        yield new Expr.Do(doNotation.sourcePos(), bindName, lamExprs);
      }
      case Expr.Array arrayExpr -> {
        var arrayBlock = arrayExpr.arrayBlock().map(
          left -> {
            var generator = commit(left.generator());
            var bindings = left.bindings().map(binding ->
              new Expr.DoBind(binding.sourcePos(), binding.var(), commit(binding.expr()))
            );
            var bindName = commit(left.bindName());

            if (generator == left.generator() && bindings.sameElements(left.bindings()) && bindName == left.bindName()) {
              return left;
            } else {
              return new Expr.Array.CompBlock(generator, bindings, bindName);
            }
          },
          right -> {
            var exprs = right.exprList().map(this::commit);
            var nilCtor = commit(right.nilCtor());
            var consCtor = commit(right.consCtor());

            if (exprs.sameElements(right.exprList()) && nilCtor == right.nilCtor() && consCtor == right.consCtor()) {
              return right;
            } else {
              return new Expr.Array.ElementList(exprs, nilCtor, consCtor);
            }
          }
        );

        if (arrayBlock == arrayExpr.arrayBlock()) {
          yield arrayExpr;
        } else {
          yield new Expr.Array(arrayExpr.sourcePos(), arrayBlock);
        }
      }
    };
  }

  default @NotNull Expr commit() {
    return commit(initial());
  }
}
