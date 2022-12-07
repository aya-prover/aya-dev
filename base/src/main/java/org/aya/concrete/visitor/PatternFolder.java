// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.value.MutableValue;
import org.aya.concrete.Pattern;
import org.aya.ref.AnyVar;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public interface PatternFolder<R> {
  @NotNull R init();

  default @NotNull R foldVar(@NotNull R acc, @NotNull AnyVar var, @NotNull SourcePos pos) {
    return acc;
  }

  default @NotNull R foldVarRef(@NotNull R acc, @NotNull AnyVar var, @NotNull SourcePos pos) {
    return foldVar(acc, var, pos);
  }

  default @NotNull R foldVarDecl(@NotNull R acc, @NotNull AnyVar var, @NotNull SourcePos pos) {
    return foldVar(acc, var, pos);
  }

  default @NotNull R fold(@NotNull R acc, @NotNull Pattern pat) {
    return switch (pat) {
      case Pattern.Ctor ctor -> foldVarRef(acc, ctor.resolved().data(), ctor.resolved().sourcePos());
      case Pattern.Bind bind -> foldVarDecl(acc, bind.bind(), bind.sourcePos());
      case Pattern.As as -> foldVarDecl(acc, as.as(), as.as().definition());
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
}
