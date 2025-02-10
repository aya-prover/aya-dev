// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package source;

import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.data.LocalVariable;
import org.jetbrains.annotations.NotNull;

public sealed interface SourceFreeJavaExpr extends FreeJavaExpr {
  record BlackBox(@NotNull String expr) implements SourceFreeJavaExpr, LocalVariable {
    @Override public @NotNull FreeJavaExpr ref() {
      return this;
    }
  }

  // A {@link Cont} should be used in a {@link SourceCodeBuilder} who constructs it.
  @FunctionalInterface
  non-sealed interface Cont extends SourceFreeJavaExpr, Runnable {
  }
}
