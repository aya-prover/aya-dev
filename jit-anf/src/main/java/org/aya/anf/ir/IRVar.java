// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.ir;

import org.jetbrains.annotations.NotNull;

public sealed interface IRVar permits IRVar.Generated, IRVar.Local {
  /// Shows the display name of the variable. Used during debugging and source generation.
  @NotNull String display();

  // TODO: generated vars should derive their name from the generation context.
  // e.g., when trivializing a nested application, the parameters are assigned to generated
  // vars in surronding let-bindings. Their display name should reflect that.

  record Generated() implements IRVar {
    @Override public @NotNull String display() { return "TODO"; }
  }

  record Local(@NotNull String name) implements IRVar {
    public @Override @NotNull String display() { return name; }
  }
}
