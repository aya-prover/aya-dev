// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import org.jetbrains.annotations.NotNull;

public class VariablePool {
  private int nextAvailable = 0;

  public VariablePool() { }

  private VariablePool(int nextAvailable) {
    this.nextAvailable = nextAvailable;
  }

  public int acquire() {
    return nextAvailable++;
  }

  public @NotNull VariablePool copy() {
    return new VariablePool(nextAvailable);
  }
}
