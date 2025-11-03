// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.telescope.JitTele;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract non-sealed class JitClass extends JitDef implements ClassDefLike {
  protected JitMember @Nullable [] members = null;

  protected JitClass() {
    super();
  }

  @Override public int classifyingIndex() { return -1; }
  public abstract @NotNull JitMember[] membars();

  @Override public final @NotNull ImmutableSeq<JitMember> members() {
    return ImmutableArray.Unsafe.wrap(membars());
  }

  @Override public @NotNull JitTele signature() { return Panic.unreachable(); }
}
