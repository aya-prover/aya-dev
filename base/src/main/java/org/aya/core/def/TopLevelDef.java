// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.core.sort.Sort;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * Top-level definitions.
 *
 * @author ice1000
 */
public sealed abstract class TopLevelDef implements Def permits UserDef, PrimDef {
  public final @NotNull ImmutableArray<Term.Param> telescope;
  public final @NotNull Term result;
  public final @NotNull ImmutableArray<Sort.LvlVar> levels;

  protected TopLevelDef(
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Term result, @NotNull ImmutableSeq<Sort.LvlVar> levels
  ) {
    this.telescope = telescope.toImmutableArray();
    this.result = result;
    this.levels = levels.toImmutableArray();
  }

  @Override public @NotNull ImmutableSeq<Term.Param> telescope() {
    return telescope;
  }

  @Override public @NotNull Term result() {
    return result;
  }
}
