// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.telescope;

import org.jetbrains.annotations.NotNull;

/**
 * A Jit telescope, which is efficient when instantiating parameters/result, but not friendly with DeBruijn Index.
 */
public abstract class JitTele implements AbstractTele {
  public final int telescopeSize;
  public final boolean[] telescopeLicit;
  public final String[] telescopeNames;

  protected JitTele(int telescopeSize, boolean[] telescopeLicit, String[] telescopeNames) {
    this.telescopeSize = telescopeSize;
    this.telescopeLicit = telescopeLicit;
    this.telescopeNames = telescopeNames;
  }

  @Override public int telescopeSize() { return telescopeSize; }
  @Override public boolean telescopeLicit(int i) { return telescopeLicit[i]; }
  @Override public @NotNull String telescopeName(int i) { return telescopeNames[i]; }
}
