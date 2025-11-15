// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.asm;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

/// The implementation should be pure (at least, same input same output, some kind of side effect is acceptable)
public interface ArgsProvider {
  @NotNull AsmVariable arg(int nth);

  @NotNull ArgsProvider EMPTY = _ -> {
    throw new IndexOutOfBoundsException();
  };

  record FnParam(
    @NotNull ImmutableSeq<ClassDesc> parameters,
    boolean isStatic
  ) implements ArgsProvider {
    @Override public @NotNull AsmVariable arg(int nth) {
      assert nth < parameters.size();
      return new AsmVariable((isStatic ? 0 : 1) + nth, parameters.get(nth), false);
    }
  }

  record Lambda(
    @NotNull ImmutableSeq<ClassDesc> captures,
    @NotNull ImmutableSeq<ClassDesc> parameters
  ) implements ArgsProvider {
    public @NotNull AsmVariable capture(int nth) {
      assert nth < captures.size();
      return new AsmVariable(nth, captures.get(nth), false);
    }

    @Override public @NotNull AsmVariable arg(int nth) {
      assert nth < parameters.size();
      return new AsmVariable(captures.size() + nth, parameters.get(nth), false);
    }
  }
}
