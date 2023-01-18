// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

public sealed interface ContextUnit {
  @NotNull AnyVar data();

  sealed interface TopLevel extends ContextUnit permits Defined, Outside {
  }

  sealed interface Defined extends ContextUnit, TopLevel permits Exportable, NotExportable {
    @NotNull Stmt.Accessibility accessibility();
  }

  record Exportable(
    @Override @NotNull DefVar<?, ?> data,
    @NotNull Stmt.Accessibility accessibility
  ) implements Defined {
  }

  record NotExportable(
    @Override @NotNull AnyVar data
  ) implements Defined {
    @Override
    public @NotNull Stmt.Accessibility accessibility() {
      return Stmt.Accessibility.Private;
    }
  }

  record Local(@Override @NotNull LocalVar data) implements ContextUnit {}

  /**
   * A var that come from the other module
   */
  record Outside(@Override @NotNull DefVar<?, ?> data) implements ContextUnit, TopLevel {}

  /// region Factory

  static ContextUnit ofPublic(@NotNull DefVar<?, ?> data) {
    return new Exportable(data, Stmt.Accessibility.Public);
  }

  static ContextUnit ofPrivate(@NotNull DefVar<?, ?> data) {
    return new Exportable(data, Stmt.Accessibility.Private);
  }

  static Outside ofOutside(@NotNull DefVar<?, ?> data) {
    return new Outside(data);
  }

  /// endregion
}
