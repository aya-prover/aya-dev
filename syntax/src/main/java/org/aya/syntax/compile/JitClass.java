// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.core.def.MemberDefLike;
import org.aya.syntax.core.term.SortTerm;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public abstract non-sealed class JitClass extends JitDef implements ClassDefLike {
  private JitMember[] members = null;

  protected JitClass() {
    super(0, new boolean[0], new String[0]);
  }

  public abstract @NotNull JitMember[] membars();

  @Override
  public final @NotNull ImmutableSeq<MemberDefLike> members() {
    return ImmutableArray.Unsafe.wrap(membars());
  }

  @Override
  public @NotNull Term telescope(int i, Seq<Term> teleArgs) {
    throw new UnsupportedOperationException("Unreachable");
  }

  @Override
  public @NotNull Term result(Seq<Term> teleArgs) {
    return SortTerm.Type0;
  }
}
