// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.value.LazyValue;
import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.*;
import org.aya.core.def.Def;
import org.aya.core.term.Term;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      .foldLeft(acc, (ac, v) -> foldVarRef(ac, v.component1(), v.component2().sourcePos(), lazyType(v.component1())));
  }

  @MustBeInvokedByOverriders
  default @NotNull R fold(@NotNull R acc, @NotNull Stmt stmt) {
    return switch (stmt) {
      case Generalize g -> g.variables.foldLeft(acc, (a, v) -> foldVarDecl(a, v, v.sourcePos, noType()));
      case Command.Module m -> foldModuleDecl(acc, new QualifiedID(m.sourcePos(), m.name()));
      case Command.Import i -> foldModuleRef(acc, i.path());
      case Command.Open o when o.fromSugar() -> acc;  // handled in `case Decl` or `case Command.Import`
      case Command.Open o -> {
        acc = foldModuleRef(acc, o.path());
        // https://github.com/aya-prover/aya-dev/issues/721
        yield o.useHide().list().foldLeft(acc, (ac, v) -> fold(ac, v.asBind()));
      }
      case Decl decl -> {
        acc = fold(acc, decl.bindBlock());
        var declType = declType(decl);
        acc = declType.component2().foldLeft(acc, (ac, p) -> foldVarDecl(ac, p.component1(), p.component1().definition(), p.component2()));
        yield foldVarDecl(acc, decl.ref(), decl.sourcePos(), declType.component1());
      }
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

  private Tuple2<@NotNull LazyValue<@Nullable Term>, SeqView<Tuple2<LocalVar, @NotNull LazyValue<@Nullable Term>>>>
  declType(@NotNull Decl decl) {
    // If it has core term, type is available.
    if (decl.ref().core instanceof Def def) return Tuple.of(lazyType(decl.ref()),
      def.telescope().view().map(p -> Tuple.of(p.ref(), LazyValue.ofValue(p.type()))));
    // If it is telescopic, type is available when it is type-checked.
    if (decl instanceof TeleDecl<?> teleDecl) return Tuple.of(lazyType(decl.ref()),
      teleDecl.telescope.view().map(p -> Tuple.of(p.ref(), withTermType(p))));
    // Oops, no type available.
    return Tuple.of(noType(), SeqView.empty());
  }
}
