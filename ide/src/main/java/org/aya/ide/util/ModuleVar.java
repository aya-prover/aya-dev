// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.util;

import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.ref.AnyVar;
import org.jetbrains.annotations.NotNull;

/** Modules are not variables. This is only used in LSP for convenience */
public record ModuleVar(@NotNull ModuleName path) implements AnyVar {
  @Override public @NotNull String name() { return path.toString(); }
}
