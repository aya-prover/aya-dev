// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import org.aya.concrete.stmt.QualifiedID;
import org.aya.ref.Var;
import org.jetbrains.annotations.NotNull;

/** Modules are not variables. This is only used in LSP for convenience */
public record ModuleVar(@NotNull QualifiedID path) implements Var {
  @Override public @NotNull String name() {
    return path.join();
  }
}
