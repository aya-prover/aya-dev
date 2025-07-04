// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.ir.struct;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Represents a variable declaration in the IR. Each variable should only have one instantiation
/// of this, and it should be referenced on use site.
public interface IRVarDecl {

  /// Returns the (unique) identifier of a variable. The result of this may change in different
  /// phases of the compilation (e.g., for generated variables after gathering all contextual
  /// info).
  @NotNull String getIdentifier();

  /// Returns a newly generated reference to this variable.
  @NotNull IRVarRef newRef();

  class Generated implements IRVarDecl {

    private final int uid;
    private @Nullable String assignedName = null;

    public Generated(int unnamedIndex) { uid = unnamedIndex; }

    @Override
    public @NotNull String getIdentifier() {
      return assignedName != null ? assignedName : "gen_var_" + uid;
    }

    @Override
    public @NotNull IRVarRef newRef() {
      return new IRVarRef(this);
    }
  }
}
