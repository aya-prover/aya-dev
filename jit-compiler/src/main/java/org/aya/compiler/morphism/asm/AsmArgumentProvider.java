// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.asm;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.ArgumentProvider;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public record AsmArgumentProvider(
  @NotNull ImmutableSeq<ClassDesc> parameters,
  boolean isStatic
) implements ArgumentProvider {

  @Override public @NotNull AsmVariable arg(int nth) {
    assert nth < parameters.size();
    return new AsmVariable((isStatic ? 0 : 1) + nth, parameters.get(nth), false);
  }

  public record Lambda(
    @NotNull ImmutableSeq<ClassDesc> captures,
    @NotNull ImmutableSeq<ClassDesc> parameters
  ) implements ArgumentProvider.Lambda {
    @Override public @NotNull AsmVariable capture(int nth) {
      assert nth < captures.size();
      return new AsmVariable(nth, captures.get(nth), false);
    }

    @Override public @NotNull AsmVariable arg(int nth) {
      assert nth < parameters.size();
      return new AsmVariable(captures.size() + nth, parameters.get(nth), false);
    }
  }
}
