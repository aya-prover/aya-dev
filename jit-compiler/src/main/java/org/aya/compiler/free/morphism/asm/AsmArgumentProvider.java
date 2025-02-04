// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.asm;

import java.lang.constant.ClassDesc;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.ArgumentProvider;
import org.jetbrains.annotations.NotNull;

public class AsmArgumentProvider implements ArgumentProvider {
  public final @NotNull ImmutableSeq<ClassDesc> parameters;
  public final boolean isStatic;

  public AsmArgumentProvider(@NotNull ImmutableSeq<ClassDesc> parameters, boolean isStatic) {
    this.parameters = parameters;
    this.isStatic = isStatic;
  }

  @Override public @NotNull AsmVariable arg(int nth) {
    assert nth < parameters.size();
    return new AsmVariable((isStatic ? 0 : 1) + nth, parameters.get(nth), false);
  }

  public static final class Lambda implements ArgumentProvider.Lambda {
    public final @NotNull ImmutableSeq<ClassDesc> captures;
    public final @NotNull ImmutableSeq<ClassDesc> parameters;
    public Lambda(@NotNull ImmutableSeq<ClassDesc> captures, @NotNull ImmutableSeq<ClassDesc> parameters) {
      this.captures = captures;
      this.parameters = parameters;
    }

    @Override public @NotNull AsmExpr capture(int nth) {
      assert nth < captures.size();
      return new AsmVariable(nth, captures.get(nth), false).ref();
    }

    @Override public @NotNull AsmVariable arg(int nth) {
      assert nth < parameters.size();
      return new AsmVariable(captures.size() + nth, parameters.get(nth), false);
    }
  }
}
