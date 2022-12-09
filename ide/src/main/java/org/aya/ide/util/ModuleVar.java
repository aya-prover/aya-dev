// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.util;

import org.aya.concrete.stmt.QualifiedID;
import org.aya.ref.AnyVar;
import org.jetbrains.annotations.NotNull;

/** Modules are not variables. This is only used in LSP for convenience */
public record ModuleVar(@NotNull QualifiedID path) implements AnyVar {
  @Override public @NotNull String name() {
    return path.join();
  }
}
