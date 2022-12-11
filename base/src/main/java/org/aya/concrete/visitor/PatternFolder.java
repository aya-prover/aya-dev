// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.value.LazyValue;
import kala.value.MutableValue;
import org.aya.concrete.Pattern;
import org.aya.core.def.Def;
import org.aya.core.term.PiTerm;
import org.aya.core.term.Term;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PatternFolder<R> {
  @NotNull R init();

  default @NotNull R foldVar(@NotNull R acc, @NotNull AnyVar var, @NotNull SourcePos pos, @NotNull LazyValue<@Nullable Term> type) {
    return acc;
  }

  default @NotNull R foldVarRef(@NotNull R acc, @NotNull AnyVar var, @NotNull SourcePos pos, @NotNull LazyValue<@Nullable Term> type) {
    return foldVar(acc, var, pos, type);
  }

  default @NotNull R foldVarDecl(@NotNull R acc, @NotNull AnyVar var, @NotNull SourcePos pos, @NotNull LazyValue<@Nullable Term> type) {
    return foldVar(acc, var, pos, type);
  }

  default @NotNull R fold(@NotNull R acc, @NotNull Arg<Pattern> pat) {
    return fold(acc, pat.term());
  }

  @MustBeInvokedByOverriders
  default @NotNull R fold(@NotNull R acc, @NotNull Pattern pat) {
    return switch (pat) {
      case Pattern.List list -> list.elements().foldLeft(acc, this::fold);
      case Pattern.Tuple tuple -> tuple.patterns().foldLeft(acc, this::fold);
      case Pattern.BinOpSeq seq -> seq.seq().foldLeft(acc, this::fold);
      case Pattern.Ctor ctor -> {
        var resolvedVar = ctor.resolved().data();
        acc = foldVarRef(acc, resolvedVar, ctor.resolved().sourcePos(), lazyType(resolvedVar));
        yield ctor.params().foldLeft(acc, this::fold);
      }
      case Pattern.Bind bind -> foldVarDecl(acc, bind.bind(), bind.sourcePos(), LazyValue.of(bind.type()));
      case Pattern.As as -> {
        acc = foldVarDecl(acc, as.as(), as.as().definition(), LazyValue.of(as.type()));
        yield fold(acc, as.pattern());
      }
      default -> acc;
    };
  }

  default @NotNull R apply(@NotNull Pattern pat) {
    var acc = MutableValue.create(init());
    new PatternConsumer() {
      @Override public void pre(@NotNull Pattern pat) {
        acc.set(fold(acc.get(), pat));
      }
    }.accept(pat);
    return acc.get();
  }

  default @Nullable Term varType(@Nullable AnyVar var) {
    if (var instanceof DefVar<?, ?> defVar && defVar.core instanceof Def def)
      return PiTerm.make(def.telescope(), def.result());
    return null;
  }

  default @NotNull LazyValue<@Nullable Term> lazyType(@Nullable AnyVar var) {
    return LazyValue.of(() -> varType(var));
  }

  /** @implNote Should conceptually only be used outside of these folders, where types are all ignored. */
  default @NotNull LazyValue<@Nullable Term> noType() {
    return LazyValue.ofValue(null);
  }
}
