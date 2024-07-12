// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.core.def.MemberDefLike;
import org.jetbrains.annotations.NotNull;

public abstract non-sealed class JitMember extends JitDef implements MemberDefLike {
  public final @NotNull JitClass classRef;
  public final @NotNull int index;

  protected JitMember(
    int telescopeSize, boolean[] telescopeLicit, String[] telescopeNames,
    JitClass classRef, int index) {
    super(telescopeSize, telescopeLicit, telescopeNames);
    this.classRef = classRef;
    this.index = index;
  }

  @Override
  public @NotNull ClassDefLike classRef() {
    return classRef;
  }

  @Override
  public int index() {
    return index;
  }
}
