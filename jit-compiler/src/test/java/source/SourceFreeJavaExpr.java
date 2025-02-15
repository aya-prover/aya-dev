// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package source;

import org.aya.compiler.LocalVariable;
import org.aya.compiler.morphism.JavaExpr;
import org.jetbrains.annotations.NotNull;

public sealed interface SourceFreeJavaExpr extends JavaExpr {
  record BlackBox(@NotNull String expr) implements SourceFreeJavaExpr, LocalVariable {
    @Override public @NotNull JavaExpr ref() {
      return this;
    }
  }

  // A {@link Cont} should be used in a {@link SourceCodeBuilder} who constructs it.
  @FunctionalInterface
  non-sealed interface Cont extends SourceFreeJavaExpr, Runnable { }
}
