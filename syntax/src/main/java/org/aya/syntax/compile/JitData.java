// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.def.DataDefLike;
import org.aya.syntax.telescope.JitTele;
import org.jetbrains.annotations.NotNull;

public abstract non-sealed class JitData extends JitTele implements DataDefLike {
  protected final JitCon @NotNull [] constructors;

  protected JitData(int telescopeSize, boolean[] telescopeLicit, String[] telescopeName, int conAmount) {
    super(telescopeSize, telescopeLicit, telescopeName);
    this.constructors = new JitCon[conAmount];
  }

  /**
   * The constructor of this data type, should initialize {@link #constructors} at the first call.
   */
  public abstract @NotNull JitCon @NotNull [] constructors();

  /**
   * @implNote We should use `constructors()` rather than `constructors`,
   * since `constructors()` will initialize them
   */
  @Override public @NotNull ImmutableSeq<JitCon> body() {
    return ImmutableArray.Unsafe.wrap(constructors());
  }
}
