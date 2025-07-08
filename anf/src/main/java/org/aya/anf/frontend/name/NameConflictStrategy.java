// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.name;

import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/// Represents a strategy for generating a fresh variable name during compilation. An instance
/// of this should be able to ensure that newly generated names do not conflict with existing
/// ones while maximizing readability. It should also keep a record of the names in the current
/// scope (managed by its responsible `ScopeTracker`) and deallocate name of out-of-scope binds
/// for variable name reuse.
public interface NameConflictStrategy extends UnaryOperator<String> {
  boolean conflict(@NotNull String name);

  class Numbering implements NameConflictStrategy {

    // TODO

    @Override
    public boolean conflict(@NotNull String name) {
      return false;
    }

    @Override
    public String apply(String s) {
      return "";
    }
  }
}
