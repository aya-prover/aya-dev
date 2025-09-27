// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.ir.struct;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Represents a variable declaration in the IR. Each variable should only have one instantiation
/// of this, and it should be referenced on use site.
public interface IrVarDecl {

  /// Returns the (unique) identifier of a variable. The result of this may change in different
  /// phases of the compilation (e.g., for generated variables after gathering all contextual
  /// info).
  @NotNull String identifier();

  /// Returns a newly generated reference to this variable.
  default @NotNull IrVarRef newRef() { return new IrVarRef(this); }

  record Local(@NotNull String identifier) implements IrVarDecl { }

  class Generated implements IrVarDecl {

    private final int uid;
    private @Nullable String assignedName = null;

    public Generated(int unnamedIndex) { uid = unnamedIndex; }

    @Override
    public @NotNull String identifier() {
      return assignedName != null ? assignedName : "gen_var_" + uid;
    }
  }
}
