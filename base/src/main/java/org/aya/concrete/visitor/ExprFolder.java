// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.value.LazyValue;
import org.aya.concrete.Expr;
import org.aya.core.term.IntervalTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;

public interface ExprFolder<R> extends PatternFolder<R> {
  @NotNull R init();

  default @NotNull R foldParamDecl(@NotNull R acc, Expr.@NotNull Param param) {
    return foldVarDecl(acc, param.ref(), param.sourcePos(), noType()); // TODO: param type
  }

  @MustBeInvokedByOverriders
  default @NotNull R fold(@NotNull R acc, @NotNull Expr expr) {
    return switch (expr) {
      case Expr.Ref ref -> foldVarRef(acc, ref.resolvedVar(), ref.sourcePos(), LazyValue.of(() -> {
        var core = ref.core();
        return core == null ? null : core.type();
      }));
      case Expr.Lambda lam -> foldParamDecl(acc, lam.param());
      case Expr.Pi pi -> foldParamDecl(acc, pi.param());
      case Expr.Sigma sigma -> sigma.params().foldLeft(acc, this::foldParamDecl);
      case Expr.Path path -> {
        var type = LazyValue.<Term>ofValue(IntervalTerm.INSTANCE);
        yield path.params().foldLeft(acc, (ac, var) -> foldVarDecl(ac, var, var.definition(), type));
      }
      // TODO: type for array bind, let bind, and do bind
      case Expr.Array array -> array.arrayBlock().fold(
        left -> left.binds().foldLeft(acc, (ac, bind) -> foldVarDecl(ac, bind.var(), bind.sourcePos(), noType())),
        right -> acc
      );
      case Expr.Let let -> foldVarDecl(acc, let.bind().bindName(), let.bind().sourcePos(), noType());
      case Expr.Do du -> du.binds().foldLeft(acc, (ac, bind) -> foldVarDecl(ac, bind.var(), bind.sourcePos(), noType()));
      case Expr.Proj proj when proj.ix().isRight() && proj.resolvedVar() != null ->
        foldVarRef(acc, proj.resolvedVar(), proj.ix().getRightValue().sourcePos(), lazyType(proj.resolvedVar()));
      case Expr.Coe coe -> foldVarRef(acc, coe.resolvedVar(), coe.id().sourcePos(), lazyType(coe.resolvedVar()));
      case Expr.New neu -> neu.fields().view().foldLeft(acc, (ac, field) -> {
        // TODO: type for `field.bindings()`
        var acc1 = field.bindings().foldLeft(ac, (a, binding) -> foldVarDecl(a, binding.data(), binding.sourcePos(), noType()));
        var fieldRef = field.resolvedField().get();
        return fieldRef != null ? foldVarRef(acc1, fieldRef, field.name().sourcePos(), lazyType(fieldRef)) : acc1;
      });
      case Expr.Match match -> match.clauses().foldLeft(acc, (ac, clause) -> clause.patterns.foldLeft(ac,
        (a, p) -> fold(a, p.term())));
      default -> acc;
    };
  }
}
