// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import org.aya.compiler.morphism.ArgumentProvider;
import org.jetbrains.annotations.NotNull;

/**
 * A variable name counter.
 * Note that we do not reserve place for arguments even though they are local variables in Java,
 * because we need to keep the invocation of {@link ArgumentProvider}, see {@link AstVariable.Arg}
 */
public class VariablePool {
  private int nextAvailable = 0;

  public VariablePool() { }
  public VariablePool(int nextAvailable) { this.nextAvailable = nextAvailable; }
  public int acquire() { return nextAvailable++; }
  public @NotNull VariablePool copy() { return new VariablePool(nextAvailable); }
}
