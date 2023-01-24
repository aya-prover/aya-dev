// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import org.aya.concrete.stmt.Stmt;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public sealed interface ContextUnit {
  @NotNull AnyVar data();

  record Exportable(
    @Override @NotNull DefVar<?, ?> data,
    @NotNull Stmt.Accessibility accessibility
  ) implements ContextUnit {
  }

  record NotExportable(
    @Override @NotNull AnyVar data
  ) implements ContextUnit {
    public NotExportable {
      assert !(data instanceof DefVar<?, ?>) : "Use Exportable instead.";
    }
  }

  /// region Factory

  static ContextUnit ofPublic(@NotNull DefVar<?, ?> data) {
    return new Exportable(data, Stmt.Accessibility.Public);
  }

  static ContextUnit ofPrivate(@NotNull DefVar<?, ?> data) {
    return new Exportable(data, Stmt.Accessibility.Private);
  }

  /// endregion
}
