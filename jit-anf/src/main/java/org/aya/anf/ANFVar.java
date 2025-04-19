// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf;

import org.jetbrains.annotations.NotNull;

public sealed interface ANFVar permits ANFVar.Generated, ANFVar.Local {
  /// Shows the display name of the variable. Used during debugging and source generation.
  @NotNull String display();

  // TODO: generated vars should derive their name from the generation context.
  // e.g., when trivializing a nested application, the parameters are assigned to generated
  // vars in surronding let-bindings. Their display name should reflect that.

  record Generated() implements ANFVar {
    @Override public @NotNull String display() { return "TODO"; }
  }

  record Local(@NotNull String name) implements ANFVar {
    public @Override @NotNull String display() { return name; }
  }
}
