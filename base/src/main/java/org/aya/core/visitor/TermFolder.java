// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.value.MutableValue;
import org.aya.core.pat.Pat;
import org.aya.core.term.Callable;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.ref.AnyVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface TermFolder<R> extends Function<Term, R> {
  @NotNull R init();

  default @NotNull R fold(@NotNull R acc, @NotNull AnyVar var) {
    return acc;
  }

  default @NotNull R fold(@NotNull R acc, @NotNull Pat pat) {
    return switch (pat) {
      case Pat.Ctor ctor -> fold(acc, ctor.ref());
      case Pat.Bind bind -> fold(acc, bind.bind());
      default -> acc;
    };
  }

  default @NotNull R fold(@NotNull R acc, @NotNull Term term) {
    return switch (term) {
      case Callable call -> fold(acc, call.ref());
      case RefTerm ref -> fold(acc, ref.var());
      case RefTerm.Field field -> fold(acc, field.ref());
      default -> acc;
    };
  }

  @Override default @NotNull R apply(@NotNull Term term) {
    var acc = MutableValue.create(init());
    new TermConsumer() {
      @Override public void pre(@NotNull Term term) {
        acc.set(fold(acc.get(), term));
      }
    }.accept(term);
    return acc.get();
  }

  record Usages(@NotNull AnyVar var) implements TermFolder<Integer> {
    @Override public @NotNull Integer init() {
      return 0;
    }

    @Override public @NotNull Integer fold(@NotNull Integer count, @NotNull AnyVar v) {
      return v == var ? count + 1 : count;
    }
  }
}
