// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.Stmt;
import org.aya.ref.AnyVar;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface StmtFolder<R> extends Function<Stmt, R> {
  @NotNull R init();

  default @NotNull R fold(@NotNull R acc, @NotNull AnyVar var, @NotNull SourcePos pos) {
    return acc;
  }

  default @NotNull R fold(@NotNull R acc, @NotNull Pattern pat) {
    return switch (pat) {
      case Pattern.Ctor ctor -> {
        acc = fold(acc, ctor.resolved().data(), ctor.resolved().sourcePos());
        yield ctor.as() != null ? fold(acc, ctor.as(), ctor.as().definition()) : acc;
      }
      case Pattern.Bind bind -> fold(acc, bind.bind(), bind.sourcePos());
      default -> acc;
    };
  }

  default @NotNull R fold(@NotNull R acc, @NotNull Expr expr) {
    return switch (expr) {
      case Expr.RefExpr ref -> fold(acc, ref.resolvedVar(), ref.sourcePos());
      case Expr.ProjExpr proj when proj.ix().isRight() && proj.resolvedVar() != null ->
        fold(acc, proj.resolvedVar(), proj.ix().getRightValue().sourcePos());
      case Expr.CoeExpr coe -> fold(acc, coe.resolvedVar(), coe.id().sourcePos());
      case Expr.NewExpr neu -> neu.fields().view().foldLeft(acc, (ac, field) -> {
        var acc1 = field.bindings().foldLeft(ac, (a, binding) -> fold(a, binding.data(), binding.sourcePos()));
        var fieldRef = field.resolvedField().get();
        return fieldRef != null ? fold(acc1, fieldRef, field.name().sourcePos()) : acc1;
      });
      case Expr.Match match -> match.clauses().foldLeft(acc, (ac, clause) -> clause.patterns.foldLeft(ac, this::fold));
      default -> acc;
    };
  }

  default @NotNull R fold(@NotNull R acc, @NotNull Stmt stmt) {
    return acc;
  }

  default @NotNull R apply(@NotNull Stmt stmt) {
    var acc = MutableValue.create(init());
    new StmtConsumer() {
      @Override public void accept(@NotNull Stmt stmt) {
        acc.set(fold(acc.get(), stmt));
        StmtConsumer.super.accept(stmt);
      }

      @Override public @NotNull Expr pre(@NotNull Expr expr) {
        acc.set(fold(acc.get(), expr));
        return expr;
      }

      @Override public @NotNull Pattern pre(@NotNull Pattern pattern) {
        acc.set(fold(acc.get(), pattern));
        return pattern;
      }
    }.accept(stmt);
    return acc.get();
  }
}
