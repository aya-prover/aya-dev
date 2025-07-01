// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.ir.struct;

import org.jetbrains.annotations.NotNull;

/// Represents a variable declaration in the IR. Each variable should only have one instantiation
/// of this, and it should be referenced on use site.
public interface IRVarDecl {

  /// Returns the (unique) identifier of a variable. The result of this may change in different
  /// phases of the compilation (e.g., for generated variables after gathering all contextual
  /// info).
  @NotNull String getIdentifier();

  /// Returns a newly generated reference to this variable.
  @NotNull IRVarRef newRef();
}
