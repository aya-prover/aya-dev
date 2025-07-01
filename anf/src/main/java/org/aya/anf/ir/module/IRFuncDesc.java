// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.ir.module;

import org.jetbrains.annotations.NotNull;

/// The locator of a function in the IR. It should contain enough contextual information to be
/// useful in diagnostics reporting (i.e., tracing back to source Aya function) and to be
/// readable in the generated program.
public sealed interface IRFuncDesc permits IRFuncDesc.Direct, IRFuncDesc.Generated {

  @NotNull String displayName();
  @NotNull IRFuncDesc source();

  /// A `Direct` function is generated directly from a source Aya function, and hence should
  /// inherit its name.
  /// TODO: include link back to source for diagnostics
  record Direct(@NotNull String displayName) implements IRFuncDesc {
    @Override
    public @NotNull IRFuncDesc source() { return this; }
  }

  /// A `Generated` function is one that is instantiated during the IR translation process,
  /// e.g., from a lambda function.
  record Generated(@NotNull Direct source, int id) implements IRFuncDesc {
    @Override
    public @NotNull String displayName() {
      return "GEN_" + source.displayName() + "_" + id;
    }
  }
}
