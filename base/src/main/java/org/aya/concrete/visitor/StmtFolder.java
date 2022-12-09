// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.*;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface StmtFolder<R> extends Function<Stmt, R>, ExprFolder<R> {
  default @NotNull R foldModuleDecl(@NotNull R acc, @NotNull QualifiedID mod) {
    return acc;
  }

  default @NotNull R foldModuleRef(@NotNull R acc, @NotNull QualifiedID mod) {
    return acc;
  }

  default @NotNull R fold(@NotNull R acc, @NotNull BindBlock bb) {
    var t = Option.ofNullable(bb.resolvedTighters().get()).getOrElse(ImmutableSeq::empty);
    var l = Option.ofNullable(bb.resolvedLoosers().get()).getOrElse(ImmutableSeq::empty);
    return t.zipView(bb.tighters()).concat(l.zipView(bb.loosers()))
      .foldLeft(acc, (ac, v) -> foldVarRef(ac, v._1, v._2.sourcePos(), lazyType(v._1)));
  }

  @MustBeInvokedByOverriders
  default @NotNull R fold(@NotNull R acc, @NotNull Stmt stmt) {
    return switch (stmt) {
      case Generalize g -> g.variables.foldLeft(acc, (a, v) -> foldVarDecl(a, v, v.sourcePos, noType()));
      case Command.Module m -> foldModuleDecl(acc, new QualifiedID(m.sourcePos(), m.name()));
      case Command.Import i -> foldModuleRef(acc, i.path());
      case Command.Open o when o.fromSugar() -> acc;  // handled in `case Decl` or `case Command.Import`
      case Command.Open o -> {
        var acc1 = foldModuleRef(acc, o.path());
        // https://github.com/aya-prover/aya-dev/issues/721
        yield o.useHide().list().foldLeft(acc1, (ac, v) -> fold(ac, v.asBind()));
      }
      default -> acc;
    };
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
