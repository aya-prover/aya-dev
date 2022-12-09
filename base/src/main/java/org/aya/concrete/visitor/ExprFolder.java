// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import org.aya.concrete.Expr;
import org.jetbrains.annotations.NotNull;

public interface ExprFolder<R> extends PatternFolder<R> {
  @NotNull R init();

  default @NotNull R fold(@NotNull R acc, @NotNull Expr expr) {
    return switch (expr) {
      case Expr.Ref ref -> foldVarRef(acc, ref.resolvedVar(), ref.sourcePos());
      case Expr.Lambda lam -> foldParamDecl(acc, lam.param());
      case Expr.Pi pi -> foldParamDecl(acc, pi.param());
      case Expr.Sigma sigma -> sigma.params().foldLeft(acc, this::foldParamDecl);
      case Expr.Path path -> path.params().foldLeft(acc, (ac, var) -> foldVarDecl(ac, var, var.definition()));
      case Expr.Array array -> array.arrayBlock().fold(
        left -> left.binds().foldLeft(acc, (ac, bind) -> foldVarDecl(ac, bind.var(), bind.sourcePos())),
        right -> acc
      );
      case Expr.Let let -> foldVarDecl(acc, let.bind().bindName(), let.bind().sourcePos());
      case Expr.Do du -> du.binds().foldLeft(acc, (ac, bind) -> foldVarDecl(ac, bind.var(), bind.sourcePos()));
      case Expr.Proj proj when proj.ix().isRight() && proj.resolvedVar() != null ->
        foldVarRef(acc, proj.resolvedVar(), proj.ix().getRightValue().sourcePos());
      case Expr.Coe coe -> foldVarRef(acc, coe.resolvedVar(), coe.id().sourcePos());
      case Expr.New neu -> neu.fields().view().foldLeft(acc, (ac, field) -> {
        var acc1 = field.bindings().foldLeft(ac, (a, binding) -> foldVarDecl(a, binding.data(), binding.sourcePos()));
        var fieldRef = field.resolvedField().get();
        return fieldRef != null ? foldVarRef(acc1, fieldRef, field.name().sourcePos()) : acc1;
      });
      case Expr.Match match -> match.clauses().foldLeft(acc, (ac, clause) -> clause.patterns.foldLeft(ac,
        (a, p) -> fold(a, p.term())));
      default -> acc;
    };
  }
}
