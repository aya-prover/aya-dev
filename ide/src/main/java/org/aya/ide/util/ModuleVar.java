// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.util;

import org.aya.ref.AnyVar;
import org.aya.resolve.context.ModuleName;
import org.jetbrains.annotations.NotNull;

/** Modules are not variables. This is only used in LSP for convenience */
public record ModuleVar(@NotNull ModuleName path) implements AnyVar {
  @Override public @NotNull String name() {
    return path.toString();
  }
}
