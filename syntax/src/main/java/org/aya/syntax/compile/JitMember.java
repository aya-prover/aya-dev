// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.core.def.MemberDefLike;
import org.aya.syntax.core.term.SortTerm;
import org.jetbrains.annotations.NotNull;

public abstract non-sealed class JitMember extends JitDef implements MemberDefLike {
  public final @NotNull JitClass classRef;
  public final int index;

  /**
   * the type of the type/telescope (exclude self-parameter) of this member
   */
  public final @NotNull SortTerm type;

  protected JitMember(
    int telescopeSize, boolean[] telescopeLicit, String[] telescopeNames,
    @NotNull JitClass classRef, int index, @NotNull SortTerm type) {
    super(telescopeSize, telescopeLicit, telescopeNames);
    this.classRef = classRef;
    this.index = index;
    this.type = type;
  }

  @Override
  public @NotNull ClassDefLike classRef() {
    return classRef;
  }

  @Override
  public @NotNull SortTerm type() { return type; }
  @Override
  public int index() {
    return index;
  }
}
