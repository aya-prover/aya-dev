// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.BindBlock;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.CommonDecl;
import org.aya.concrete.stmt.Stmt;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface StmtFolder<R> extends Function<Stmt, R>, ExprFolder<R> {
  private R bindBlock(@NotNull R acc, @NotNull BindBlock bb) {
    var t = Option.ofNullable(bb.resolvedTighters().get()).getOrElse(ImmutableSeq::empty);
    var l = Option.ofNullable(bb.resolvedLoosers().get()).getOrElse(ImmutableSeq::empty);
    return t.zipView(bb.tighters()).concat(l.zipView(bb.loosers()))
      .foldLeft(acc, (ac, v) -> foldVarRef(ac, v._1, v._2.sourcePos(), lazyType(v._1)));
  }

  @MustBeInvokedByOverriders
  default @NotNull R fold(@NotNull R acc, @NotNull Stmt stmt) {
    return switch (stmt) {
      case CommonDecl decl -> bindBlock(acc, decl.bindBlock);
      // TODO: #721
      case Command.Open open -> open.useHide().list().foldLeft(acc, (ac, v) -> bindBlock(ac, v.asBind()));
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
